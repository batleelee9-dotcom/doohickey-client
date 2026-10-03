//! Microsoft → Xbox Live → Minecraft sign-in.
//!
//! 1. OAuth 2.0 authorization-code flow with PKCE against Microsoft's consumer
//!    tenant, in a login window inside the launcher. Microsoft's own page takes
//!    the password; the launcher only sees the redirect carrying the code.
//! 2. The Microsoft access token is exchanged for an Xbox Live user token (XBL).
//! 3. XBL is traded for an XSTS token scoped to Minecraft services.
//! 4. Minecraft services accept `XBL3.0 x=<userhash>;<xsts>` and return a
//!    Minecraft access token, which is used to read the player's profile.

use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use reqwest::{Client, StatusCode, Url};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use uuid::Uuid;

use crate::error::AppError;

const AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
/// Azure's standard redirect for desktop apps. The login window stops at it
/// and never loads it; it must be added to the Azure app under
/// Authentication → Mobile and desktop applications.
pub const REDIRECT_URI: &str = "https://login.microsoftonline.com/common/oauth2/nativeclient";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentications/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const MC_ENTITLEMENTS_URL: &str = "https://api.minecraftservices.com/entitlements/mcstore";

const SCOPE: &str = "XboxLive.signin offline_access";

pub const NO_CLIENT_ID: &str = "Microsoft sign-in needs your Azure app's client ID. \
    Paste it on the sign-in screen (see README).";

/// The Azure app's client ID: a runtime env var, then the one pasted on the
/// sign-in screen (`saved`), then the one baked in at compile time.
pub fn client_id(saved: Option<String>) -> Option<String> {
    std::env::var("QUARTZ_MS_CLIENT_ID")
        .ok()
        .filter(|id| !id.trim().is_empty())
        .or(saved)
        .or_else(|| option_env!("QUARTZ_MS_CLIENT_ID").map(str::to_owned))
        .filter(|id| !id.trim().is_empty())
}

#[derive(Deserialize)]
pub struct MsTokens {
    pub access_token: String,
    pub refresh_token: String,
}

#[derive(Deserialize)]
pub struct Profile {
    pub id: String,
    pub name: String,
    #[serde(default)]
    skins: Vec<Skin>,
}

#[derive(Deserialize)]
struct Skin {
    state: String,
    url: String,
}

impl Profile {
    pub fn active_skin_url(&self) -> Option<String> {
        self.skins
            .iter()
            .find(|s| s.state == "ACTIVE")
            // Mojang still hands out http:// texture URLs; the CSP only allows https.
            .map(|s| s.url.replacen("http://", "https://", 1))
    }
}

/// Progress reported to the UI once the browser part is done.
#[derive(Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum LoginStep {
    Xbox,
    Minecraft,
    Profile,
}

#[derive(Default, Deserialize)]
struct OAuthError {
    #[serde(default)]
    error: String,
    #[serde(default)]
    error_description: String,
}

/// One sign-in attempt: the PKCE verifier stays here, only its hash goes to Microsoft.
pub struct Pkce {
    pub verifier: String,
    pub state: String,
}

impl Pkce {
    pub fn new() -> Self {
        // Two v4 UUIDs = 244 random bits from the OS RNG.
        let random = || URL_SAFE_NO_PAD.encode([*Uuid::new_v4().as_bytes(), *Uuid::new_v4().as_bytes()].concat());
        Self { verifier: random(), state: random() }
    }

    /// The Microsoft sign-in page to open in the login window.
    pub fn authorize_url(&self, client_id: &str) -> String {
        let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(self.verifier.as_bytes()));
        let mut url = Url::parse(AUTHORIZE_URL).expect("valid constant URL");
        url.query_pairs_mut()
            .append_pair("client_id", client_id)
            .append_pair("response_type", "code")
            .append_pair("redirect_uri", REDIRECT_URI)
            .append_pair("response_mode", "query")
            .append_pair("scope", SCOPE)
            .append_pair("code_challenge", &challenge)
            .append_pair("code_challenge_method", "S256")
            .append_pair("state", &self.state)
            // Lets the player pick or add an account instead of reusing the last one.
            .append_pair("prompt", "select_account");
        url.into()
    }

    /// Reads the authorization code from the URL Microsoft redirected to.
    pub fn code_from_redirect(&self, redirect: &str) -> Result<String, AppError> {
        let url = Url::parse(redirect).map_err(|_| unexpected("Microsoft"))?;
        let param = |name: &str| url.query_pairs().find(|(k, _)| k == name).map(|(_, v)| v.into_owned());
        if let Some(error) = param("error") {
            if error == "access_denied" {
                return Err(AppError::Auth("Sign-in was cancelled in the Microsoft window.".into()));
            }
            return Err(describe_oauth_error(&OAuthError { error, error_description: param("error_description").unwrap_or_default() }));
        }
        if param("state").as_deref() != Some(self.state.as_str()) {
            return Err(AppError::Auth("The sign-in response didn't match this request. Try again.".into()));
        }
        param("code").ok_or_else(|| unexpected("Microsoft"))
    }
}

