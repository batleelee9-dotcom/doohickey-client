//! Turns a profile into something launchable: the right Java, the game jar,
//! the loader, every library and asset — downloading only what's missing.

use std::{
    io::Read,
    path::{Path, PathBuf},
};

use reqwest::Client;
use serde::Serialize;

use crate::{
    download::{self, Download, Progress, Verify},
    error::AppError,
    instances::Instance,
    java,
    meta::{
        self, forge, loaders,
        loaders::LoaderKind,
        mojang::{rules_allow, AssetIndex, Library, RuleEnv, VersionJson, LIBRARIES_URL, RESOURCES_URL},
    },
    paths::{join_rel, maven_path, Paths},
    system,
};

#[derive(Clone, Copy, Debug, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Stage {
    Account,
    Java,
    Game,
    Loader,
    Files,
    Natives,
    Starting,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchProgress {
    pub stage: Stage,
    pub message: String,
    pub progress: Option<Progress>,
}

pub type Reporter<'a> = &'a (dyn Fn(LaunchProgress) + Send + Sync);

fn report(r: Reporter, stage: Stage, message: impl Into<String>, progress: Option<Progress>) {
    r(LaunchProgress { stage, message: message.into(), progress });
}

pub struct Prepared {
    pub version: VersionJson,
    pub version_id: String,
    pub java: PathBuf,
    pub java_major: u32,
    pub classpath: Vec<PathBuf>,
    pub natives_dir: PathBuf,
    pub assets_root: PathBuf,
    pub assets_index: String,
    /// Where pre-1.7.3 versions expect assets laid out by name.
    pub game_assets: PathBuf,
    pub log_config: Option<(String, PathBuf)>,
}

const FALLBACK_REPOS: [&str; 3] =
    ["https://maven.minecraftforge.net/", "https://repo1.maven.org/maven2/", "https://libraries.minecraft.net/"];

