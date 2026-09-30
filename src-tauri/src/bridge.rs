//! A tiny local API that Doohickey Client, running inside the game, uses to
//! switch accounts without restarting: it lists the accounts added to the
//! launcher and hands out a fresh session for one of them.
//!
//! Security: it listens on 127.0.0.1 only, and every request must carry a
//! random secret generated at startup. The secret reaches the game through
//! `<profile>/.quartz/bridge.json`, written just before launch and deleted
//! by the mod as soon as it's read — never on a command line. Browsers
//! can't use it either: without CORS headers a page can neither send the
//! secret header cross-origin nor read the response.

use std::{net::TcpListener as StdListener, path::Path};

use serde::Serialize;
use serde_json::json;
use tauri::{AppHandle, Manager};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::{TcpListener, TcpStream},
    time::{timeout, Duration},
};

use crate::{
    accounts::{session, AccountKind},
    error::AppError,
    fsutil,
    state::AppState,
};

pub struct Bridge {
    port: u16,
    secret: String,
}

impl Bridge {
    /// Binds a free local port and starts serving in the background.
    pub fn start(app: AppHandle) -> std::io::Result<Self> {
        let listener = StdListener::bind(("127.0.0.1", 0))?;
        listener.set_nonblocking(true)?;
        let port = listener.local_addr()?.port();
        let secret = format!("{}{}", uuid::Uuid::new_v4().simple(), uuid::Uuid::new_v4().simple());
        let key = secret.clone();
        tauri::async_runtime::spawn(async move {
            let Ok(listener) = TcpListener::from_std(listener) else { return };
            loop {
                let Ok((stream, _)) = listener.accept().await else { continue };
                let (app, key) = (app.clone(), key.clone());
                tauri::async_runtime::spawn(async move {
                    let _ = serve(stream, &app, &key).await;
                });
            }
        });
        Ok(Self { port, secret })
    }

    /// Tells a game where the bridge is; Doohickey Client reads and deletes it.
    pub fn write_handshake(&self, game_dir: &Path) -> Result<(), AppError> {
        fsutil::write_json_atomic(&game_dir.join(".quartz").join("bridge.json"), &json!({ "port": self.port, "secret": self.secret }))
    }
}

#[derive(Debug, PartialEq)]
struct Request {
    method: String,
    path: String,
    bearer: Option<String>,
}

/// Parses the request line and the Authorization header of an HTTP/1.1 head.
fn parse(head: &str) -> Option<Request> {
    let mut lines = head.split("\r\n");
    let mut first = lines.next()?.split(' ');
    let (method, path) = (first.next()?.to_owned(), first.next()?.to_owned());
    let bearer = lines.filter_map(|l| l.split_once(':')).find_map(|(name, value)| {
        name.trim().eq_ignore_ascii_case("authorization").then(|| value.trim().strip_prefix("Bearer ").map(str::to_owned)).flatten()
    });
    Some(Request { method, path, bearer })
}

/// Compares in constant time, so response timing can't reveal the secret.
fn secret_matches(given: &str, secret: &str) -> bool {
    given.len() == secret.len() && given.bytes().zip(secret.bytes()).fold(0u8, |acc, (a, b)| acc | (a ^ b)) == 0
}

#[derive(Debug, PartialEq)]
enum Route {
    Accounts,
    Session(String),
    NotFound,
}

