//! Saved servers and the Server List Ping protocol (the same request the
//! multiplayer screen sends), so the launcher shows MOTD, players and latency.

use std::{
    sync::{Mutex, PoisonError},
    time::{Duration, Instant},
};

use serde::{Deserialize, Serialize};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpStream,
};

use crate::{error::AppError, fsutil};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Server {
    pub id: String,
    pub name: String,
    pub address: String,
    #[serde(default)]
    pub favorite: bool,
    /// Profile used for quick-join (a per-server mod/config setup).
    #[serde(default)]
    pub instance_id: Option<String>,
    pub added_at: u64,
}

pub struct ServerStore {
    path: std::path::PathBuf,
    data: Mutex<Vec<Server>>,
}

impl ServerStore {
    pub fn load(path: std::path::PathBuf) -> Result<Self, AppError> {
        let data = fsutil::read_json(&path)?.unwrap_or_default();
        Ok(Self { path, data: Mutex::new(data) })
    }

    pub fn list(&self) -> Vec<Server> {
        let mut list = self.data.lock().unwrap_or_else(PoisonError::into_inner).clone();
        list.sort_by(|a, b| b.favorite.cmp(&a.favorite).then(a.added_at.cmp(&b.added_at)));
        list
    }

    pub fn upsert(&self, mut server: Server) -> Result<Vec<Server>, AppError> {
        server.name = server.name.trim().to_owned();
        server.address = server.address.trim().to_owned();
        if server.address.is_empty() {
            return Err(AppError::Invalid("Enter the server address.".into()));
        }
        parse_address(&server.address)?;
        if server.name.is_empty() {
            server.name = server.address.clone();
        }
        {
            let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
            match data.iter_mut().find(|s| s.id == server.id) {
                Some(existing) => *existing = server,
                None => data.push(server),
            }
            fsutil::write_json_atomic(&self.path, &*data)?;
        }
        Ok(self.list())
    }