pub async fn prepare(
    http: &Client,
    paths: &Paths,
    instance: &Instance,
    verify: Verify,
    reporter: Reporter<'_>,
) -> Result<Prepared, AppError> {
    let game = instance.game_version.as_str();

    report(reporter, Stage::Game, format!("Fetching Minecraft {game}"), None);
    let vanilla = meta::vanilla_version(http, paths, game).await?;

    // Java first: Forge/NeoForge installers need it to run.
    let (component, required_major) = vanilla
        .java_version
        .as_ref()
        .map(|j| (j.component.clone(), j.major_version))
        .unwrap_or_else(|| ("jre-legacy".into(), 8));
    let (java, java_major) = match instance.java_path.as_deref() {
        Some(custom) => {
            let path = PathBuf::from(custom);
            if !path.exists() {
                return Err(AppError::Config(format!(
                    "The custom Java path for this profile doesn't exist: {custom}. Fix or clear it in the profile settings."
                )));
            }
            let major = java::detect_major(&path).unwrap_or(required_major);
            if major < required_major {
                return Err(AppError::Config(format!(
                    "Minecraft {game} needs Java {required_major}, but this profile's custom Java is Java {major}."
                )));
            }
            (path, major)
        }
        None => {
            report(reporter, Stage::Java, format!("Preparing Java {required_major}"), None);
            let exe = java::ensure_runtime(http, paths, &component, |p| {
                report(reporter, Stage::Java, format!("Downloading Java {required_major}"), Some(p))
            })
            .await?;
            (exe, required_major)
        }
    };

    // The vanilla client jar, which loader installers also expect to find.
    let client = vanilla
        .downloads
        .as_ref()
        .and_then(|d| d.client.clone())
        .ok_or_else(|| AppError::Invalid(format!("Minecraft {game} has no client download.")))?;
    let vanilla_jar = paths.version_jar(game);
    download::fetch_all(
        http,
        vec![Download::new(&client.url, vanilla_jar.clone(), client.sha1.clone(), client.size)],
        verify,
        |p| report(reporter, Stage::Game, format!("Downloading Minecraft {game}"), Some(p)),
    )
    .await?;

    let version_id = match (&instance.version_id, instance.loader) {
        (Some(id), _) if paths.version_json(id).exists() && verify == Verify::Size => id.clone(),
        (_, LoaderKind::Vanilla) => game.to_owned(),
        (_, kind) => {
            let loader_version =
                instance.loader_version.as_deref().ok_or_else(|| AppError::Invalid("Pick a loader version.".into()))?;
            report(reporter, Stage::Loader, format!("Installing {} {loader_version}", kind.label()), None);
            match kind {
                LoaderKind::Fabric | LoaderKind::Quilt | LoaderKind::LegacyFabric => {
                    loaders::install_fabric_like(http, paths, kind, game, loader_version).await?
                }
                _ => forge::install(http, paths, kind, loader_version, &java).await?,
            }
        }
    };

    let version = meta::resolve_version(http, paths, &version_id).await?;
    let env = RuleEnv::with_features(&[]);

    // Asset index and log config are tiny; fetch them before the bulk download.
    let asset_ref = version.asset_index.clone().ok_or_else(|| AppError::Invalid("Version has no asset index.".into()))?;
    let index_path = paths.assets().join("indexes").join(format!("{}.json", asset_ref.id));
    let mut small = vec![Download::new(&asset_ref.url, index_path.clone(), Some(asset_ref.sha1.clone()), Some(asset_ref.size))];
    let log_config = version.logging.as_ref().and_then(|l| l.client.as_ref()).map(|c| {
        let path = paths.assets().join("log_configs").join(&c.file.id);
        small.push(Download::new(&c.file.url, path.clone(), Some(c.file.sha1.clone()), Some(c.file.size)));
        (c.argument.clone(), path)
    });
    download::fetch_all(http, small, verify, |_| {}).await?;

    // Libraries (+ legacy natives) and asset objects in one progress bar.
    let mut downloads = Vec::new();
    let mut classpath = Vec::new();
    let mut native_jars: Vec<(PathBuf, Vec<String>)> = Vec::new();
    for lib in &version.libraries {
        if !rules_allow(lib.rules.as_deref(), &env) || lib.clientreq == Some(false) {
            continue;
        }
        if let Some((path, download)) = library_artifact(paths, lib)? {
            classpath.push(path);
            downloads.extend(download);
        }
        if let Some((path, download, exclude)) = library_natives(paths, lib) {
            native_jars.push((path, exclude));
            downloads.extend(download);
        }
    }

    let index: AssetIndex = serde_json::from_slice(&std::fs::read(&index_path)?)?;
    let objects_dir = paths.assets().join("objects");
    for object in index.objects.values() {
        let prefix = &object.hash[..2];
        downloads.push(Download::new(
            format!("{RESOURCES_URL}{prefix}/{}", object.hash),
            objects_dir.join(prefix).join(&object.hash),
            Some(object.hash.clone()),
            Some(object.size),
        ));
    }
    download::fetch_all(http, downloads, verify, |p| {
        report(reporter, Stage::Files, "Downloading game files", Some(p))
    })
    .await?;

    // Launchers run inherited versions from a copy of the game jar named after
    // the launched version; Forge's module system relies on that name to
    // exclude it (`-DignoreList=...${version_name}.jar`).
    let client_jar = if version_id == game {
        vanilla_jar
    } else {
        let copy = paths.version_jar(&version_id);
        let stale = std::fs::metadata(&copy).map(|m| Some(m.len()) != client.size).unwrap_or(true);
        if stale {
            std::fs::create_dir_all(copy.parent().expect("versions/<id>"))?;
            std::fs::copy(&vanilla_jar, &copy)?;
        }
        copy
    };
    classpath.push(client_jar);

    let natives_dir = paths.natives(&version_id);
    if !native_jars.is_empty() {
        report(reporter, Stage::Natives, "Extracting native libraries", None);
        let dir = natives_dir.clone();
        tokio::task::spawn_blocking(move || extract_natives(&dir, &native_jars))
            .await
            .map_err(|e| AppError::Invalid(e.to_string()))??;
    }
    std::fs::create_dir_all(&natives_dir)?;

    let assets_root = paths.assets();
    let game_assets = if index.is_virtual || index.map_to_resources {
        let target = if index.map_to_resources {
            paths.instance(&instance.id).join("resources")
        } else {
            assets_root.join("virtual").join(&asset_ref.id)
        };
        let objects = index.objects.clone();
        let (objects_dir, target_dir) = (objects_dir.clone(), target.clone());
        tokio::task::spawn_blocking(move || -> std::io::Result<()> {
            for (name, obj) in objects {
                let dest = join_rel(&target_dir, &name);
                if !dest.exists() {
                    std::fs::create_dir_all(dest.parent().unwrap_or(&target_dir))?;
                    std::fs::copy(objects_dir.join(&obj.hash[..2]).join(&obj.hash), dest)?;
                }
            }
            Ok(())
        })
        .await
        .map_err(|e| AppError::Invalid(e.to_string()))??;
        target
    } else {
        assets_root.clone()
    };

    Ok(Prepared {
        version,
        version_id,
        java,
        java_major,
        classpath,
        natives_dir,
        assets_root: if index.is_virtual { game_assets.clone() } else { assets_root },
        assets_index: asset_ref.id,
        game_assets,
        log_config,
    })
}

