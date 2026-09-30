//! Crash analysis: reads the game log, crash report and JVM fatal-error file
//! and explains the most likely cause in plain English, with fixes.
//!
//! Rules run from most to least specific; the first that matches wins. Each
//! pattern is one that shows up constantly in modded-Minecraft support
//! channels.

use std::{
    path::{Path, PathBuf},
    sync::{Mutex, PoisonError},
    time::SystemTime,
};

use serde::{Deserialize, Serialize};

use crate::{error::AppError, fsutil};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CrashAnalysis {
    /// Machine-readable cause, e.g. "out-of-memory".
    pub category: String,
    pub title: String,
    pub summary: String,
    pub fixes: Vec<String>,
    /// Mods most likely responsible, when the logs name any.
    pub culprits: Vec<String>,
    /// The key line(s) from the logs that led to this diagnosis.
    pub evidence: Vec<String>,
}

pub struct CrashContext<'a> {
    pub java_major: u32,
    pub memory_mb: u32,
    pub loader: &'a str,
    pub exit_code: Option<i32>,
}

pub fn analyze(log: &str, crash_report: Option<&str>, hs_err: Option<&str>, ctx: &CrashContext) -> CrashAnalysis {
    let all = format!("{}\n{}\n{}", crash_report.unwrap_or(""), log, hs_err.unwrap_or(""));

    if let Some(line) = find_line(&all, &["java.lang.OutOfMemoryError"]) {
        let suggested = (ctx.memory_mb + 2048).min(16384);
        return analysis(
            "out-of-memory",
            "Minecraft ran out of memory",
            format!("The game used all {} MB it was allowed and couldn't continue.", ctx.memory_mb),
            vec![
                format!("Raise Memory to about {suggested} MB in Settings."),
                "Lower render distance, or remove heavy mods / shader packs.".into(),
            ],
            vec![],
            vec![line],
        );
    }

    if let Some(line) = find_line(
        &all,
        &[
            "Could not reserve enough space for object heap",
            "Invalid maximum heap size",
            "Initial heap size set to a larger value than the maximum heap size",
            "There is insufficient memory for the Java Runtime Environment",
        ],
    ) {
        return analysis(
            "heap-allocation",
            "Java couldn't get the memory you asked for",
            format!("Java was asked for {} MB but the system couldn't provide it.", ctx.memory_mb),
            vec![
                "Lower Memory in Settings.".into(),
                "Close other memory-hungry programs (browsers, other games) and try again.".into(),
            ],
            vec![],
            vec![line],
        );
    }

    if let Some(line) = find_line(&all, &["Unrecognized VM option", "Unrecognized option:", "Invalid initial heap size", "Could not create the Java Virtual Machine"]) {
        return analysis(
            "jvm-options",
            "Java rejected a startup option",
            "Java refused to start because one of its options isn't valid for this Java version.".into(),
            vec![
                "Switch the JVM preset in Settings back to Balanced.".into(),
            ],
            vec![],
            vec![line],
        );
    }

    if let Some(line) = find_line(&all, &["UnsupportedClassVersionError"]) {
        let needed = class_version_to_java(&line);
        let summary = match needed {
            Some(n) => format!(
                "A mod in this build was compiled for Java {n}, but the game ran on Java {}.",
                ctx.java_major
            ),
            None => format!("A mod in this build needs a newer Java than Java {}.", ctx.java_major),
        };
        return analysis(
            "java-version",
            "Wrong Java version",
            summary,
            vec![
                "Press Play again; Doohickey picks the Java each version needs.".into(),
                "If a mod caused this, it was built for a newer Minecraft — use the version made for yours.".into(),
            ],
            culprits_from_stack(&all),
            vec![line],
        );
    }

    // Fabric / Quilt dependency resolution.
    if let Some(start) = find_index(&all, &["Incompatible mods found!", "incompatible mod set!", "Mod resolution failed"]) {
        let details: Vec<String> = all
            .lines()
            .skip(start + 1)
            .map(str::trim)
            .filter(|l| l.starts_with("- ") || l.starts_with("A potential solution"))
            .take(6)
            .map(str::to_owned)
            .collect();
        let culprits = quoted_names(&details);
        return analysis(
            "mod-dependencies",
            "Some mods are missing dependencies or conflict",
            "The mod loader stopped before the game started because the installed mods don't fit together.".into(),
            vec![
                "If it names Fabric API, press Play again and Doohickey reinstalls it.".into(),
                "Update or remove the mods listed as incompatible.".into(),
            ],
            culprits,
            details,
        );
    }

    // Forge / NeoForge dependency resolution.
    if let Some(start) = find_index(&all, &["Missing or unsupported mandatory dependencies"]) {
        let details: Vec<String> = all
            .lines()
            .skip(start + 1)
            .map(str::trim)
            .filter(|l| l.starts_with("Mod ID:"))
            .take(6)
            .map(str::to_owned)
            .collect();
        let mut culprits: Vec<String> = Vec::new();
        for line in &details {
            for key in ["Mod ID: '", "Requested by: '"] {
                if let Some(rest) = line.split_once(key).map(|(_, r)| r) {
                    let id = rest.split('\'').next().unwrap_or("").to_owned();
                    if !id.is_empty() && !culprits.contains(&id) {
                        culprits.push(id);
                    }
                }
            }
        }
        return analysis(
            "mod-dependencies",
            "Some mods are missing dependencies",
            "Forge stopped because some mods need other mods (or other versions) that aren't installed.".into(),
            vec!["Install the mods listed under \"Mod ID\" in the versions shown, or remove the mods that request them.".into()],
            culprits,
            details,
        );
    }

    if let Some(line) = find_line(&all, &["DuplicateModsFoundException", "Duplicate mods found", "Found duplicate mods", "duplicate mod ids"]) {
        return analysis(
            "duplicate-mods",
            "The same mod is installed twice",
            "Two files provide the same mod, usually an old and a new version side by side.".into(),
            vec!["Remove the older copy from this build's mods folder.".into()],
            quoted_names(std::slice::from_ref(&line)),
            vec![line],
        );
    }

    if let Some(line) =
        find_line(&all, &["Mixin apply failed", "MixinApplyError", "InvalidInjectionException", "InvalidMixinException", "Mixin transformation of"])
    {
        let mut culprits = mixin_owner(&all).into_iter().collect::<Vec<_>>();
        if culprits.is_empty() {
            culprits = culprits_from_stack(&all);
        }
        return analysis(
            "mixin-conflict",
            "A mod doesn't work with this setup",
            "A mod tried to modify the game's code and failed — it's either built for a different Minecraft version or conflicts with another mod.".into(),
            vec![
                "Update the mod named below, or remove it.".into(),
                "If two mods touch the same feature (e.g. two rendering mods), keep only one.".into(),
            ],
            culprits,
            vec![line],
        );
    }

    if let Some(line) = find_line(
        &all,
        &[
            "Pixel format not accelerated",
            "The driver does not appear to support OpenGL",
            "GLFW error 65542",
            "GLFW error 65543",
            "Failed to create OpenGL context",
            "OpenGL 3.2 is not supported",
            "Couldn't set pixel format",
        ],
    ) {
        return driver_problem(None, line);
    }

    if let Some(hs) = hs_err {
        let vendor = [("atio6axx.dll", "AMD"), ("atioglxx.dll", "AMD"), ("amdxx64.dll", "AMD"), ("nvoglv64.dll", "NVIDIA"), ("nvoglv32.dll", "NVIDIA")]
            .iter()
            .find(|(dll, _)| hs.contains(dll))
            .map(|(_, v)| *v)
            .or_else(|| hs.lines().any(|l| l.contains("C  [ig") && l.contains("icd")).then_some("Intel"));
        if let Some(vendor) = vendor {
            let line = find_line(hs, &["C  ["]).unwrap_or_default();
            return driver_problem(Some(vendor), line);
        }
        if let Some(line) = find_line(hs, &["EXCEPTION_ACCESS_VIOLATION", "SIGSEGV"]) {
            return analysis(
                "native-crash",
                "Java crashed in native code",
                "The Java runtime itself crashed, which is almost always caused by graphics drivers or overlay software.".into(),
                vec![
                    "Update your graphics drivers.".into(),
                    "Disable overlays (Discord, GeForce Experience, RivaTuner, OBS game capture) and try again.".into(),
                ],
                vec![],
                vec![line],
            );
        }
    }

    if let Some(line) = find_line(&all, &["NoSuchMethodError", "NoSuchFieldError", "NoClassDefFoundError", "ClassNotFoundException"]) {
        let culprits = culprits_from_stack(&all);
        if !culprits.is_empty() || ctx.loader != "vanilla" {
            return analysis(
                "wrong-mod-version",
                "A mod was made for a different version",
                "A mod referenced game or library code that doesn't exist in this version.".into(),
                vec![
                    "Make sure every mod is the build for this Minecraft version and loader.".into(),
                    "Use \"Check for updates\" in the Mods tab.".into(),
                ],
                culprits,
                vec![line],
            );
        }
    }

    // Generic crash report: use its description, exception and suspects.
    if let Some(report) = crash_report {
        let description = report
            .lines()
            .find_map(|l| l.strip_prefix("Description: "))
            .unwrap_or("Unexpected error")
            .to_owned();
        let exception = report_exception(report);
        let mut culprits = suspected_mods(report);
        if culprits.is_empty() {
            culprits = culprits_from_stack(report);
        }
        let mut fixes = Vec::new();
        if !culprits.is_empty() {
            fixes.push("Update or temporarily remove the suspected mod(s) below.".into());
        }
        fixes.push("If it keeps happening, try launching with mods disabled to confirm the cause.".into());
        return analysis(
            "crash-report",
            &format!("The game crashed: {description}"),
            exception.clone().unwrap_or_else(|| "Minecraft wrote a crash report.".into()),
            fixes,
            culprits,
            exception.into_iter().collect(),
        );
    }

    let code_hint = match ctx.exit_code {
        Some(-1073741819) => Some("Windows reported an access violation (0xC0000005) — usually a graphics driver or overlay."),
        Some(-1073740791) => Some("Windows reported a stack buffer overrun (0xC0000409) — usually a graphics driver or overlay."),
        Some(137) => Some("The system killed the game, most likely because the computer ran out of memory."),
        _ => None,
    };
    let last_error = all
        .lines()
        .rev()
        .find(|l| l.contains("Exception") || l.contains("ERROR") || l.contains("Error:"))
        .map(|l| l.trim().to_owned());
    analysis(
        "unknown",
        "Minecraft closed unexpectedly",
        code_hint
            .map(str::to_owned)
            .unwrap_or_else(|| format!("The game exited with code {}.", ctx.exit_code.map_or("unknown".into(), |c| c.to_string()))),
        vec![
            "Check the console tab for the last errors before it closed.".into(),
            "Update your graphics drivers and try again.".into(),
        ],
        culprits_from_stack(&all),
        last_error.into_iter().collect(),
    )
}

