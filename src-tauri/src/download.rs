//! Parallel, verified downloads. Everything the launcher fetches in bulk
//! (libraries, assets, Java runtimes, mods) goes through `fetch_all`.

use std::{
    collections::HashSet,
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicU32, AtomicU64, Ordering},
        Arc,
    },
    time::Duration,
};

use futures_util::{stream, StreamExt};
use reqwest::Client;
use serde::Serialize;
use sha1::{Digest, Sha1};
use tokio::io::AsyncWriteExt;

use crate::{error::AppError, fsutil};

/// Enough parallelism to saturate a home connection on thousands of small
/// asset files, without tripping CDN rate limits.
const CONCURRENCY: usize = 24;
const ATTEMPTS: u32 = 3;

#[derive(Clone, Debug)]
pub struct Download {
    pub url: String,
    /// Tried in order after `url` fails (old Forge libraries moved between repos).
    pub mirrors: Vec<String>,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

impl Download {
    pub fn new(url: impl Into<String>, path: PathBuf, sha1: Option<String>, size: Option<u64>) -> Self {
        Self { url: url.into(), mirrors: Vec::new(), path, sha1, size }
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Verify {
    /// Trust an existing file if its size matches (fast; used before every launch).
    Size,
    /// Re-hash every existing file (used by "Repair").
    Hash,
}

#[derive(Clone, Copy, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
    pub done_bytes: u64,
    pub total_bytes: u64,
    pub done_files: u32,
    pub total_files: u32,
}

fn is_valid(d: &Download, verify: Verify) -> bool {
    let Ok(meta) = std::fs::metadata(&d.path) else { return false };
    if let Some(size) = d.size {
        if meta.len() != size {
            return false;
        }
    }
    match (verify, &d.sha1) {
        (Verify::Hash, Some(expected)) => fsutil::sha1_file(&d.path).is_ok_and(|h| h.eq_ignore_ascii_case(expected)),
        _ => true,
    }
}

pub async fn fetch_all(
    http: &Client,
    mut items: Vec<Download>,
    verify: Verify,
    on_progress: impl Fn(Progress) + Send + Sync,
) -> Result<(), AppError> {
    // Asset objects are content-addressed, so several entries can share a path.
    let mut seen = HashSet::new();
    items.retain(|d| seen.insert(d.path.clone()));

    // Stat/hash existing files off the async threads.
    let needed: Vec<Download> = tokio::task::spawn_blocking(move || {
        items.into_iter().filter(|d| !is_valid(d, verify)).collect()
    })
    .await
    .map_err(|e| AppError::Invalid(format!("Download check failed: {e}")))?;

    let total_bytes = needed.iter().filter_map(|d| d.size).sum();
    let total_files = needed.len() as u32;
    if needed.is_empty() {
        on_progress(Progress { done_bytes: 0, total_bytes: 0, done_files: 0, total_files: 0 });
        return Ok(());
    }

    let done_bytes = Arc::new(AtomicU64::new(0));
    let done_files = Arc::new(AtomicU32::new(0));
    let snapshot = || Progress {
        done_bytes: done_bytes.load(Ordering::Relaxed),
        total_bytes,
        done_files: done_files.load(Ordering::Relaxed),
        total_files,
    };

    // Each future owns what it uses (the client is a cheap Arc clone). Futures
    // that borrow from a lazily-mapped iterator trip rustc's Send inference
    // when awaited inside Tauri's async commands.
    let futures: Vec<_> = needed
        .into_iter()
        .map(|d| {
            let (http, done_bytes, done_files) = (http.clone(), done_bytes.clone(), done_files.clone());
            async move {
                let result = download_with_retries(&http, &d, &done_bytes).await;
                done_files.fetch_add(1, Ordering::Relaxed);
                result.map_err(|e| (d.path, e))
            }
        })
        .collect();
    let mut results = stream::iter(futures).buffer_unordered(CONCURRENCY);

    // Progress is reported on a timer rather than per chunk, so a download of
    // 4000 tiny files doesn't flood the UI with 4000 events.
    let mut ticker = tokio::time::interval(Duration::from_millis(120));
    let mut failures: Vec<(PathBuf, AppError)> = Vec::new();
    loop {
        tokio::select! {
            next = results.next() => match next {
                Some(Ok(())) => {}
                Some(Err((path, e))) => failures.push((path, e)),
                None => break,
            },
            _ = ticker.tick() => on_progress(snapshot()),
        }
    }
    on_progress(snapshot());

    match failures.len() {
        0 => Ok(()),
        n => {
            let (path, err) = &failures[0];
            let name = path.file_name().map(|f| f.to_string_lossy().into_owned()).unwrap_or_default();
            Err(AppError::Network(if n == 1 {
                format!("Couldn't download {name}: {err}")
            } else {
                format!("Couldn't download {n} files (first: {name}: {err})")
            }))
        }
    }
}

async fn download_with_retries(http: &Client, d: &Download, done_bytes: &AtomicU64) -> Result<(), AppError> {
    let urls: Vec<&str> = std::iter::once(d.url.as_str()).chain(d.mirrors.iter().map(String::as_str)).collect();
    let mut last_err = AppError::Network("no download URL".into());
    for url in urls {
        for attempt in 0..ATTEMPTS {
            let mut attempt_bytes = 0u64;
            match download_one(http, url, d, done_bytes, &mut attempt_bytes).await {
                Ok(()) => return Ok(()),
                Err(e) => {
                    // Undo this attempt's contribution so progress never overshoots.
                    done_bytes.fetch_sub(attempt_bytes, Ordering::Relaxed);
                    let not_found = matches!(&e, AppError::Network(m) if m.contains("404"));
                    last_err = e;
                    if not_found {
                        break; // retrying a 404 is pointless; try the next mirror
                    }
                    tokio::time::sleep(Duration::from_millis(400 << attempt)).await;
                }
            }
        }
    }
    Err(last_err)
}

async fn download_one(
    http: &Client,
    url: &str,
    d: &Download,
    done_bytes: &AtomicU64,
    attempt_bytes: &mut u64,
) -> Result<(), AppError> {
    let res = http.get(url).send().await?;
    if !res.status().is_success() {
        return Err(AppError::Network(format!("server returned {} for {url}", res.status())));
    }
    if let Some(parent) = d.path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let tmp = part_path(&d.path);
    let mut file = tokio::io::BufWriter::new(tokio::fs::File::create(&tmp).await?);
    let mut hasher = Sha1::new();
    let mut body = res.bytes_stream();
    while let Some(chunk) = body.next().await {
        let chunk = chunk?;
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
        *attempt_bytes += chunk.len() as u64;
        done_bytes.fetch_add(chunk.len() as u64, Ordering::Relaxed);
    }
    file.flush().await?;
    drop(file);

    if let Some(expected) = &d.sha1 {
        let actual = fsutil::hex(&hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(&tmp).await;
            return Err(AppError::Network(format!("checksum mismatch (corrupted download) for {url}")));
        }
    }
    tokio::fs::rename(&tmp, &d.path).await?;
    Ok(())
}

fn part_path(path: &Path) -> PathBuf {
    let mut p = path.as_os_str().to_owned();
    p.push(".part");
    PathBuf::from(p)
}

/// Single small file (JSON, installer) with an optional checksum; skips the
/// download if a valid copy already exists.
pub async fn fetch_one(http: &Client, d: Download) -> Result<(), AppError> {
    fetch_all(http, vec![d], Verify::Size, |_| {}).await
}
