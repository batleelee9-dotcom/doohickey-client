import type { JvmPreset } from "./ipc";

/** JVM tuning presets (mirrors `launch::presets::JvmPreset`). */
export const PRESETS: { id: JvmPreset; label: string; desc: string }[] = [
  { id: "performance", label: "Performance", desc: "Recommended. Tuned G1 with the whole heap reserved up front, so the game never stalls to grow it." },
  { id: "balanced", label: "Balanced", desc: "G1 with short pauses; starts small and grows memory as needed." },
  { id: "lowLatency", label: "Low latency", desc: "ZGC: near-zero GC stutter. Needs Java 15+ and spare RAM." },
  { id: "jvmDefault", label: "JVM default", desc: "No tuning flags at all." },
];
