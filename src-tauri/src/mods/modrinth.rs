//! Modrinth API v2 (no key required).

use std::collections::HashMap;

use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;

use super::SearchHit;
use crate::{error::AppError, meta::loaders::LoaderKind};

const API: &str = "https://api.modrinth.com/v2";

#[derive(Clone, Debug, Deserialize)]
pub struct Version {
    pub id: String,
    pub project_id: String,
    pub version_number: String,
    #[serde(default)]
    pub version_type: String,
    #[serde(default)]
    pub files: Vec<VersionFile>,
    #[serde(default)]
    pub dependencies: Vec<Dependency>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct VersionFile {
    pub url: String,
    pub filename: String,
    #[serde(default)]
    pub primary: bool,
    pub size: u64,
    pub hashes: HashMap<String, String>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct Dependency {
    pub project_id: Option<String>,
    pub version_id: Option<String>,
    pub dependency_type: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Project {
    pub id: String,
    pub slug: String,
    pub title: String,
    #[serde(default)]
    pub icon_url: Option<String>,
}

impl Version {
    pub fn primary_file(&self) -> Option<&VersionFile> {
        self.files.iter().find(|f| f.primary).or_else(|| self.files.first())
    }
}

pub async fn search(
    http: &Client,
    query: &str,
    game_version: &str,
    loader: LoaderKind,
    sort: &str,
    offset: u32,
) -> Result<(Vec<SearchHit>, u32), AppError> {
    #[derive(Deserialize)]
    struct Response {
        hits: Vec<Hit>,
        total_hits: u32,
    }
    #[derive(Deserialize)]
    struct Hit {
        project_id: String,
        slug: String,
        title: String,
        description: String,
        #[serde(default)]
        icon_url: Option<String>,
        downloads: u64,
        author: String,
        #[serde(default)]
        display_categories: Vec<String>,
    }

    // Facets are ANDed between arrays and ORed within one.
    let mut facets = vec![json!(["project_type:mod"]), json!([format!("versions:{game_version}")])];
    let loaders: Vec<String> = loader.modrinth_loaders().iter().map(|l| format!("categories:{l}")).collect();
    if !loaders.is_empty() {
        facets.push(json!(loaders));
    }
    let index = match sort {
        "downloads" | "updated" | "newest" | "follows" => sort,
        _ => "relevance",
    };
    let res: Response = http
        .get(format!("{API}/search"))
        .query(&[
            ("query", query.to_owned()),
            ("facets", serde_json::to_string(&facets)?),
            ("index", index.to_owned()),
            ("offset", offset.to_string()),
            ("limit", "30".to_owned()),
        ])
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let hits = res
        .hits
        .into_iter()
        .map(|h| SearchHit {
            platform: "modrinth".into(),
            project_id: h.project_id,
            slug: h.slug.clone(),
            title: h.title,
            description: h.description,
            icon_url: h.icon_url.filter(|u| !u.is_empty()),
            downloads: h.downloads,
            author: h.author,
            categories: h.display_categories,
            url: format!("https://modrinth.com/mod/{}", h.slug),
        })
        .collect();
    Ok((hits, res.total_hits))
}

pub async fn project(http: &Client, id: &str) -> Result<Project, AppError> {
    let res = http.get(format!("{API}/project/{id}")).send().await?;
    if res.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(AppError::Invalid(format!("Modrinth has no project \"{id}\".")));
    }
    Ok(res.error_for_status()?.json().await?)
}

pub async fn version(http: &Client, id: &str) -> Result<Version, AppError> {
    Ok(http.get(format!("{API}/version/{id}")).send().await?.error_for_status()?.json().await?)
}

/// Newest compatible version, preferring releases over betas over alphas.
pub async fn best_version(
    http: &Client,
    project: &str,
    game_version: &str,
    loader: LoaderKind,
) -> Result<Option<Version>, AppError> {
    let loaders = serde_json::to_string(loader.modrinth_loaders())?;
    let game_versions = serde_json::to_string(&[game_version])?;
    let res = http
        .get(format!("{API}/project/{project}/version"))
        .query(&[("loaders", loaders), ("game_versions", game_versions)])
        .send()
        .await?;
    if res.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    let versions: Vec<Version> = res.error_for_status()?.json().await?;
    let rank = |t: &str| match t {
        "release" => 0,
        "beta" => 1,
        _ => 2,
    };
    // The API returns newest first; min_by_key keeps the first of equal rank.
    Ok(versions.into_iter().min_by_key(|v| rank(&v.version_type)))
}

/// sha1 → newest compatible version, for every file Modrinth recognises.
pub async fn latest_for_hashes(
    http: &Client,
    hashes: &[String],
    game_version: &str,
    loader: LoaderKind,
) -> Result<HashMap<String, Version>, AppError> {
    if hashes.is_empty() {
        return Ok(HashMap::new());
    }
    Ok(http
        .post(format!("{API}/version_files/update"))
        .json(&json!({
            "hashes": hashes,
            "algorithm": "sha1",
            "loaders": loader.modrinth_loaders(),
            "game_versions": [game_version],
        }))
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?)
}

pub async fn projects(http: &Client, ids: &[String]) -> Result<Vec<Project>, AppError> {
    if ids.is_empty() {
        return Ok(vec![]);
    }
    Ok(http
        .get(format!("{API}/projects"))
        .query(&[("ids", serde_json::to_string(ids)?)])
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?)
}
