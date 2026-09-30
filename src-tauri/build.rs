use std::{fmt::Write, path::PathBuf};

fn main() {
    embed_client_mods();
    tauri_build::build()
}

/// The launcher embeds Doohickey Client — one jar per Minecraft version under
/// `client-mod/versions/` — so the mod always matches the launcher it
/// shipped with. Build them first: `cd client-mod && ./gradlew build`.
///
/// Writes `$OUT_DIR/clients.rs`: a `TARGETS` table that `client_mod.rs`
/// includes.
fn embed_client_mods() {
    let manifest = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let versions = manifest.parent().unwrap().join("client-mod").join("versions");
    println!("cargo:rerun-if-changed={}", versions.display());

    let mut dirs: Vec<PathBuf> = std::fs::read_dir(&versions)
        .unwrap_or_else(|e| panic!("Can't read {}: {e}", versions.display()))
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| p.join("gradle.properties").is_file())
        .collect();
    dirs.sort();

    let mut table = String::from("pub static TARGETS: &[Target] = &[\n");
    for dir in dirs {
        let props_path = dir.join("gradle.properties");
        println!("cargo:rerun-if-changed={}", props_path.display());
        let text = std::fs::read_to_string(&props_path).unwrap();
        let prop = |key: &str| {
            text.lines()
                .filter_map(|l| l.split_once('='))
                .find(|(k, _)| k.trim() == key)
                .map(|(_, v)| v.trim().to_owned())
                .unwrap_or_else(|| panic!("{key} missing from {}", props_path.display()))
        };
        let (version, minecraft, loader, requires, summary) =
            (prop("version"), prop("minecraft_version"), prop("loader"), prop("requires"), prop("summary"));
        let loader = match loader.as_str() {
            "fabric" => "Fabric",
            "legacyfabric" => "LegacyFabric",
            other => panic!("Unknown loader '{other}' in {}", props_path.display()),
        };
        let jar = dir.join("build").join("libs").join(format!("quartz-client-{version}+{minecraft}.jar"));
        println!("cargo:rerun-if-changed={}", jar.display());
        if !jar.is_file() {
            panic!(
                "\n\nDoohickey Client for {minecraft} isn't built yet ({} is missing).\nBuild it first:  cd client-mod && ./gradlew build\n\n",
                jar.display()
            );
        }
        let requires: Vec<String> =
            requires.split(',').map(str::trim).filter(|s| !s.is_empty()).map(|s| format!("{s:?}")).collect();
        writeln!(
            table,
            "    Target {{ minecraft: {minecraft:?}, loader: LoaderKind::{loader}, version: {version:?}, summary: {summary:?}, requires: &[{}], jar: include_bytes!({:?}) }},",
            requires.join(", "),
            jar.display().to_string()
        )
        .unwrap();
    }
    table.push_str("];\n");
    let out = PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("clients.rs");
    std::fs::write(out, table).unwrap();
}