/// The library's jar on disk, plus a download if it can be fetched.
fn library_artifact(paths: &Paths, lib: &Library) -> Result<Option<(PathBuf, Option<Download>)>, AppError> {
    if let Some(artifact) = lib.downloads.as_ref().and_then(|d| d.artifact.as_ref()) {
        let rel = artifact.path.clone().or_else(|| maven_path(&lib.name)).unwrap_or_default();
        let path = join_rel(&paths.libraries(), &rel);
        if artifact.url.is_empty() {
            // Generated locally by a Forge installer; it must already exist.
            if !path.exists() {
                return Err(AppError::Invalid(format!(
                    "{} is missing — the loader install is incomplete. Use \"Repair\" in the profile settings.",
                    lib.name
                )));
            }
            return Ok(Some((path, None)));
        }
        let download = Download::new(&artifact.url, path.clone(), artifact.sha1.clone(), artifact.size);
        return Ok(Some((path, Some(download))));
    }
    // A library with only `natives` (pre-1.19 LWJGL platform jars, with or
    // without a `downloads` block — Legacy Fabric's have none) has no
    // classpath artifact of its own.
    if lib.natives.is_some() {
        return Ok(None);
    }
    let Some(rel) = maven_path(&lib.name) else { return Ok(None) };
    let path = join_rel(&paths.libraries(), &rel);
    let base = lib.url.as_deref().map(normalize_repo).unwrap_or_else(|| LIBRARIES_URL.to_owned());
    let mut download = Download::new(format!("{base}{rel}"), path.clone(), lib.sha1.clone(), lib.size);
    if lib.sha1.is_none() {
        // Legacy Forge libraries carry no checksums and have moved between
        // repositories over the years; try the usual homes.
        download.mirrors =
            FALLBACK_REPOS.iter().filter(|r| !base.starts_with(**r)).map(|r| format!("{r}{rel}")).collect();
    }
    Ok(Some((path, Some(download))))
}

fn library_natives(paths: &Paths, lib: &Library) -> Option<(PathBuf, Option<Download>, Vec<String>)> {
    let classifier = lib.natives.as_ref()?.get(system::mojang_os())?;
    let classifier = classifier.replace("${arch}", if system::is_32bit() { "32" } else { "64" });
    let exclude = lib.extract.clone().unwrap_or_default().exclude;
    if let Some(artifact) = lib.downloads.as_ref().and_then(|d| d.classifiers.as_ref()).and_then(|c| c.get(&classifier)) {
        let rel = artifact.path.clone().or_else(|| maven_path(&format!("{}:{classifier}", lib.name)))?;
        let path = join_rel(&paths.libraries(), &rel);
        let download = Download::new(&artifact.url, path.clone(), artifact.sha1.clone(), artifact.size);
        return Some((path, Some(download), exclude));
    }
    let rel = maven_path(&format!("{}:{classifier}", lib.name))?;
    let path = join_rel(&paths.libraries(), &rel);
    let base = lib.url.as_deref().map(normalize_repo).unwrap_or_else(|| LIBRARIES_URL.to_owned());
    Some((path.clone(), Some(Download::new(format!("{base}{rel}"), path, None, None)), exclude))
}

/// Old version JSONs point at repositories that have since moved to HTTPS or
/// new hosts.
fn normalize_repo(url: &str) -> String {
    let url = url
        .replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
        .replace("https://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
        .replace("http://", "https://");
    if url.ends_with('/') {
        url
    } else {
        format!("{url}/")
    }
}

fn extract_natives(dir: &Path, jars: &[(PathBuf, Vec<String>)]) -> Result<(), AppError> {
    std::fs::create_dir_all(dir)?;
    for (jar, exclude) in jars {
        let mut zip = zip::ZipArchive::new(std::fs::File::open(jar)?)
            .map_err(|e| AppError::Invalid(format!("Corrupt native library {}: {e}", jar.display())))?;
        for i in 0..zip.len() {
            let mut entry = zip.by_index(i).map_err(|e| AppError::Invalid(e.to_string()))?;
            let name = entry.name().to_owned();
            if entry.is_dir() || exclude.iter().any(|ex| name.starts_with(ex.as_str())) {
                continue;
            }
            // Natives are flat files; never follow a path out of the folder.
            let Some(file_name) = Path::new(&name).file_name() else { continue };
            let target = dir.join(file_name);
            if target.exists() {
                continue;
            }
            let mut bytes = Vec::new();
            entry.read_to_end(&mut bytes)?;
            std::fs::write(target, bytes)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn repos_are_normalized() {
        assert_eq!(normalize_repo("http://files.minecraftforge.net/maven/"), "https://maven.minecraftforge.net/");
        assert_eq!(normalize_repo("https://maven.fabricmc.net"), "https://maven.fabricmc.net/");
    }

    /// Legacy Fabric's LWJGL platform library: natives only, no downloads block.
    #[test]
    fn natives_only_library_is_not_on_the_classpath() {
        let lib: Library = serde_json::from_value(serde_json::json!({
            "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17",
            "url": "https://maven.legacyfabric.net/",
            "natives": { "linux": "natives-linux", "osx": "natives-osx", "windows": "natives-windows" }
        }))
        .unwrap();
        let paths = Paths { root: PathBuf::from("/data") };
        assert!(library_artifact(&paths, &lib).unwrap().is_none());
        let (_, download, _) = library_natives(&paths, &lib).expect("natives for this OS");
        let url = download.expect("downloadable").url;
        assert!(url.starts_with("https://maven.legacyfabric.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/"), "{url}");
        assert!(url.contains("-natives-"), "{url}");
    }
}
