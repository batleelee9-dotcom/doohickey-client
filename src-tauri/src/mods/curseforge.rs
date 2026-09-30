//! CurseForge API v1. Requires an API key from console.curseforge.com, which
//! each launcher must obtain under CurseForge's terms; players can paste one
//! into Settings. Mods whose authors disabled third-party distribution have no
//! download URL — those are opened in the browser instead of downloaded.

use reqwest::Client;
use serde::Deserialize;

use super::SearchHit;
use crate::{error::AppError, meta::loaders::LoaderKind};

const API: &str = "https://api.curseforge.com/v1";
const MINECRAFT: &str = "432";
const MODS_CLASS: &str = "6";

fn loader_type(kind: LoaderKind) -> Option<&'static str> {
    match kind {
        LoaderKind::Forge => Some("1"),
        // CurseForge files Legacy Fabric mods under Fabric.
        LoaderKind::Fabric | LoaderKind::LegacyFabric => Some("4"),
        LoaderKind::Quilt => Some("5"),
        LoaderKind::NeoForge => Some("6"),
        LoaderKind::Vanilla => None,
    }
}

#[derive(Deserialize)]
struct Envelope<T> {
    data: T,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Mod {
    pub id: u64,
    pub name: String,
    pub slug: String,
    #[serde(default)]
    pub summary: String,
    #[serde(default)]
    pub download_count: f64,
    #[serde(default)]
    pub authors: Vec<Author>,
    #[serde(default)]
    pub logo: Option<Logo>,
    #[serde(default)]
    pub links: Option<Links>,
}

#[derive(Deserialize)]
pub struct Author {
    pub name: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Logo {
    pub thumbnail_url: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Links {
    pub website_url: Option<String>,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct File {
    pub id: u64,
    pub file_name: String,
    pub download_url: Option<String>,
    #[serde(default)]
    pub hashes: Vec<Hash>,
    #[serde(default)]
    pub file_length: u64,
    /// 1 = release, 2 = beta, 3 = alpha.
    #[serde(default)]
    pub release_type: u8,
    #[serde(default)]
    pub file_date: String,
    #[serde(default)]
    pub dependencies: Vec<FileDependency>,
}

#[derive(Clone, Deserialize)]
pub struct Hash {
    pub value: String,
    /// 1 = sha1, 2 = md5.
    pub algo: u8,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FileDependency {
    pub mod_id: u64,
    /// 3 = required.
    pub relation_type: u8,
}

impl File {
    pub fn sha1(&self) -> Option<String> {
        self.hashes.iter().find(|h| h.algo == 1).map(|h| h.value.clone())
    }
}

fn request(http: &Client, key: &str, url: String) -> reqwest::RequestBuilder {
    http.get(url).header("x-api-key", key).header("Accept", "application/json")
}

fn check_status(res: reqwest::Response) -> Result<reqwest::Response, AppError> {
    match res.status().as_u16() {
        401 | 403 => Err(AppError::Config("CurseForge rejected the API key. Check it in Settings → Integrations.".into())),
        _ => Ok(res.error_for_status()?),
    }
}

pub async fn search(
    http: &Client,
    key: &str,
    query: &str,
    game_version: &str,
    loader: LoaderKind,
    sort: &str,
    offset: u32,
) -> Result<(Vec<SearchHit>, u32), AppError> {
    #[derive(Deserialize)]
    struct Response {
        data: Vec<Mod>,
        pagination: Pagination,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Pagination {
        total_count: u32,
    }
    // sortField: 1 featured, 2 popularity, 3 last updated, 6 downloads.
    let sort_field = match sort {
        "downloads" => "6",
        "updated" | "newest" => "3",
        _ => "2",
    };
    let mut params = vec![
        ("gameId", MINECRAFT.to_owned()),
        ("classId", MODS_CLASS.to_owned()),
        ("searchFilter", query.to_owned()),
        ("gameVersion", game_version.to_owned()),
        ("sortField", sort_field.to_owned()),
        ("sortOrder", "desc".to_owned()),
        ("index", offset.to_string()),
        ("pageSize", "30".to_owned()),
    ];
    if let Some(t) = loader_type(loader) {
        params.push(("modLoaderType", t.to_owned()));
    }
    let res = request(http, key, format!("{API}/mods/search")).query(&params).send().await?;
    let body: Response = check_status(res)?.json().await?;
    let hits = body
        .data
        .into_iter()
        .map(|m| SearchHit {
            platform: "curseforge".into(),
            project_id: m.id.to_string(),
            slug: m.slug.clone(),
            title: m.name,
            description: m.summary,
            icon_url: m.logo.and_then(|l| l.thumbnail_url),
            downloads: m.download_count as u64,
            author: m.authors.first().map(|a| a.name.clone()).unwrap_or_default(),
            categories: vec![],
            url: m
                .links
                .and_then(|l| l.website_url)
                .unwrap_or_else(|| format!("https://www.curseforge.com/minecraft/mc-mods/{}", m.slug)),
        })
        .collect();
    Ok((hits, body.pagination.total_count.min(10_000)))
}

pub async fn get_mod(http: &Client, key: &str, id: &str) -> Result<Mod, AppError> {
    let res = request(http, key, format!("{API}/mods/{id}")).send().await?;
    Ok(check_status(res)?.json::<Envelope<Mod>>().await?.data)
}

/// Newest compatible file, preferring releases.
pub async fn best_file(
    http: &Client,
    key: &str,
    mod_id: &str,
    game_version: &str,
    loader: LoaderKind,
) -> Result<Option<File>, AppError> {
    let mut params = vec![("gameVersion", game_version.to_owned()), ("pageSize", "50".to_owned())];
    if let Some(t) = loader_type(loader) {
        params.push(("modLoaderType", t.to_owned()));
    }
    let res = request(http, key, format!("{API}/mods/{mod_id}/files")).query(&params).send().await?;
    let mut files: Vec<File> = check_status(res)?.json::<Envelope<Vec<File>>>().await?.data;
    files.sort_by(|a, b| b.file_date.cmp(&a.file_date));
    Ok(files.into_iter().min_by_key(|f| if f.release_type == 0 { 1 } else { f.release_type }))
}
