//! On-disk layout. The shared part mirrors the vanilla `.minecraft` folder
//! (versions/, libraries/, assets/) so Forge/NeoForge installers — which expect
//! that layout — can install straight into it, and every profile shares one
//! copy of each library and asset.

use std::path::{Path, PathBuf};

#[derive(Clone)]
pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    /// Picks the data folder (`QUARTZ_DATA_DIR` overrides the OS default, for
    /// portable installs) and resolves it to its real location on disk.
    ///
    /// Resolving matters: under Windows app-container file virtualization
    /// (MSIX/Store packaging, or being started by a packaged app) %APPDATA%
    /// is silently redirected. The JVM then reports jar locations under the
    /// redirected path while the classpath we pass says %APPDATA%; Fabric
    /// compares the two, concludes its own jar isn't on the classpath, and
    /// loads a second copy of itself — every mod then fails with
    /// ClassCastExceptions. Passing real paths keeps both views identical.
    pub fn resolve(default_root: PathBuf) -> std::io::Result<Self> {
        let root = std::env::var_os("QUARTZ_DATA_DIR").map(PathBuf::from).unwrap_or(default_root);
        std::fs::create_dir_all(&root)?;
        let real = std::fs::canonicalize(&root).map(strip_verbatim).unwrap_or(root);
        Ok(Self { root: real })
    }

    pub fn versions(&self) -> PathBuf {
        self.root.join("versions")
    }
    pub fn version_json(&self, id: &str) -> PathBuf {
        self.versions().join(id).join(format!("{id}.json"))
    }
    pub fn version_jar(&self, id: &str) -> PathBuf {
        self.versions().join(id).join(format!("{id}.jar"))
    }
    pub fn libraries(&self) -> PathBuf {
        self.root.join("libraries")
    }
    pub fn assets(&self) -> PathBuf {
        self.root.join("assets")
    }
    pub fn java(&self) -> PathBuf {
        self.root.join("java")
    }
    pub fn natives(&self, version_id: &str) -> PathBuf {
        self.root.join("natives").join(version_id)
    }
    pub fn instances(&self) -> PathBuf {
        self.root.join("instances")
    }
    pub fn instance(&self, id: &str) -> PathBuf {
        self.instances().join(id)
    }
    pub fn skins(&self) -> PathBuf {
        self.root.join("skins")
    }
    /// Cached metadata (version manifests, loader lists, Java runtime index).
    pub fn meta(&self) -> PathBuf {
        self.root.join("meta")
    }
    /// Downloaded Forge/NeoForge installers.
    pub fn installers(&self) -> PathBuf {
        self.root.join("installers")
    }
    pub fn logs(&self) -> PathBuf {
        self.root.join("logs")
    }
    pub fn file(&self, name: &str) -> PathBuf {
        self.root.join(name)
    }
}

/// `group:artifact:version[:classifier][@ext]` → `group/path/artifact/version/artifact-version[-classifier].ext`
pub fn maven_path(name: &str) -> Option<String> {
    let (coords, ext) = name.split_once('@').unwrap_or((name, "jar"));
    let parts: Vec<&str> = coords.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let (group, artifact, version) = (parts[0], parts[1], parts[2]);
    let file = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    Some(format!("{}/{artifact}/{version}/{file}", group.replace('.', "/")))
}

/// `canonicalize` returns `\\?\C:\...` on Windows, which some Java tooling
/// mishandles; plain drive paths are equivalent for anything under MAX_PATH.
fn strip_verbatim(path: PathBuf) -> PathBuf {
    let s = path.to_string_lossy();
    match s.strip_prefix(r"\\?\") {
        Some(rest) if rest.chars().nth(1) == Some(':') => PathBuf::from(rest),
        _ => path,
    }
}

/// Joins a forward-slash relative path onto a base using the platform separator.
pub fn join_rel(base: &Path, rel: &str) -> PathBuf {
    rel.split('/').filter(|p| !p.is_empty()).fold(base.to_path_buf(), |acc, part| acc.join(part))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven_paths() {
        assert_eq!(
            maven_path("org.ow2.asm:asm:9.10.1").as_deref(),
            Some("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar")
        );
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-windows").as_deref(),
            Some("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar")
        );
        assert_eq!(
            maven_path("de.oceanlabs.mcp:mcp_config:1.20.1@zip").as_deref(),
            Some("de/oceanlabs/mcp/mcp_config/1.20.1/mcp_config-1.20.1.zip")
        );
        assert_eq!(maven_path("broken"), None);
    }

    #[test]
    fn strips_verbatim_drive_prefix_only() {
        assert_eq!(strip_verbatim(PathBuf::from(r"\\?\C:\Users\x")), PathBuf::from(r"C:\Users\x"));
        assert_eq!(strip_verbatim(PathBuf::from(r"\\?\UNC\server\share")), PathBuf::from(r"\\?\UNC\server\share"));
    }
}
