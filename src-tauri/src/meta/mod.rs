pub mod forge;
pub mod loaders;
pub mod mojang;

use std::time::Duration;

use reqwest::Client;
use serde::de::DeserializeOwned;

use crate::{
    download::{self, Download},
    error::AppError,
    fsutil,
    paths::Paths,
};
use mojang::{VersionJson, VersionManifest, MANIFEST_URL};

/// Fetches JSON through a small disk cache under `meta/`. A fresh cache entry
/// is used without touching the network; if the network fails, a stale entry is
/// used instead — so profiles that are already installed launch offline.
pub async fn cached_json<T: DeserializeOwned>(
    http: &Client,
    paths: &Paths,
    key: &str,
    url: &str,
    ttl: Duration,
) -> Result<T, AppError> {
    let cache = paths.meta().join(key);
    let fresh = std::fs::metadata(&cache)
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| t.elapsed().ok())
        .is_some_and(|age| age < ttl);
    if fresh {
        if let Some(v) = fsutil::read_json(&cache)? {
            return Ok(v);
        }
    }
    let fetched = async {
        let res = http.get(url).send().await?.error_for_status()?;
        Ok::<_, AppError>(res.bytes().await?)
    }
    .await;
    match fetched {
        Ok(bytes) => {
            let value: T = serde_json::from_slice(&bytes)
                .map_err(|e| AppError::Network(format!("Unexpected response from {url}: {e}")))?;
            if let Some(parent) = cache.parent() {
                std::fs::create_dir_all(parent)?;
            }
            std::fs::write(&cache, &bytes)?;
            Ok(value)
        }
        Err(err) => fsutil::read_json(&cache)?.ok_or(err),
    }
}

pub async fn manifest(http: &Client, paths: &Paths) -> Result<VersionManifest, AppError> {
    cached_json(http, paths, "version_manifest_v2.json", MANIFEST_URL, Duration::from_secs(600)).await
}

/// Ensures `versions/<id>/<id>.json` exists for a vanilla version and returns it.
pub async fn vanilla_version(http: &Client, paths: &Paths, id: &str) -> Result<VersionJson, AppError> {
    let path = paths.version_json(id);
    if let Some(v) = fsutil::read_json(&path)? {
        return Ok(v);
    }
    let manifest = manifest(http, paths).await?;
    let entry = manifest
        .versions
        .iter()
        .find(|v| v.id == id)
        .ok_or_else(|| AppError::Invalid(format!("Minecraft {id} isn't in Mojang's version list.")))?;
    download::fetch_one(http, Download::new(&entry.url, path.clone(), entry.sha1.clone(), None)).await?;
    fsutil::read_json(&path)?.ok_or_else(|| AppError::Invalid(format!("Version file for {id} is unreadable.")))
}

/// Loads a version and every parent it `inheritsFrom`, merged into one.
pub async fn resolve_version(http: &Client, paths: &Paths, id: &str) -> Result<VersionJson, AppError> {
    let mut chain = Vec::new();
    let mut next = Some(id.to_owned());
    while let Some(current) = next {
        if chain.len() > 8 {
            return Err(AppError::Invalid("Version inheritance is circular.".into()));
        }
        let version = match fsutil::read_json::<VersionJson>(&paths.version_json(&current))? {
            Some(v) => v,
            // Only vanilla versions can be fetched on demand; loader profiles
            // are written by their installers.
            None => vanilla_version(http, paths, &current).await?,
        };
        next = version.inherits_from.clone();
        chain.push(version);
    }
    let mut merged = chain.pop().expect("chain has at least one version");
    while let Some(child) = chain.pop() {
        merged = mojang::merge(child, merged);
    }
    Ok(merged)
}