fn analysis(
    category: &str,
    title: &str,
    summary: String,
    fixes: Vec<String>,
    culprits: Vec<String>,
    evidence: Vec<String>,
) -> CrashAnalysis {
    CrashAnalysis { category: category.into(), title: title.into(), summary, fixes, culprits, evidence }
}

fn driver_problem(vendor: Option<&str>, line: String) -> CrashAnalysis {
    let who = vendor.map(|v| format!("{v} ")).unwrap_or_default();
    analysis(
        "graphics-driver",
        "Graphics driver problem",
        format!("Your {who}graphics driver crashed or doesn't support what Minecraft needs (OpenGL)."),
        vec![
            format!("Update your {who}graphics drivers from the manufacturer's website."),
            "On laptops, make Java use the dedicated GPU (Windows: Settings → Display → Graphics).".into(),
            "Remove shader packs or rendering mods to test.".into(),
        ],
        vec![],
        vec![line],
    )
}

fn find_line(text: &str, needles: &[&str]) -> Option<String> {
    text.lines().find(|l| needles.iter().any(|n| l.contains(n))).map(|l| l.trim().to_owned())
}

fn find_index(text: &str, needles: &[&str]) -> Option<usize> {
    text.lines().position(|l| needles.iter().any(|n| l.contains(n)))
}

