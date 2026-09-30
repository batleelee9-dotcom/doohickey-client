//! Turns the game's console output into structured lines.
//!
//! Mojang's log4j config (which we pass on for its Log4Shell mitigation)
//! writes each event to stdout as XML (`LegacyXMLLayout`):
//!
//! ```text
//! <log4j:Event logger="..." timestamp="1790000000000" level="INFO" thread="Render thread">
//!   <log4j:Message><![CDATA[Setting user: Steve]]></log4j:Message>
//! </log4j:Event>
//! ```
//!
//! Anything that isn't inside an event (early JVM output, mods printing to
//! System.out) passes through as a plain line.

use serde::Serialize;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Level {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    Fatal,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LogLine {
    pub level: Level,
    pub thread: Option<String>,
    /// Unix milliseconds, when the event carried a timestamp.
    pub time: Option<u64>,
    pub text: String,
}

#[derive(Default)]
pub struct LogParser {
    event: Option<PendingEvent>,
}

struct PendingEvent {
    level: Level,
    thread: Option<String>,
    time: Option<u64>,
    body: String,
}

impl LogParser {
    pub fn push(&mut self, line: &str) -> Vec<LogLine> {
        if let Some(event) = &mut self.event {
            event.body.push_str(line);
            event.body.push('\n');
            if line.contains("</log4j:Event>") {
                let event = self.event.take().expect("checked above");
                return event.finish();
            }
            return vec![];
        }
        let trimmed = line.trim_start();
        if trimmed.starts_with("<log4j:Event") {
            let event = PendingEvent {
                level: attr(trimmed, "level").map(parse_level).unwrap_or(Level::Info),
                thread: attr(trimmed, "thread").map(unescape),
                time: attr(trimmed, "timestamp").and_then(|t| t.parse().ok()),
                body: String::new(),
            };
            if trimmed.contains("</log4j:Event>") {
                let mut event = event;
                event.body.push_str(trimmed);
                return event.finish();
            }
            self.event = Some(event);
            return vec![];
        }
        if line.trim().is_empty() {
            return vec![];
        }
        vec![LogLine { level: guess_level(line), thread: None, time: None, text: line.to_owned() }]
    }
}

impl PendingEvent {
    fn finish(self) -> Vec<LogLine> {
        let mut text = cdata(&self.body, "log4j:Message").unwrap_or_default();
        if let Some(throwable) = cdata(&self.body, "log4j:Throwable") {
            if !text.is_empty() {
                text.push('\n');
            }
            text.push_str(throwable.trim_end());
        }
        // One LogLine per physical line keeps the console's rows a fixed
        // height (it's a virtualized list); stack traces become several rows.
        text.lines()
            .map(|l| LogLine { level: self.level, thread: self.thread.clone(), time: self.time, text: l.to_owned() })
            .collect()
    }
}

fn attr<'a>(tag: &'a str, name: &str) -> Option<&'a str> {
    let key = format!(" {name}=\"");
    let start = tag.find(&key)? + key.len();
    let end = tag[start..].find('"')? + start;
    Some(&tag[start..end])
}

fn cdata(body: &str, element: &str) -> Option<String> {
    let open = format!("<{element}>");
    let start = body.find(&open)? + open.len();
    let rest = &body[start..];
    let rest = rest.trim_start();
    if let Some(inner) = rest.strip_prefix("<![CDATA[") {
        let end = inner.find("]]>")?;
        Some(inner[..end].to_owned())
    } else {
        let end = rest.find(&format!("</{element}>"))?;
        Some(unescape(&rest[..end]))
    }
}

fn unescape(s: &str) -> String {
    s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}

fn parse_level(s: &str) -> Level {
    match s {
        "TRACE" => Level::Trace,
        "DEBUG" => Level::Debug,
        "WARN" => Level::Warn,
        "ERROR" => Level::Error,
        "FATAL" => Level::Fatal,
        _ => Level::Info,
    }
}

/// Plain lines from stdout (e.g. `[12:00:00] [main/WARN]: ...`, or a raw
/// stack trace) — pick a level from the usual markers.
fn guess_level(line: &str) -> Level {
    let upper = line.to_ascii_uppercase();
    if upper.contains("/FATAL]") || upper.contains("[FATAL]") {
        Level::Fatal
    } else if upper.contains("/ERROR]") || upper.contains("[ERROR]") || line.contains("Exception") || line.starts_with("\tat ") {
        Level::Error
    } else if upper.contains("/WARN]") || upper.contains("[WARN]") || upper.contains("WARNING") {
        Level::Warn
    } else if upper.contains("/DEBUG]") || upper.contains("[DEBUG]") {
        Level::Debug
    } else {
        Level::Info
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_xml_events_and_plain_lines() {
        let mut p = LogParser::default();
        assert!(p
            .push(r#"<log4j:Event logger="net.minecraft.client.Minecraft" timestamp="1790000000000" level="INFO" thread="Render thread">"#)
            .is_empty());
        assert!(p.push("  <log4j:Message><![CDATA[Setting user: Steve]]></log4j:Message>").is_empty());
        let out = p.push("</log4j:Event>");
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].text, "Setting user: Steve");
        assert_eq!(out[0].level, Level::Info);
        assert_eq!(out[0].thread.as_deref(), Some("Render thread"));
        assert_eq!(out[0].time, Some(1790000000000));

        let plain = p.push("[12:00:00] [main/WARN]: something odd");
        assert_eq!(plain[0].level, Level::Warn);
    }

    #[test]
    fn keeps_throwables_and_multiline_messages() {
        let mut p = LogParser::default();
        p.push(r#"<log4j:Event logger="x" timestamp="1" level="ERROR" thread="main">"#);
        p.push("  <log4j:Message><![CDATA[Failed to load");
        p.push("second line]]></log4j:Message>");
        p.push("  <log4j:Throwable><![CDATA[java.lang.RuntimeException: boom");
        p.push("\tat a.b.C.d(C.java:1)");
        p.push("]]></log4j:Throwable>");
        let out = p.push("</log4j:Event>");
        let texts: Vec<_> = out.iter().map(|l| l.text.as_str()).collect();
        assert_eq!(texts, ["Failed to load", "second line", "java.lang.RuntimeException: boom", "\tat a.b.C.d(C.java:1)"]);
        assert!(out.iter().all(|l| l.level == Level::Error));
    }
}