fn route(method: &str, path: &str) -> Route {
    match (method, path.strip_prefix("/v1/")) {
        ("GET", Some("accounts")) => Route::Accounts,
        ("POST", Some(rest)) => match rest.strip_prefix("accounts/").and_then(|r| r.strip_suffix("/session")) {
            Some(id) if !id.is_empty() && !id.contains('/') => Route::Session(id.to_owned()),
            _ => Route::NotFound,
        },
        _ => Route::NotFound,
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AccountEntry {
    id: String,
    username: String,
    kind: AccountKind,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SessionEntry {
    username: String,
    /// Without dashes, as the game uses it.
    uuid: String,
    access_token: String,
    user_type: &'static str,
    xuid: String,
}

async fn handle(app: &AppHandle, route: Route) -> (u16, serde_json::Value) {
    let state = app.state::<AppState>();
    let state = state.inner();
    match route {
        Route::Accounts => {
            let accounts: Vec<AccountEntry> = state
                .accounts
                .snapshot()
                .accounts
                .into_iter()
                .map(|a| AccountEntry { id: a.id, username: a.username, kind: a.kind })
                .collect();
            (200, json!({ "accounts": accounts }))
        }
        Route::Session(id) => {
            let Some(account) = state.accounts.get(&id) else {
                return (404, json!({ "error": "That account isn't in Doohickey any more." }));
            };
            match session::launch_auth(&state, &account).await {
                Ok(auth) => (
                    200,
                    serde_json::to_value(SessionEntry {
                        username: auth.username,
                        uuid: auth.uuid,
                        access_token: auth.access_token,
                        user_type: auth.user_type,
                        xuid: auth.xuid,
                    })
                    .unwrap_or_default(),
                ),
                Err(e) => (502, json!({ "error": e.to_string() })),
            }
        }
        Route::NotFound => (404, json!({ "error": "Not found." })),
    }
}

async fn serve(mut stream: TcpStream, app: &AppHandle, secret: &str) -> std::io::Result<()> {
    let mut head = Vec::with_capacity(512);
    let mut chunk = [0u8; 1024];
    while !head.windows(4).any(|w| w == b"\r\n\r\n") {
        let n = timeout(Duration::from_secs(5), stream.read(&mut chunk)).await.map_err(|_| std::io::ErrorKind::TimedOut)??;
        if n == 0 || head.len() + n > 16 * 1024 {
            return Ok(());
        }
        head.extend_from_slice(&chunk[..n]);
    }
    let (status, body) = match parse(&String::from_utf8_lossy(&head)) {
        None => (400, json!({ "error": "Bad request." })),
        Some(req) if !req.bearer.as_deref().is_some_and(|b| secret_matches(b, secret)) => {
            (401, json!({ "error": "Unauthorized." }))
        }
        Some(req) => handle(app, route(&req.method, &req.path)).await,
    };
    let body = body.to_string();
    let reason = match status {
        200 => "OK",
        400 => "Bad Request",
        401 => "Unauthorized",
        404 => "Not Found",
        _ => "Bad Gateway",
    };
    let response = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream.write_all(response.as_bytes()).await?;
    stream.shutdown().await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_requests() {
        let req = parse("GET /v1/accounts HTTP/1.1\r\nHost: 127.0.0.1\r\nauthorization: Bearer abc\r\n\r\n").unwrap();
        assert_eq!(req, Request { method: "GET".into(), path: "/v1/accounts".into(), bearer: Some("abc".into()) });
        assert_eq!(parse("POST /v1/x HTTP/1.1\r\n\r\n").unwrap().bearer, None);
        assert!(parse("").is_none());
    }

    #[test]
    fn checks_the_secret() {
        assert!(secret_matches("s3cret", "s3cret"));
        assert!(!secret_matches("s3cres", "s3cret"));
        assert!(!secret_matches("s3cre", "s3cret"));
    }

    #[test]
    fn routes() {
        assert_eq!(route("GET", "/v1/accounts"), Route::Accounts);
        assert_eq!(route("POST", "/v1/accounts/abc-123/session"), Route::Session("abc-123".into()));
        assert_eq!(route("POST", "/v1/accounts//session"), Route::NotFound);
        assert_eq!(route("POST", "/v1/accounts/a/b/session"), Route::NotFound);
        assert_eq!(route("GET", "/v1/accounts/abc/session"), Route::NotFound);
        assert_eq!(route("DELETE", "/v1/accounts"), Route::NotFound);
    }
}