/// "class file version 65.0" → Java 21 (class version − 44).
fn class_version_to_java(line: &str) -> Option<u32> {
    let idx = line.find("class file version ")? + "class file version ".len();
    let digits: String = line[idx..].chars().take_while(|c| c.is_ascii_digit()).collect();
    let class_version: u32 = digits.parse().ok()?;
    class_version.checked_sub(44)
}

/// Names in single quotes ('Sodium') or after "Mod ID: '".
fn quoted_names(lines: &[String]) -> Vec<String> {
    let mut out = Vec::new();
    for line in lines {
        let mut rest = line.as_str();
        while let Some(start) = rest.find('\'') {
            let after = &rest[start + 1..];
            let Some(end) = after.find('\'') else { break };
            let name = &after[..end];
            if !name.is_empty() && name.len() < 60 && !out.iter().any(|n: &String| n == name) {
                out.push(name.to_owned());
            }
            rest = &after[end + 1..];
        }
    }
    out.truncate(6);
    out
}

/// The mod whose mixin config failed: `mixins.<id>.json`, `<id>.mixins.json`
/// or Mixin's own "from mod <id>" note.
fn mixin_owner(text: &str) -> Option<String> {
    for line in text.lines() {
        if let Some(idx) = line.find("from mod ") {
            let id: String = line[idx + 9..].chars().take_while(|c| c.is_alphanumeric() || *c == '_' || *c == '-').collect();
            if !id.is_empty() {
                return Some(id);
            }
        }
        for token in line.split(|c: char| c.is_whitespace() || c == '\'' || c == '"' || c == '(' || c == ')' || c == ',' || c == ':') {
            if let Some(id) = token.strip_prefix("mixins.").and_then(|t| t.strip_suffix(".json")) {
                return Some(id.to_owned());
            }
            if let Some(id) = token.strip_suffix(".mixins.json") {
                return Some(id.to_owned());
            }
        }
    }
    None
}

