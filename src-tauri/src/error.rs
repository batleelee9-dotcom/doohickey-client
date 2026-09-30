use serde::{ser::SerializeStruct, Serialize, Serializer};

/// Every failure that crosses the IPC boundary.
///
/// Messages are written for players, not developers: each one says what went
/// wrong and, where possible, what to do next. The UI shows `message` verbatim
/// and branches on `kind` (e.g. it stays silent on `cancelled`).
#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("{0}")]
    Auth(String),
    #[error("{0}")]
    Network(String),
    #[error("{0}")]
    Invalid(String),
    #[error("{0}")]
    Config(String),
    #[error("Sign-in was cancelled.")]
    Cancelled,
    #[error("Couldn't read or write launcher data: {0}")]
    Io(#[from] std::io::Error),
    #[error("Couldn't access the system keychain: {0}")]
    Keychain(#[from] keyring::Error),
    #[error("Launcher data couldn't be encoded: {0}")]
    Json(#[from] serde_json::Error),
}

impl AppError {
    fn kind(&self) -> &'static str {
        match self {
            Self::Auth(_) => "auth",
            Self::Network(_) => "network",
            Self::Invalid(_) => "invalid",
            Self::Config(_) => "config",
            Self::Cancelled => "cancelled",
            Self::Io(_) => "io",
            Self::Keychain(_) => "keychain",
            Self::Json(_) => "internal",
        }
    }
}

/// Serialized as `{ kind, message }` — mirrored by `AppError` in `src/lib/ipc.ts`.
impl Serialize for AppError {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        let mut s = serializer.serialize_struct("AppError", 2)?;
        s.serialize_field("kind", self.kind())?;
        s.serialize_field("message", &self.to_string())?;
        s.end()
    }
}

/// reqwest's own messages ("error sending request for url ...") mean nothing to a
/// player, so translate the failure class into something actionable.
impl From<reqwest::Error> for AppError {
    fn from(err: reqwest::Error) -> Self {
        let host = err
            .url()
            .and_then(|u| u.host_str())
            .unwrap_or("the server")
            .to_owned();
        let message = if err.is_timeout() {
            format!("{host} took too long to respond. Check your connection and try again.")
        } else if err.is_connect() {
            format!("Couldn't reach {host}. Check your internet connection, VPN or firewall.")
        } else if err.is_decode() {
            format!("{host} sent a response Doohickey didn't understand. Try again in a moment.")
        } else if let Some(status) = err.status() {
            format!("{host} returned an error ({status}).")
        } else {
            format!("Network error while contacting {host}.")
        };
        Self::Network(message)
    }
}
