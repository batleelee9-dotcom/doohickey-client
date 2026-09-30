use md5::{Digest, Md5};
use uuid::Uuid;

use crate::error::AppError;

/// Mirrors the rules Mojang enforces for real account names, so an offline
/// name is always one that could also exist online.
pub fn validate_username(name: &str) -> Result<(), AppError> {
    if !(3..=16).contains(&name.len()) {
        return Err(AppError::Invalid("Usernames must be 3–16 characters long.".into()));
    }
    if !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return Err(AppError::Invalid(
            "Usernames can only contain letters, numbers and underscores.".into(),
        ));
    }
    Ok(())
}

/// Same algorithm as Java's `UUID.nameUUIDFromBytes("OfflinePlayer:" + name)`,
/// which is what vanilla servers assign in offline mode — so worlds and
/// offline-mode servers keep a player's inventory and stats across launchers.
pub fn offline_uuid(name: &str) -> Uuid {
    let hash: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}")).into();
    uuid::Builder::from_md5_bytes(hash).into_uuid()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_java_name_uuid_from_bytes() {
        // Values produced by the vanilla server for these names in offline mode.
        assert_eq!(offline_uuid("Notch").to_string(), "b50ad385-829d-3141-a216-7e7d7539ba7f");
        assert_eq!(offline_uuid("Steve").to_string(), "5627dd98-e6be-3c21-b8a8-e92344183641");
    }

    #[test]
    fn validates_names() {
        assert!(validate_username("Steve_01").is_ok());
        assert!(validate_username("ab").is_err());
        assert!(validate_username("seventeen_chars_x").is_err());
        assert!(validate_username("bad name").is_err());
        assert!(validate_username("ünïcode").is_err());
    }
}
