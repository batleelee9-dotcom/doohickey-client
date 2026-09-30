//! JVM tuning presets. Flags are chosen per Java major version: a flag the
//! JVM doesn't recognise makes it refuse to start, and profiles span Java 8
//! (1.8.9) through Java 25 (26.x).

use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum JvmPreset {
    /// G1 with short pauses. Good default for any profile.
    Balanced,
    /// Aggressively tuned G1 (after Aikar's flags, adapted for clients):
    /// bigger young generation, earlier mixed collections. Best for modpacks.
    Performance,
    /// ZGC: sub-millisecond pauses at the cost of some throughput and RAM.
    /// Falls back to Balanced on Java versions without a production ZGC.
    LowLatency,
    /// No GC flags; the JVM's own defaults.
    JvmDefault,
}

const BALANCED: &[&str] =
    &["-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=50", "-XX:+DisableExplicitGC"];

const PERFORMANCE: &[&str] = &[
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=37",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:G1NewSizePercent=30",
    "-XX:G1MaxNewSizePercent=40",
    "-XX:G1HeapRegionSize=16M",
    "-XX:G1ReservePercent=20",
    "-XX:G1MixedGCCountTarget=4",
    "-XX:InitiatingHeapOccupancyPercent=15",
    "-XX:G1MixedGCLiveThresholdPercent=90",
    "-XX:SurvivorRatio=32",
    "-XX:MaxTenuringThreshold=1",
    "-XX:+PerfDisableSharedMem",
];

pub fn gc_flags(preset: JvmPreset, java_major: u32) -> Vec<String> {
    let flags: Vec<&str> = match preset {
        JvmPreset::Balanced => BALANCED.to_vec(),
        JvmPreset::Performance => PERFORMANCE.to_vec(),
        // ZGC became production-ready in 15 and generational in 21, where it
        // had to be opted into until 23 made it the default.
        JvmPreset::LowLatency if (21..=22).contains(&java_major) => vec!["-XX:+UseZGC", "-XX:+ZGenerational"],
        JvmPreset::LowLatency if java_major >= 15 => vec!["-XX:+UseZGC"],
        JvmPreset::LowLatency => BALANCED.to_vec(),
        JvmPreset::JvmDefault => vec![],
    };
    flags.into_iter().map(str::to_owned).collect()
}

/// Heap flags. The initial heap stays small so an idle game doesn't claim
/// its whole maximum up front.
pub fn memory_flags(max_mb: u32) -> Vec<String> {
    vec![format!("-Xms{}m", max_mb.min(1024)), format!("-Xmx{max_mb}m")]
}

/// Splits user-entered JVM arguments, honouring double quotes.
pub fn split_args(input: &str) -> Vec<String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let mut has_token = false;
    for c in input.chars() {
        match c {
            '"' => {
                in_quotes = !in_quotes;
                has_token = true;
            }
            c if c.is_whitespace() && !in_quotes => {
                if has_token {
                    args.push(std::mem::take(&mut current));
                    has_token = false;
                }
            }
            c => {
                current.push(c);
                has_token = true;
            }
        }
    }
    if has_token {
        args.push(current);
    }
    args
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn zgc_only_where_supported() {
        assert_eq!(gc_flags(JvmPreset::LowLatency, 21), ["-XX:+UseZGC", "-XX:+ZGenerational"]);
        assert_eq!(gc_flags(JvmPreset::LowLatency, 25), ["-XX:+UseZGC"]);
        assert_eq!(gc_flags(JvmPreset::LowLatency, 8)[0], "-XX:+UseG1GC");
    }

    #[test]
    fn splits_quoted_args() {
        assert_eq!(split_args(r#"-Dfoo=1  "-Dpath=C:\My Games" -Xss2m"#), ["-Dfoo=1", r"-Dpath=C:\My Games", "-Xss2m"]);
        assert!(split_args("   ").is_empty());
    }
}
