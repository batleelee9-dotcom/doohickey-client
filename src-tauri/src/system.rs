use std::sync::OnceLock;

/// Total physical memory in MiB (cached; it doesn't change while we run).
pub fn total_memory_mb() -> u64 {
    static TOTAL: OnceLock<u64> = OnceLock::new();
    *TOTAL.get_or_init(|| {
        let mut sys = sysinfo::System::new();
        sys.refresh_memory();
        sys.total_memory() / 1024 / 1024
    })
}

/// OS name as used by Mojang's version-JSON rules.
pub fn mojang_os() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

pub fn is_32bit() -> bool {
    cfg!(target_pointer_width = "32")
}

/// Sensible default heap for a profile: a quarter of RAM, clamped to 2–8 GiB.
pub fn recommended_memory_mb() -> u32 {
    let quarter = total_memory_mb() / 4;
    (quarter.clamp(2048, 8192) / 512 * 512) as u32
}
