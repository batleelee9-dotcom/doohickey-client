//! Installed mods for a profile, plus installing/updating from Modrinth and
//! CurseForge.
//!
//! Disabled mods are renamed to `*.jar.disabled` (the convention other
//! launchers use, so profiles stay portable). Where each file came from is
//! recorded in `<profile>/.quartz/mods.json`, keyed by the enabled file name.

pub mod curseforge;
pub mod metadata;
pub mod modrinth;

use std::{
    collections::{HashMap, HashSet},
    path::{Path, PathBuf},
    sync::{Mutex, PoisonError},
    time::SystemTime,
};

use base64::Engine;
use reqwest::Client;
use serde::{Deserialize, Serialize};

use crate::{
    download::{self, Download},
    error::AppError,
    fsutil,
    instances::Instance,
    meta::loaders::LoaderKind,
};
use metadata::ModMeta;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchHit {
    pub platform: String,
    pub project_id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
    pub author: String,
    pub categories: Vec<String>,
    pub url: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModSource {
    pub platform: String,
    pub project_id: String,
    pub version_id: String,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub icon_url: Option<String>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledMod {
    pub file_name: String,
    pub enabled: bool,
    pub size: u64,
    #[serde(flatten)]
    pub meta: ModMeta,
    pub has_icon: bool,
    pub source: Option<ModSource>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModUpdate {
    pub file_name: String,
    pub project_id: String,
    pub version_id: String,
    pub version_number: String,
}

#[derive(Clone, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallOutcome {
    /// File names written to mods/.
    pub installed: Vec<String>,
    /// Mods that must be downloaded by hand (distribution disabled by author).
    pub external: Vec<ExternalMod>,
    /// Things that couldn't be installed, and why.
    pub skipped: Vec<String>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalMod {
    pub name: String,
    pub url: String,
}

/// Parsed jar metadata keyed by (path, size, mtime), so reopening the Mods tab
/// doesn't re-read hundreds of zips.
#[derive(Default)]
pub struct MetaCache {
    entries: Mutex<HashMap<PathBuf, (u64, SystemTime, ModMeta, Option<String>)>>,
}

impl MetaCache {
    fn get_or_read(&self, path: &Path, size: u64, modified: SystemTime) -> ModMeta {
        if let Some((s, m, meta, _)) = self.entries.lock().unwrap_or_else(PoisonError::into_inner).get(path) {
            if *s == size && *m == modified {
                return meta.clone();
            }
        }
        let meta = metadata::read(path);
        self.entries
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .insert(path.to_owned(), (size, modified, meta.clone(), None));
        meta
    }

    /// sha1 of the file, cached alongside its metadata.
    fn sha1(&self, path: &Path, size: u64, modified: SystemTime) -> Option<String> {
        if let Some((s, m, _, Some(hash))) = self.entries.lock().unwrap_or_else(PoisonError::into_inner).get(path) {
            if *s == size && *m == modified {
                return Some(hash.clone());
            }
        }
        let hash = fsutil::sha1_file(path).ok()?;
        let meta = self.get_or_read(path, size, modified);
        self.entries
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .insert(path.to_owned(), (size, modified, meta, Some(hash.clone())));
        Some(hash)
    }
}

/// Everything mod operations need about one profile.
pub struct ModCtx<'a> {
    pub http: &'a Client,
    pub dir: PathBuf,
    pub game_version: String,
    pub loader: LoaderKind,
    pub cache: &'a MetaCache,
}

impl<'a> ModCtx<'a> {
    pub fn new(http: &'a Client, instance_dir: PathBuf, instance: &Instance, cache: &'a MetaCache) -> Result<Self, AppError> {
        if instance.loader == LoaderKind::Vanilla {
            return Err(AppError::Invalid(
                "Vanilla profiles can't load mods. Change the profile's loader to Fabric, Quilt, Forge or NeoForge.".into(),
            ));
        }
        Ok(Self {
            http,
            dir: instance_dir,
            game_version: instance.game_version.clone(),
            loader: instance.loader,
            cache,
        })
    }

    fn mods_dir(&self) -> PathBuf {
        self.dir.join("mods")
    }

    fn index_path(&self) -> PathBuf {
        self.dir.join(".quartz").join("mods.json")
    }

    fn index(&self) -> HashMap<String, ModSource> {
        fsutil::read_json(&self.index_path()).ok().flatten().unwrap_or_default()
    }

    fn save_index(&self, index: &HashMap<String, ModSource>) -> Result<(), AppError> {
        fsutil::write_json_atomic(&self.index_path(), index)
    }
}

fn is_mod_file(name: &str) -> bool {
    let base = name.strip_suffix(".disabled").unwrap_or(name);
    base.ends_with(".jar") || base.ends_with(".zip") || base.ends_with(".litemod")
}

/// "sodium.jar.disabled" → "sodium.jar"
fn base_name(name: &str) -> &str {
    name.strip_suffix(".disabled").unwrap_or(name)
}

/// File names must stay inside mods/.
fn safe_name(name: &str) -> Result<&str, AppError> {
    if name.is_empty() || name.contains(['/', '\\']) || name.contains("..") || !is_mod_file(name) {
        return Err(AppError::Invalid("Invalid mod file name.".into()));
    }
    Ok(name)
}

pub fn list(ctx: &ModCtx) -> Result<Vec<InstalledMod>, AppError> {
    let dir = ctx.mods_dir();
    std::fs::create_dir_all(&dir)?;
    let index = ctx.index();
    let mut mods: Vec<InstalledMod> = std::fs::read_dir(&dir)?
        .filter_map(Result::ok)
        .filter_map(|e| {
            let name = e.file_name().to_string_lossy().into_owned();
            let meta = e.metadata().ok()?;
            if !meta.is_file() || !is_mod_file(&name) {
                return None;
            }
            let modified = meta.modified().unwrap_or(SystemTime::UNIX_EPOCH);
            let parsed = ctx.cache.get_or_read(&e.path(), meta.len(), modified);
            Some(InstalledMod {
                enabled: !name.ends_with(".disabled"),
                size: meta.len(),
                has_icon: parsed.icon_path.is_some(),
                source: index.get(base_name(&name)).cloned(),
                meta: parsed,
                file_name: name,
            })
        })
        .collect();
    mods.sort_by_key(|m| m.meta.name.clone().or(m.source.as_ref().and_then(|s| s.title.clone())).unwrap_or_else(|| m.file_name.clone()).to_lowercase());
    Ok(mods)
}

pub fn icon(ctx: &ModCtx, file_name: &str) -> Result<Option<String>, AppError> {
    let path = ctx.mods_dir().join(safe_name(file_name)?);
    let meta = std::fs::metadata(&path)?;
    let parsed = ctx.cache.get_or_read(&path, meta.len(), meta.modified().unwrap_or(SystemTime::UNIX_EPOCH));
    Ok(parsed.icon_path.and_then(|icon| metadata::read_icon(&path, &icon)).map(|bytes| {
        format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes))
    }))
}