/// Trades the authorization code (plus the PKCE verifier) for tokens.
pub async fn exchange_code(http: &Client, client_id: &str, code: &str, pkce: &Pkce) -> Result<MsTokens, AppError> {
    let res = http
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "authorization_code"),
            ("client_id", client_id),
            ("code", code),
            ("redirect_uri", REDIRECT_URI),
            ("code_verifier", pkce.verifier.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    if res.status().is_success() {
        return Ok(res.json().await?);
    }
    Err(describe_oauth_error(&res.json().await.unwrap_or_default()))
}

/// Exchanges a stored refresh token for fresh tokens (Microsoft rotates the
/// refresh token too, so the caller must store the new one).
pub async fn refresh_tokens(http: &Client, client_id: &str, refresh_token: &str) -> Result<MsTokens, AppError> {
    let res = http
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", refresh_token),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    if res.status().is_success() {
        return Ok(res.json().await?);
    }
    let err: OAuthError = res.json().await.unwrap_or_default();
    if err.error == "invalid_grant" {
        return Err(AppError::Auth(
            "Your Microsoft sign-in has expired. Sign in again from the account menu.".into(),
        ));
    }
    Err(describe_oauth_error(&err))
}

/// A Minecraft services session: what the game receives as `--accessToken`.
pub struct McSession {
    pub access_token: String,
    pub expires_in: u64,
}

/// Runs steps 2–4. `on_step` is called as each stage starts.
pub async fn login_minecraft(
    http: &Client,
    ms_access_token: &str,
    mut on_step: impl FnMut(LoginStep),
) -> Result<(Profile, McSession), AppError> {
    on_step(LoginStep::Xbox);
    let xbl = xbox_user_token(http, ms_access_token).await?;
    let xsts = xsts_token(http, &xbl.token).await?;

    on_step(LoginStep::Minecraft);
    let session = minecraft_token(http, xsts.user_hash()?, &xsts.token).await?;

    on_step(LoginStep::Profile);
    let profile = fetch_profile(http, &session.access_token).await?;
    Ok((profile, session))
}

/// The profile of an already-signed-in session (skins and capes included).
pub async fn fetch_profile_raw(http: &Client, mc_token: &str) -> Result<serde_json::Value, AppError> {
    Ok(http.get(MC_PROFILE_URL).bearer_auth(mc_token).send().await?.error_for_status()?.json().await?)
}

#[derive(Deserialize)]
#[serde(rename_all = "PascalCase")]
struct XboxToken {
    token: String,
    display_claims: DisplayClaims,
}

#[derive(Deserialize)]
struct DisplayClaims {
    xui: Vec<XboxUser>,
}

#[derive(Deserialize)]
struct XboxUser {
    uhs: String,
}

impl XboxToken {
    fn user_hash(&self) -> Result<&str, AppError> {
        self.display_claims
            .xui
            .first()
            .map(|u| u.uhs.as_str())
            .ok_or_else(|| unexpected("Xbox Live"))
    }
}

async fn xbox_user_token(http: &Client, ms_access_token: &str) -> Result<XboxToken, AppError> {
    let res = http
        .post(XBL_URL)
        .header("Accept", "application/json")
        .json(&json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                // "d=" marks a token issued to a third-party Azure app.
                "RpsTicket": format!("d={ms_access_token}"),
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT",
        }))
        .send()
        .await?;
    if !res.status().is_success() {
        return Err(AppError::Auth(format!(
            "Xbox Live rejected the Microsoft sign-in ({}). Try signing in again.",
            res.status()
        )));
    }
    Ok(res.json().await?)
}