/// "Suspected Mod(s):" block (Forge) or "Suspected Mods:" line (Fabric).
fn suspected_mods(report: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut lines = report.lines();
    while let Some(line) = lines.next() {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix("Suspected Mods:").or_else(|| trimmed.strip_prefix("Suspected Mod:")) {
            let rest = rest.trim();
            if !rest.is_empty() && rest != "None" && rest != "NONE" {
                out.extend(rest.split(',').map(|s| s.trim().to_owned()).filter(|s| !s.is_empty()));
            } else if rest.is_empty() {
                for next in lines.by_ref() {
                    let t = next.trim();
                    if t.is_empty() || t.starts_with("Stacktrace") {
                        break;
                    }
                    out.push(t.to_owned());
                }
            }
        }
    }
    out.truncate(6);
    out
}

fn report_exception(report: &str) -> Option<String> {
    let mut lines = report.lines().skip_while(|l| !l.starts_with("Description:")).skip(1);
    lines.find(|l| !l.trim().is_empty()).map(|l| l.trim().to_owned())
}

/// Guesses mods from stack frames: the first frames outside Minecraft, Java,
/// the loader and common libraries usually belong to the mod that broke.
fn culprits_from_stack(text: &str) -> Vec<String> {
    const IGNORED: &[&str] = &[
        "java.", "javax.", "jdk.", "sun.", "net.minecraft.", "com.mojang.", "org.lwjgl.", "net.fabricmc.",
        "org.quiltmc.", "net.minecraftforge.", "net.neoforged.", "cpw.mods.", "org.spongepowered.", "org.objectweb.",
        "com.google.", "io.netty.", "org.apache.", "it.unimi.", "com.sun.", "kotlin.", "scala.", "org.slf4j.",
    ];
    let mut out: Vec<String> = Vec::new();
    for line in text.lines() {
        let Some(frame) = line.trim().strip_prefix("at ") else { continue };
        // Drop "(File.java:42)" and " ~[mod.jar:?]" — both contain dots.
        let frame = frame.split(['(', ' ']).next().unwrap_or(frame);
        // Strip module / class-loader prefixes: "java.base/…", Fabric's "knot//…".
        let frame = frame.rsplit('/').next().unwrap_or(frame);
        if IGNORED.iter().any(|p| frame.starts_with(p)) {
            continue;
        }
        // Mixin-merged frames look like handler$abc000$sodium$method; those
        // name the mod directly.
        let parts: Vec<&str> = frame.split('.').collect();
        if parts.len() < 3 {
            continue;
        }
        let package = parts[..parts.len().saturating_sub(2).max(2)].join(".");
        if !out.contains(&package) {
            out.push(package);
        }
        if out.len() >= 3 {
            break;
        }
    }
    out
}

// ---- Collecting evidence after a game exits ---------------------------------