pub fn set_enabled(ctx: &ModCtx, file_name: &str, enabled: bool) -> Result<(), AppError> {
    let name = safe_name(file_name)?;
    let base = base_name(name);
    let (from, to) = if enabled {
        (format!("{base}.disabled"), base.to_owned())
    } else {
        (base.to_owned(), format!("{base}.disabled"))
    };
    let dir = ctx.mods_dir();
    if dir.join(&from).exists() {
        std::fs::rename(dir.join(from), dir.join(to))?;
    }
    Ok(())
}

pub fn remove(ctx: &ModCtx, file_name: &str) -> Result<(), AppError> {
    let name = safe_name(file_name)?;
    let path = ctx.mods_dir().join(name);
    if path.exists() {
        trash::delete(&path).map_err(|e| AppError::Invalid(format!("Couldn't move the mod to the recycle bin: {e}")))?;
    }
    let mut index = ctx.index();
    if index.remove(base_name(name)).is_some() {
        ctx.save_index(&index)?;
    }
    Ok(())
}

pub fn import_file(ctx: &ModCtx, source: &Path) -> Result<String, AppError> {
    let name = source
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .filter(|n| is_mod_file(n))
        .ok_or_else(|| AppError::Invalid("Mods are .jar files.".into()))?;
    std::fs::create_dir_all(ctx.mods_dir())?;
    std::fs::copy(source, ctx.mods_dir().join(&name))?;
    Ok(name)
}