async fn xsts_token(http: &Client, xbl_token: &str) -> Result<XboxToken, AppError> {
    let res = http
        .post(XSTS_URL)
        .header("Accept", "application/json")
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT",
        }))
        .send()
        .await?;
    if res.status() == StatusCode::UNAUTHORIZED {
        #[derive(Deserialize)]
        #[serde(rename_all = "PascalCase")]
        struct XstsError {
            x_err: u64,
        }
        let err: XstsError = res.json().await.map_err(|_| unexpected("Xbox Live"))?;
        return Err(AppError::Auth(xsts_error_message(err.x_err)));
    }
    if !res.status().is_success() {
        return Err(AppError::Auth(format!("Xbox Live returned an error ({}).", res.status())));
    }
    Ok(res.json().await?)
}

/// XErr codes are documented by Microsoft for the XSTS endpoint; each one has a
/// specific fix the player can apply themselves.
fn xsts_error_message(code: u64) -> String {
    match code {
        2148916227 => "This Microsoft account is banned from Xbox services.".into(),
        2148916229 => "This account needs a parent's permission to play online. \
            A parent can allow it in Microsoft Family Safety settings."
            .into(),
        2148916233 => "This Microsoft account doesn't have an Xbox profile yet. \
            Sign in once at minecraft.net to create one, then try again."
            .into(),
        2148916234 => "You need to accept the Xbox terms of use. \
            Sign in at xbox.com, accept them, then try again."
            .into(),
        2148916235 => "Xbox Live isn't available in your country or region, \
            so Minecraft sign-in can't continue."
            .into(),
        2148916236 | 2148916237 => "This account needs adult verification (required in South Korea). \
            Complete it on xbox.com, then try again."
            .into(),
        2148916238 => "This is a child account that isn't part of a Microsoft Family. \
            An adult must add it to a Family group at account.microsoft.com/family."
            .into(),
        _ => format!("Xbox Live refused the sign-in (error {code})."),
    }
}

async fn minecraft_token(http: &Client, user_hash: &str, xsts_token: &str) -> Result<McSession, AppError> {
    #[derive(Deserialize)]
    struct McToken {
        access_token: String,
        #[serde(default = "one_day")]
        expires_in: u64,
    }
    fn one_day() -> u64 {
        86_400
    }

    let res = http
        .post(MC_LOGIN_URL)
        .json(&json!({ "identityToken": format!("XBL3.0 x={user_hash};{xsts_token}") }))
        .send()
        .await?;
    match res.status() {
        status if status.is_success() => {
            let token = res.json::<McToken>().await?;
            Ok(McSession { access_token: token.access_token, expires_in: token.expires_in })
        }
        StatusCode::FORBIDDEN => {
            let body = res.text().await.unwrap_or_default();
            // Mojang allow-lists every third-party Azure app ID. Until this
            // app's ID is approved, every login fails here with this message.
            if body.contains("Invalid app registration") {
                Err(AppError::Config(
                    "This launcher's Azure app isn't approved for Minecraft sign-in yet. \
                     New app IDs must be allow-listed by Mojang: https://aka.ms/mce-reviewappid"
                        .into(),
                ))
            } else {
                Err(AppError::Auth("Minecraft services rejected the sign-in (403).".into()))
            }
        }
        StatusCode::TOO_MANY_REQUESTS => Err(AppError::Auth(
            "Too many sign-in attempts. Wait a minute, then try again.".into(),
        )),
        status => Err(AppError::Auth(format!("Minecraft services returned an error ({status})."))),
    }
}

async fn fetch_profile(http: &Client, mc_token: &str) -> Result<Profile, AppError> {
    let res = http.get(MC_PROFILE_URL).bearer_auth(mc_token).send().await?;
    if res.status() == StatusCode::NOT_FOUND {
        // No profile means either the game isn't owned, or it's owned but the
        // player never chose a name. Entitlements tell the two apart.
        let message = if owns_minecraft(http, mc_token).await? {
            "You own Minecraft but haven't chosen a profile name yet. \
             Set one at minecraft.net, then try again."
        } else {
            "This Microsoft account doesn't own Minecraft: Java Edition. \
             If you play through Xbox Game Pass, open the official Minecraft Launcher once \
             to set up your profile, then try again."
        };
        return Err(AppError::Auth(message.into()));
    }
    Ok(res.error_for_status()?.json().await?)
}

async fn owns_minecraft(http: &Client, mc_token: &str) -> Result<bool, AppError> {
    #[derive(Deserialize)]
    struct Entitlements {
        #[serde(default)]
        items: Vec<serde_json::Value>,
    }

    let res = http.get(MC_ENTITLEMENTS_URL).bearer_auth(mc_token).send().await?;
    let entitlements: Entitlements = res.error_for_status()?.json().await?;
    Ok(!entitlements.items.is_empty())
}

