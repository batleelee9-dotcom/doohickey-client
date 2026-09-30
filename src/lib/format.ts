import type { AccountKind, LoaderKind } from "./ipc";

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let v = n / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v < 10 ? v.toFixed(1) : Math.round(v)} ${units[i]}`;
}

export const loaderLabel: Record<LoaderKind, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  quilt: "Quilt",
  forge: "Forge",
  neoforge: "NeoForge",
  legacyfabric: "Legacy Fabric",
};

/** Forge ids embed the game version ("1.8.9-11.15.1.2318-1.8.9"); show just the loader part. */
export const accountKindLabel: Record<AccountKind, string> = {
  microsoft: "Microsoft",
  offline: "Offline",
};

/** Loader names as the Play screen shows them: Legacy Fabric is just "Fabric" there. */
export function buildLoaderLabel(kind: LoaderKind): string {
  return kind === "legacyfabric" ? "Fabric" : loaderLabel[kind];
}

const MOD_NAMES: Record<string, string> = {
  "ferrite-core": "FerriteCore",
  entityculling: "EntityCulling",
  immediatelyfast: "ImmediatelyFast",
  moreculling: "MoreCulling",
  modernfix: "ModernFix",
  vintagefix: "VintageFix",
  "dynamic-fps": "Dynamic FPS",
};

/** Modrinth slug → display name ("ferrite-core" → "FerriteCore"). */
export function modName(slug: string): string {
  return MOD_NAMES[slug] ?? slug.charAt(0).toUpperCase() + slug.slice(1).replace(/-/g, " ");
}

/** Mirrors `servers::is_lan_address`: loopback, private and link-local addresses, `localhost`, `.local`/`.lan`. */
export function isLanAddress(address: string): boolean {
  let host = address.trim().toLowerCase();
  if (host.startsWith("[")) host = host.slice(1, host.indexOf("]"));
  else if (host.split(":").length === 2) host = host.split(":")[0];
  if (host === "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return true;
  const v4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (v4) {
    const [a, b] = [Number(v4[1]), Number(v4[2])];
    return a === 127 || a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31) || (a === 169 && b === 254);
  }
  return host === "::1" || /^f[cd][0-9a-f]{0,2}:/.test(host) || /^fe[89ab][0-9a-f]?:/.test(host);
}