// ---- Modrinth installs -------------------------------------------------------

pub async fn install_modrinth(ctx: &ModCtx<'_>, project: &str, version_id: Option<&str>) -> Result<InstallOutcome, AppError> {
    let mut outcome = InstallOutcome::default();
    let mut index = ctx.index();
    let mut visited = HashSet::new();
    let version = match version_id {
        Some(id) => modrinth::version(ctx.http, id).await?,
        None => modrinth::best_version(ctx.http, project, &ctx.game_version, ctx.loader).await?.ok_or_else(|| {
            AppError::Invalid(format!(
                "This mod has no version for {} {}.",
                ctx.loader.label(),
                ctx.game_version
            ))
        })?,
    };
    let mut queue = vec![version];
    while let Some(version) = queue.pop() {
        if !visited.insert(version.project_id.clone()) || visited.len() > 40 {
            continue;
        }
        let mods_dir = ctx.mods_dir();
        let already = index.iter().any(|(file, s)| {
            s.project_id == version.project_id
                && (mods_dir.join(file).exists() || mods_dir.join(format!("{file}.disabled")).exists())
        });
        if !already {
            let file = version.primary_file().ok_or_else(|| AppError::Invalid("That version has no files.".into()))?;
            let target = ctx.mods_dir().join(safe_name(&file.filename)?);
            download::fetch_one(
                ctx.http,
                Download::new(&file.url, target, file.hashes.get("sha1").cloned(), Some(file.size)),
            )
            .await?;
            let project = modrinth::project(ctx.http, &version.project_id).await.ok();
            index.insert(
                file.filename.clone(),
                ModSource {
                    platform: "modrinth".into(),
                    project_id: version.project_id.clone(),
                    version_id: version.id.clone(),
                    title: project.as_ref().map(|p| p.title.clone()),
                    icon_url: project.and_then(|p| p.icon_url),
                },
            );
            ctx.save_index(&index)?;
            outcome.installed.push(file.filename.clone());
        }
        for dep in version.dependencies.iter().filter(|d| d.dependency_type == "required") {
            let dep_version = match (&dep.version_id, &dep.project_id) {
                (Some(v), _) => modrinth::version(ctx.http, v).await.ok(),
                (None, Some(p)) if !visited.contains(p) => {
                    modrinth::best_version(ctx.http, p, &ctx.game_version, ctx.loader).await.ok().flatten()
                }
                _ => None,
            };
            match dep_version {
                Some(v) => queue.push(v),
                None => {
                    if let Some(p) = &dep.project_id {
                        if !visited.contains(p) {
                            outcome.skipped.push(format!("Required dependency {p} has no compatible version"));
                        }
                    }
                }
            }
        }
    }
    Ok(outcome)
}

// ---- CurseForge installs -----------------------------------------------------