/// Turns Azure AD errors into instructions. AADSTS codes are stable, and the
/// common ones here are caused by how the Azure app was registered.
fn describe_oauth_error(err: &OAuthError) -> AppError {
    let desc = &err.error_description;
    if desc.contains("AADSTS50011") || desc.contains("AADSTS500113") {
        return AppError::Config(format!(
            "The Azure app is missing the sign-in redirect. In the Azure portal, open the app's \
             Authentication page, choose Add a platform → Mobile and desktop applications, and tick {REDIRECT_URI}."
        ));
    }
    if desc.contains("AADSTS7000218") || desc.contains("AADSTS70002") {
        return AppError::Config(
            "The Azure app isn't set up as a desktop app. On its Authentication page, add the redirect under \
             \"Mobile and desktop applications\" (not \"Web\") and turn on \"Allow public client flows\"."
                .into(),
        );
    }
    if desc.contains("AADSTS700016") || desc.contains("AADSTS700038") || err.error == "unauthorized_client" {
        return AppError::Config(
            "The configured Microsoft client ID wasn't recognised for personal Microsoft accounts. \
             Check the client ID on the sign-in screen and that the Azure app supports personal accounts."
                .into(),
        );
    }
    // Azure appends trace/correlation IDs on later lines; the first line is the useful part.
    let detail = desc.lines().next().filter(|l| !l.is_empty()).unwrap_or(&err.error);
    let detail = if detail.is_empty() { "unknown error" } else { detail };
    AppError::Auth(format!("Microsoft sign-in failed: {detail}"))
}

fn unexpected(service: &str) -> AppError {
    AppError::Auth(format!("{service} sent an unexpected response. Try again in a moment."))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pkce_round_trip() {
        let pkce = Pkce::new();
        assert!((43..=128).contains(&pkce.verifier.len()), "RFC 7636 verifier length");
        let url = Url::parse(&pkce.authorize_url("507b785f-1ce8-4a33-b5cf-2850e76a326d")).unwrap();
        let q = |k: &str| url.query_pairs().find(|(n, _)| n == k).map(|(_, v)| v.into_owned()).unwrap();
        assert_eq!(q("code_challenge"), URL_SAFE_NO_PAD.encode(Sha256::digest(pkce.verifier.as_bytes())));
        assert_eq!(q("redirect_uri"), REDIRECT_URI);

        let ok = format!("{REDIRECT_URI}?code=abc&state={}", pkce.state);
        assert_eq!(pkce.code_from_redirect(&ok).unwrap(), "abc");
        // A redirect for some other request (wrong state) is refused.
        assert!(pkce.code_from_redirect(&format!("{REDIRECT_URI}?code=abc&state=nope")).is_err());
        let cancelled = format!("{REDIRECT_URI}?error=access_denied&state={}", pkce.state);
        assert!(matches!(pkce.code_from_redirect(&cancelled), Err(AppError::Auth(_))));
        let bad_app = format!("{REDIRECT_URI}?error=invalid_request&error_description=AADSTS50011%3A+mismatch");
        assert!(matches!(pkce.code_from_redirect(&bad_app), Err(AppError::Config(_))));
    }

    #[test]
    fn parses_xbox_token_shape() {
        let token: XboxToken = serde_json::from_str(
            r#"{"IssueInstant":"2026-01-01T00:00:00Z","NotAfter":"2026-01-02T00:00:00Z",
                "Token":"abc","DisplayClaims":{"xui":[{"uhs":"123"}]}}"#,
        )
        .unwrap();
        assert_eq!(token.token, "abc");
        assert_eq!(token.user_hash().unwrap(), "123");
    }

    #[test]
    fn picks_active_skin_over_https() {
        let profile: Profile = serde_json::from_str(
            r#"{"id":"069a79f444e94726a5befca90e38aaf5","name":"Notch","skins":[
                {"state":"INACTIVE","url":"http://textures.minecraft.net/texture/old"},
                {"state":"ACTIVE","url":"http://textures.minecraft.net/texture/new"}]}"#,
        )
        .unwrap();
        assert_eq!(
            profile.active_skin_url().as_deref(),
            Some("https://textures.minecraft.net/texture/new")
        );
    }

    #[test]
    fn explains_public_client_misconfiguration() {
        let err = describe_oauth_error(&OAuthError {
            error: "invalid_client".into(),
            error_description: "AADSTS7000218: The request body must contain ...\r\nTrace ID: x".into(),
        });
        assert!(matches!(err, AppError::Config(m) if m.contains("Allow public client flows")));
    }
}
