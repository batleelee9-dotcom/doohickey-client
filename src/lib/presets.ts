import type { JvmPreset } from "./ipc";

/** JVM tuning presets (mirrors `launch::presets::JvmPreset`). */
export const PRESETS: { id: JvmPreset; label: string; desc: string }[] = [
  { id: "balanced", label: "Balanced", desc: "G1 with short pauses. A good default for anything." },
  { id: "performance", label: "Performance", desc: "Tuned G1 (Aikar-style). Best for big modpacks." },
  { id: "lowLatency", label: "Low latency", desc: "ZGC: near-zero GC stutter. Needs Java 15+ and spare RAM." },
  { id: "jvmDefault", label: "JVM default", desc: "No tuning flags at all." },
];
