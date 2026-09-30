// Renders a server MOTD — either a legacy string with § formatting codes or a
// JSON chat component — into flat styled segments.

export interface Segment {
  text: string;
  color?: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  strike?: boolean;
}

const CODE_COLORS: Record<string, string> = {
  "0": "#000000", "1": "#0000aa", "2": "#00aa00", "3": "#00aaaa", "4": "#aa0000", "5": "#aa00aa", "6": "#ffaa00", "7": "#aaaaaa",
  "8": "#555555", "9": "#5555ff", a: "#55ff55", b: "#55ffff", c: "#ff5555", d: "#ff55ff", e: "#ffff55", f: "#ffffff",
};

const NAMED: Record<string, string> = {
  black: "0", dark_blue: "1", dark_green: "2", dark_aqua: "3", dark_red: "4", dark_purple: "5", gold: "6", gray: "7",
  dark_gray: "8", blue: "9", green: "a", aqua: "b", red: "c", light_purple: "d", yellow: "e", white: "f",
};

type Style = Omit<Segment, "text">;

function legacy(text: string, base: Style, out: Segment[]) {
  let style: Style = { ...base };
  let buf = "";
  const flush = () => {
    if (buf) out.push({ text: buf, ...style });
    buf = "";
  };
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "§" && i + 1 < text.length) {
      flush();
      const code = text[++i].toLowerCase();
      if (CODE_COLORS[code]) style = { ...base, color: CODE_COLORS[code] };
      else if (code === "l") style.bold = true;
      else if (code === "o") style.italic = true;
      else if (code === "n") style.underline = true;
      else if (code === "m") style.strike = true;
      else if (code === "r") style = { ...base };
    } else buf += text[i];
  }
  flush();
}

function component(node: unknown, inherited: Style, out: Segment[]) {
  if (typeof node === "string") return legacy(node, inherited, out);
  if (Array.isArray(node)) return node.forEach((n) => component(n, inherited, out));
  if (!node || typeof node !== "object") return;
  const c = node as Record<string, unknown>;
  const style: Style = { ...inherited };
  if (typeof c.color === "string") style.color = c.color.startsWith("#") ? c.color : CODE_COLORS[NAMED[c.color] ?? ""] ?? style.color;
  if (typeof c.bold === "boolean") style.bold = c.bold;
  if (typeof c.italic === "boolean") style.italic = c.italic;
  if (typeof c.underlined === "boolean") style.underline = c.underlined;
  if (typeof c.strikethrough === "boolean") style.strike = c.strikethrough;
  if (typeof c.text === "string") legacy(c.text, style, out);
  if (Array.isArray(c.extra)) c.extra.forEach((e) => component(e, style, out));
}

export function parseMotd(motd: unknown): Segment[] {
  const out: Segment[] = [];
  component(motd, {}, out);
  return out;
}