pub async fn install_curseforge(ctx: &ModCtx<'_>, key: &str, mod_id: &str) -> Result<InstallOutcome, AppError> {
    let mut outcome = InstallOutcome::default();
    let mut index = ctx.index();
    let mut queue = vec![mod_id.to_owned()];
    let mut visited = HashSet::new();
    while let Some(id) = queue.pop() {
        if !visited.insert(id.clone()) || visited.len() > 40 {
            continue;
        }
        if index.values().any(|s| s.platform == "curseforge" && s.project_id == id) {
            continue;
        }
        let info = curseforge::get_mod(ctx.http, key, &id).await?;
        let Some(file) = curseforge::best_file(ctx.http, key, &id, &ctx.game_version, ctx.loader).await? else {
            outcome.skipped.push(format!("{} has no file for {} {}", info.name, ctx.loader.label(), ctx.game_version));
            continue;
        };
        match &file.download_url {
            Some(url) => {
                let target = ctx.mods_dir().join(safe_name(&file.file_name)?);
                download::fetch_one(ctx.http, Download::new(url, target, file.sha1(), Some(file.file_length))).await?;
                index.insert(
                    file.file_name.clone(),
                    ModSource {
                        platform: "curseforge".into(),
                        project_id: id.clone(),
                        version_id: file.id.to_string(),
                        title: Some(info.name.clone()),
                        icon_url: info.logo.as_ref().and_then(|l| l.thumbnail_url.clone()),
                    },
                );
                ctx.save_index(&index)?;
                outcome.installed.push(file.file_name.clone());
            }
            None => outcome.external.push(ExternalMod {
                name: info.name.clone(),
                url: info
                    .links
                    .as_ref()
                    .and_then(|l| l.website_url.clone())
                    .unwrap_or_else(|| format!("https://www.curseforge.com/minecraft/mc-mods/{}", info.slug)),
            }),
        }
        queue.extend(file.dependencies.iter().filter(|d| d.relation_type == 3).map(|d| d.mod_id.to_string()));
    }
    Ok(outcome)
}

// ---- Updates -----------------------------------------------------------------

/// Checks every jar against Modrinth by hash — which also identifies mods
/// that were dropped in by hand.
pub async fn check_updates(ctx: &ModCtx<'_>) -> Result<Vec<ModUpdate>, AppError> {
    let files: Vec<(String, PathBuf, u64, SystemTime)> = std::fs::read_dir(ctx.mods_dir())?
        .filter_map(Result::ok)
        .filter_map(|e| {
            let name = e.file_name().to_string_lossy().into_owned();
            let meta = e.metadata().ok()?;
            is_mod_file(&name).then(|| (name, e.path(), meta.len(), meta.modified().unwrap_or(SystemTime::UNIX_EPOCH)))
        })
        .collect();
    let hashed: Vec<(String, String)> = {
        let cache = ctx.cache;
        let mut out = Vec::new();
        for (name, path, size, modified) in &files {
            if let Some(h) = cache.sha1(path, *size, *modified) {
                out.push((name.clone(), h));
            }
        }
        out
    };
    let hashes: Vec<String> = hashed.iter().map(|(_, h)| h.clone()).collect();
    let latest = modrinth::latest_for_hashes(ctx.http, &hashes, &ctx.game_version, ctx.loader).await?;

    // Record provenance for mods we just identified.
    let mut index = ctx.index();
    let mut new_projects = Vec::new();
    for (name, hash) in &hashed {
        if let Some(v) = latest.get(hash) {
            let base = base_name(name).to_owned();
            if !index.contains_key(&base) {
                new_projects.push(v.project_id.clone());
                index.insert(
                    base,
                    ModSource { platform: "modrinth".into(), project_id: v.project_id.clone(), version_id: v.id.clone(), title: None, icon_url: None },
                );
            }
        }
    }
    if !new_projects.is_empty() {
        if let Ok(projects) = modrinth::projects(ctx.http, &new_projects).await {
            for source in index.values_mut() {
                if let Some(p) = projects.iter().find(|p| p.id == source.project_id) {
                    source.title.get_or_insert(p.title.clone());
                    if source.icon_url.is_none() {
                        source.icon_url = p.icon_url.clone();
                    }
                }
            }
        }
        ctx.save_index(&index)?;
    }

    let mut updates = Vec::new();
    for (name, hash) in hashed {
        let Some(newest) = latest.get(&hash) else { continue };
        // Modrinth's "latest" ignores stability, so a stable install would be
        // offered alphas. Re-resolve with the installer's preference
        // (release > beta > alpha) whenever the newest isn't a release.
        let candidate = if newest.version_type == "release" {
            Some(newest.clone())
        } else {
            modrinth::best_version(ctx.http, &newest.project_id, &ctx.game_version, ctx.loader).await?
        };
        let Some(candidate) = candidate else { continue };
        let is_current = candidate
            .primary_file()
            .and_then(|f| f.hashes.get("sha1"))
            .is_some_and(|h| h.eq_ignore_ascii_case(&hash));
        if !is_current {
            updates.push(ModUpdate {
                file_name: name,
                project_id: candidate.project_id.clone(),
                version_id: candidate.id.clone(),
                version_number: candidate.version_number.clone(),
            });
        }
    }
    Ok(updates)
}