/// Newest file in `dir` whose name matches `filter` and that was written after `since`.
fn newest_since(dir: &Path, since: SystemTime, filter: impl Fn(&str) -> bool) -> Option<PathBuf> {
    std::fs::read_dir(dir)
        .ok()?
        .filter_map(Result::ok)
        .filter(|e| filter(&e.file_name().to_string_lossy()))
        .filter_map(|e| Some((e.metadata().ok()?.modified().ok()?, e.path())))
        .filter(|(t, _)| *t >= since)
        .max_by_key(|(t, _)| *t)
        .map(|(_, p)| p)
}

pub struct Evidence {
    pub log: String,
    pub crash_report: Option<(PathBuf, String)>,
    pub hs_err: Option<String>,
}

/// `session_log` is the stdout/stderr captured from this run. It's the only
/// record of failures before logging starts (bad JVM flags, wrong Java), and
/// latest.log is used only if this session actually wrote it — a stale file
/// from an earlier run would point the diagnosis at the wrong problem.
pub fn collect(game_dir: &Path, since: SystemTime, session_log: String) -> Evidence {
    let read = |p: &Path| std::fs::read(p).map(|b| String::from_utf8_lossy(&b).into_owned()).ok();
    let latest = game_dir.join("logs").join("latest.log");
    let fresh = std::fs::metadata(&latest).and_then(|m| m.modified()).is_ok_and(|t| t >= since);
    let log = match fresh.then(|| read(&latest)).flatten() {
        Some(file) => format!("{session_log}\n{file}"),
        None => session_log,
    };
    let crash_report = newest_since(&game_dir.join("crash-reports"), since, |n| n.ends_with(".txt"))
        .and_then(|p| read(&p).map(|t| (p, t)));
    let hs_err = newest_since(game_dir, since, |n| n.starts_with("hs_err_pid") && n.ends_with(".log")).and_then(|p| read(&p));
    Evidence { log, crash_report, hs_err }
}

// ---- History -------------------------------------------------------------------

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CrashRecord {
    pub id: String,
    pub instance_id: String,
    pub instance_name: String,
    pub time: u64,
    pub exit_code: Option<i32>,
    pub analysis: CrashAnalysis,
    pub report_path: Option<String>,
    pub seen: bool,
}

pub struct CrashStore {
    path: PathBuf,
    data: Mutex<Vec<CrashRecord>>,
}

const MAX_RECORDS: usize = 50;

impl CrashStore {
    pub fn load(path: PathBuf) -> Result<Self, AppError> {
        let data = fsutil::read_json(&path)?.unwrap_or_default();
        Ok(Self { path, data: Mutex::new(data) })
    }