    pub fn remove(&self, id: &str) -> Result<Vec<Server>, AppError> {
        {
            let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
            data.retain(|s| s.id != id);
            fsutil::write_json_atomic(&self.path, &*data)?;
        }
        Ok(self.list())
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    pub latency_ms: u64,
    pub version: String,
    pub protocol: i64,
    pub players_online: i64,
    pub players_max: i64,
    pub player_sample: Vec<String>,
    /// Raw MOTD: a string with § codes or a chat component; the UI renders it.
    pub motd: serde_json::Value,
    pub favicon: Option<String>,
}

/// "host", "host:port" or "[v6]:port" → (host, explicit port).
pub fn parse_address(address: &str) -> Result<(String, Option<u16>), AppError> {
    let address = address.trim();
    let invalid = || AppError::Invalid(format!("\"{address}\" isn't a valid server address."));
    if let Some(rest) = address.strip_prefix('[') {
        let (host, rest) = rest.split_once(']').ok_or_else(invalid)?;
        let port = match rest.strip_prefix(':') {
            Some(p) => Some(p.parse().map_err(|_| invalid())?),
            None => None,
        };
        return Ok((host.to_owned(), port));
    }
    match address.rsplit_once(':') {
        Some((host, port)) if !host.contains(':') => {
            Ok((host.to_owned(), Some(port.parse().map_err(|_| invalid())?)))
        }
        _ if address.is_empty() || address.contains(' ') => Err(invalid()),
        _ => Ok((address.to_owned(), None)),
    }
}

/// Whether an address is on the local network: loopback, private or
/// link-local IPs, `localhost`, or `.local`/`.lan` names. Other host names
/// count as public — they're not resolved, so a name can't sneak through.
pub fn is_lan_address(address: &str) -> bool {
    let Ok((host, _)) = parse_address(address) else { return false };
    let host = host.to_ascii_lowercase();
    match host.parse::<std::net::IpAddr>() {
        Ok(std::net::IpAddr::V4(ip)) => ip.is_loopback() || ip.is_private() || ip.is_link_local(),
        // fc00::/7 unique-local, fe80::/10 link-local.
        Ok(std::net::IpAddr::V6(ip)) => {
            ip.is_loopback() || (ip.segments()[0] & 0xfe00) == 0xfc00 || (ip.segments()[0] & 0xffc0) == 0xfe80
        }
        Err(_) => host == "localhost" || host.ends_with(".local") || host.ends_with(".lan"),
    }
}

/// Resolves the `_minecraft._tcp` SRV record when no port was given, like the
/// game does (many networks put their server behind one).
async fn resolve(host: &str, port: Option<u16>) -> (String, u16) {
    if let Some(port) = port {
        return (host.to_owned(), port);
    }
    if host.parse::<std::net::IpAddr>().is_ok() {
        return (host.to_owned(), 25565);
    }
    if let Ok(resolver) = hickory_resolver::TokioAsyncResolver::tokio_from_system_conf() {
        if let Ok(Ok(srv)) =
            tokio::time::timeout(Duration::from_secs(3), resolver.srv_lookup(format!("_minecraft._tcp.{host}."))).await
        {
            if let Some(record) = srv.iter().min_by_key(|r| r.priority()) {
                let target = record.target().to_utf8();
                return (target.trim_end_matches('.').to_owned(), record.port());
            }
        }
    }
    (host.to_owned(), 25565)
}

pub async fn ping(address: &str) -> Result<ServerStatus, AppError> {
    let (host, port) = parse_address(address)?;
    let (target, port) = resolve(&host, port).await;
    let offline = || AppError::Network(format!("{address} didn't respond."));

    let result = tokio::time::timeout(Duration::from_secs(6), async {
        let mut stream = TcpStream::connect((target.as_str(), port)).await.map_err(|_| offline())?;
        stream.set_nodelay(true).ok();

        // Handshake: protocol version (any value works for status), the
        // address as typed (virtual hosts route on it), port, next state 1.
        let mut handshake = Vec::new();
        write_varint(&mut handshake, 0x00);
        write_varint(&mut handshake, 773);
        write_string(&mut handshake, &host);
        handshake.extend_from_slice(&port.to_be_bytes());
        write_varint(&mut handshake, 1);
        send_packet(&mut stream, &handshake).await?;
        send_packet(&mut stream, &[0x00]).await?; // status request

        let started = Instant::now();
        let payload = read_packet(&mut stream).await?;
        let status_latency = started.elapsed();
        let mut cursor = &payload[..];
        if read_varint_slice(&mut cursor)? != 0x00 {
            return Err(AppError::Network("Unexpected status response.".into()));
        }
        let len = read_varint_slice(&mut cursor)? as usize;
        let json = cursor.get(..len).ok_or_else(|| AppError::Network("Truncated status response.".into()))?;
        let status: serde_json::Value =
            serde_json::from_slice(json).map_err(|_| AppError::Network("Server sent an invalid status.".into()))?;

        // Ping/pong for a real round-trip time; some proxies don't answer it.
        let mut ping = vec![0x01];
        ping.extend_from_slice(&(fsutil::unix_now_ms() as i64).to_be_bytes());
        let ping_started = Instant::now();
        let latency = match async {
            send_packet(&mut stream, &ping).await?;
            read_packet(&mut stream).await
        }
        .await
        {
            Ok(_) => ping_started.elapsed(),
            Err(_) => status_latency,
        };
        Ok::<_, AppError>((status, latency))
    })
    .await
    .map_err(|_| offline())??;

    let (status, latency) = result;
    Ok(ServerStatus {
        latency_ms: latency.as_millis() as u64,
        version: status["version"]["name"].as_str().unwrap_or("").to_owned(),
        protocol: status["version"]["protocol"].as_i64().unwrap_or(-1),
        players_online: status["players"]["online"].as_i64().unwrap_or(0),
        players_max: status["players"]["max"].as_i64().unwrap_or(0),
        player_sample: status["players"]["sample"]
            .as_array()
            .map(|a| a.iter().filter_map(|p| p["name"].as_str().map(str::to_owned)).take(12).collect())
            .unwrap_or_default(),
        motd: status.get("description").cloned().unwrap_or(serde_json::Value::Null),
        favicon: status["favicon"].as_str().filter(|f| f.starts_with("data:image/png;base64,")).map(str::to_owned),
    })
}

fn write_varint(buf: &mut Vec<u8>, mut value: i32) {
    loop {
        let mut byte = (value & 0x7f) as u8;
        value = ((value as u32) >> 7) as i32;
        if value != 0 {
            byte |= 0x80;
        }
        buf.push(byte);
        if value == 0 {
            break;
        }
    }
}

fn write_string(buf: &mut Vec<u8>, s: &str) {
    write_varint(buf, s.len() as i32);
    buf.extend_from_slice(s.as_bytes());
}

async fn send_packet(stream: &mut TcpStream, payload: &[u8]) -> Result<(), AppError> {
    let mut framed = Vec::with_capacity(payload.len() + 5);
    write_varint(&mut framed, payload.len() as i32);
    framed.extend_from_slice(payload);
    stream.write_all(&framed).await?;
    Ok(())
}

async fn read_varint(stream: &mut TcpStream) -> Result<i32, AppError> {
    let mut value = 0i32;
    for i in 0..5 {
        let byte = stream.read_u8().await?;
        value |= ((byte & 0x7f) as i32) << (7 * i);
        if byte & 0x80 == 0 {
            return Ok(value);
        }
    }
    Err(AppError::Network("Malformed response from server.".into()))
}

fn read_varint_slice(buf: &mut &[u8]) -> Result<i32, AppError> {
    let mut value = 0i32;
    for i in 0..5 {
        let (&byte, rest) = buf.split_first().ok_or_else(|| AppError::Network("Truncated response.".into()))?;
        *buf = rest;
        value |= ((byte & 0x7f) as i32) << (7 * i);
        if byte & 0x80 == 0 {
            return Ok(value);
        }
    }
    Err(AppError::Network("Malformed response from server.".into()))
}

async fn read_packet(stream: &mut TcpStream) -> Result<Vec<u8>, AppError> {
    let len = read_varint(stream).await?;
    // Status JSON with a favicon is ~30 KB; anything absurd is not a MC server.
    if !(1..=2_097_152).contains(&len) {
        return Err(AppError::Network("Server sent an invalid packet.".into()));
    }
    let mut payload = vec![0u8; len as usize];
    stream.read_exact(&mut payload).await?;
    Ok(payload)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lan_addresses() {
        for lan in ["localhost", "127.0.0.1:25565", "192.168.1.20", "10.0.0.5:25566", "172.20.3.4", "169.254.1.1", "[::1]:25565", "[fd12::1]", "minecraft.local", "box.lan"] {
            assert!(is_lan_address(lan), "{lan} is LAN");
        }
        for public in ["mc.hypixel.net", "8.8.8.8", "172.32.0.1", "[2001:db8::1]", "local.example.com", "not an address"] {
            assert!(!is_lan_address(public), "{public} is public");
        }
    }

    #[test]
    fn addresses() {
        assert_eq!(parse_address("mc.hypixel.net").unwrap(), ("mc.hypixel.net".into(), None));
        assert_eq!(parse_address("play.example.com:25566").unwrap(), ("play.example.com".into(), Some(25566)));
        assert_eq!(parse_address("[::1]:25565").unwrap(), ("::1".into(), Some(25565)));
        assert!(parse_address("bad host").is_err());
        assert!(parse_address("host:notaport").is_err());
    }

    #[test]
    fn varints() {
        for (value, bytes) in [(0, vec![0x00]), (127, vec![0x7f]), (128, vec![0x80, 0x01]), (25565, vec![0xdd, 0xc7, 0x01]), (-1, vec![0xff, 0xff, 0xff, 0xff, 0x0f])] {
            let mut buf = Vec::new();
            write_varint(&mut buf, value);
            assert_eq!(buf, bytes, "encode {value}");
            assert_eq!(read_varint_slice(&mut &bytes[..]).unwrap(), value, "decode {value}");
        }
    }
}