pub async fn apply_update(ctx: &ModCtx<'_>, file_name: &str, version_id: &str) -> Result<String, AppError> {
    let old = safe_name(file_name)?.to_owned();
    let version = modrinth::version(ctx.http, version_id).await?;
    let file = version.primary_file().ok_or_else(|| AppError::Invalid("That version has no files.".into()))?;
    let new_name = safe_name(&file.filename)?.to_owned();
    let was_disabled = old.ends_with(".disabled");
    let tmp_target = ctx.mods_dir().join(format!("{new_name}.quartz-update"));
    download::fetch_one(ctx.http, Download::new(&file.url, tmp_target.clone(), file.hashes.get("sha1").cloned(), Some(file.size)))
        .await?;
    // Only remove the old file once the new one is safely on disk.
    let old_path = ctx.mods_dir().join(&old);
    if old_path.exists() {
        trash::delete(&old_path).or_else(|_| std::fs::remove_file(&old_path)).map_err(|e| AppError::Invalid(e.to_string()))?;
    }
    let final_name = if was_disabled { format!("{new_name}.disabled") } else { new_name.clone() };
    std::fs::rename(&tmp_target, ctx.mods_dir().join(&final_name))?;

    let mut index = ctx.index();
    let previous = index.remove(base_name(&old));
    index.insert(
        new_name,
        ModSource {
            platform: "modrinth".into(),
            project_id: version.project_id.clone(),
            version_id: version.id.clone(),
            title: previous.as_ref().and_then(|p| p.title.clone()),
            icon_url: previous.and_then(|p| p.icon_url),
        },
    );
    ctx.save_index(&index)?;
    Ok(final_name)
}

// ---- Performance pack -------------------------------------------------------

/// One-click performance mods. Each group lists interchangeable projects in
/// order of preference — only the first compatible one is installed, because
/// e.g. Sodium and Embeddium conflict.
const PERFORMANCE_PACK: &[&[&str]] = &[
    &["sodium", "embeddium"],
    &["lithium"],
    &["ferrite-core"],
    &["immediatelyfast"],
    &["entityculling"],
    &["modernfix"],
    &["moreculling"],
    &["dynamic-fps"],
    // Merged into vanilla's lighting engine after 1.20.1; only offered where it exists.
    &["starlight"],
];

pub async fn install_performance_pack(ctx: &ModCtx<'_>) -> Result<InstallOutcome, AppError> {
    let mut outcome = InstallOutcome::default();
    for group in PERFORMANCE_PACK {
        let mut installed = false;
        for slug in *group {
            match modrinth::best_version(ctx.http, slug, &ctx.game_version, ctx.loader).await? {
                Some(version) => {
                    let result = install_modrinth(ctx, slug, Some(&version.id)).await?;
                    outcome.installed.extend(result.installed);
                    outcome.skipped.extend(result.skipped);
                    installed = true;
                    break;
                }
                None => continue,
            }
        }
        if !installed {
            outcome.skipped.push(format!("{} (not available for {} {})", group[0], ctx.loader.label(), ctx.game_version));
        }
    }
    Ok(outcome)
}