    pub fn list(&self) -> Vec<CrashRecord> {
        self.data.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub fn add(&self, record: CrashRecord) -> Result<(), AppError> {
        let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        data.insert(0, record);
        data.truncate(MAX_RECORDS);
        fsutil::write_json_atomic(&self.path, &*data)
    }

    pub fn mark_seen(&self, id: &str) -> Result<(), AppError> {
        let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        for r in data.iter_mut().filter(|r| r.id == id) {
            r.seen = true;
        }
        fsutil::write_json_atomic(&self.path, &*data)
    }

    pub fn clear(&self) -> Result<(), AppError> {
        let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        data.clear();
        fsutil::write_json_atomic(&self.path, &*data)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ctx() -> CrashContext<'static> {
        CrashContext { java_major: 21, memory_mb: 4096, loader: "fabric", exit_code: Some(-1) }
    }

    #[test]
    fn out_of_memory() {
        let a = analyze("[12:00:00] [Render thread/ERROR]: java.lang.OutOfMemoryError: Java heap space", None, None, &ctx());
        assert_eq!(a.category, "out-of-memory");
        assert!(a.fixes[0].contains("6144 MB"));
    }

    #[test]
    fn bad_jvm_option() {
        let log = "Unrecognized VM option 'ThisFlagDoesNotExist'\nError: Could not create the Java Virtual Machine.\nError: A fatal exception has occurred. Program will exit.";
        let a = analyze(log, None, None, &ctx());
        assert_eq!(a.category, "jvm-options");
    }

    #[test]
    fn stale_latest_log_is_ignored() {
        let dir = std::env::temp_dir().join(format!("quartz-crash-{}", std::process::id()));
        std::fs::create_dir_all(dir.join("logs")).unwrap();
        std::fs::write(dir.join("logs").join("latest.log"), "java.lang.OutOfMemoryError: from an old session").unwrap();
        // Everything on disk predates this "session".
        let since = SystemTime::now() + std::time::Duration::from_secs(5);
        let ev = collect(&dir, since, "Unrecognized VM option 'X'".into());
        assert!(!ev.log.contains("OutOfMemoryError"));
        assert_eq!(analyze(&ev.log, None, None, &ctx()).category, "jvm-options");
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn java_version_from_class_file() {
        let log = "Exception in thread \"main\" java.lang.UnsupportedClassVersionError: me/mod/Main has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0";
        let a = analyze(log, None, None, &CrashContext { java_major: 17, ..ctx() });
        assert_eq!(a.category, "java-version");
        assert!(a.summary.contains("Java 21"), "{}", a.summary);
    }

    #[test]
    fn fabric_missing_dependency() {
        let log = "[main/ERROR]: Incompatible mods found!\nnet.fabricmc.loader.impl.FormattedException: Some of your mods are incompatible with the game or each other!\nA potential solution has been determined, this may resolve your problem:\n\t - Install fabric-api, any version.\nMore details:\n\t - Mod 'Sodium Extra' (sodium-extra) 0.6.0 requires any version of fabric-api, which is missing!";
        let a = analyze(log, None, None, &ctx());
        assert_eq!(a.category, "mod-dependencies");
        assert!(a.culprits.contains(&"Sodium Extra".to_owned()), "{:?}", a.culprits);
    }

    #[test]
    fn forge_missing_dependency() {
        let log = "Missing or unsupported mandatory dependencies:\n\tMod ID: 'geckolib', Requested by: 'alexsmobs', Expected range: '[4.4,)', Actual version: '[MISSING]'";
        let a = analyze(log, None, None, &ctx());
        assert_eq!(a.category, "mod-dependencies");
        assert_eq!(a.culprits, ["geckolib", "alexsmobs"]);
    }

    #[test]
    fn mixin_conflict_names_mod() {
        let log = "org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered\nCaused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException: Critical injection failure: @Inject annotation on renderSky could not find any targets matching 'render' in net.minecraft.class_761. [PREINJECT Applicator Phase -> coolsky.mixins.json:WorldRendererMixin from mod coolsky -> Prepare Injections]";
        let a = analyze(log, None, None, &ctx());
        assert_eq!(a.category, "mixin-conflict");
        assert_eq!(a.culprits, ["coolsky"]);
    }

    #[test]
    fn amd_driver_crash() {
        let hs = "# A fatal error has been detected by the Java Runtime Environment:\n#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb\n# Problematic frame:\n# C  [atio6axx.dll+0x1f2e3]";
        let a = analyze("", None, Some(hs), &ctx());
        assert_eq!(a.category, "graphics-driver");
        assert!(a.summary.contains("AMD"));
    }

    #[test]
    fn crash_report_suspects() {
        let report = "---- Minecraft Crash Report ----\n// Oops.\n\nTime: 2026-09-26\nDescription: Rendering overlay\n\njava.lang.NullPointerException: Cannot invoke \"Object.toString()\" because \"x\" is null\n\tat com.example.hudmod.Overlay.render(Overlay.java:42)\n\nA detailed walkthrough...\nSuspected Mods: Cool HUD (coolhud)\n";
        let a = analyze("", Some(report), None, &ctx());
        assert_eq!(a.category, "crash-report");
        assert_eq!(a.title, "The game crashed: Rendering overlay");
        assert!(a.summary.starts_with("java.lang.NullPointerException"));
        assert_eq!(a.culprits, ["Cool HUD (coolhud)"]);
    }

    #[test]
    fn stack_frames_name_the_mod_package() {
        let log = "java.lang.NoSuchMethodError: 'void net.minecraft.class_310.method_1()'\n\tat java.base/java.lang.Thread.run(Thread.java:1583)\n\tat com.example.hudmod.Overlay.render(Overlay.java:42) ~[hudmod-1.0.jar:?]\n\tat net.minecraft.client.Minecraft.run(Minecraft.java:1)";
        let a = analyze(log, None, None, &ctx());
        assert_eq!(a.category, "wrong-mod-version");
        assert_eq!(a.culprits, ["com.example.hudmod"]);
    }

    #[test]
    fn unknown_with_exit_code() {
        let a = analyze("nothing useful", None, None, &CrashContext { exit_code: Some(-1073741819), ..ctx() });
        assert_eq!(a.category, "unknown");
        assert!(a.summary.contains("0xC0000005"));
    }
}
