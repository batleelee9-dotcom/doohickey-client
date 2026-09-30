package dev.quartz.core.accounts;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.quartz.core.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Talks to the Doohickey launcher that started this game: it lists the
 * accounts added to Doohickey and hands out a fresh session for one of them.
 *
 * The launcher writes {@code <game dir>/.quartz/bridge.json} (port + secret)
 * just before launch; it's read once here and deleted straight away.
 */
public final class LauncherBridge {
	private static final Gson GSON = new Gson();
	private static int port;
	private static String secret;

	public static final class Account {
		public String id;
		public String username;
		/** "microsoft" or "offline" */
		public String kind;
	}

	public static final class Session {
		public String username;
		/** Without dashes. */
		public String uuid;
		public String accessToken;
		/** "msa" or "legacy" */
		public String userType;
		public String xuid;

		public UUID id() {
			String u = uuid;
			return UUID.fromString(u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16) + "-" + u.substring(16, 20) + "-" + u.substring(20));
		}

		/** Offline accounts carry a placeholder token and can't use Minecraft services. */
		public boolean online() {
			return "msa".equals(userType);
		}
	}

	private LauncherBridge() {
	}

	public static void init(Path gameDir) {
		Path file = gameDir.resolve(".quartz").resolve("bridge.json");
		try {
			if (!Files.exists(file)) {
				return;
			}
			JsonObject json = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), JsonObject.class);
			port = json.get("port").getAsInt();
			secret = json.get("secret").getAsString();
		} catch (IOException | RuntimeException e) {
			Log.warn("Couldn't read the launcher handshake: " + e.getMessage());
		} finally {
			try {
				Files.deleteIfExists(file);
			} catch (IOException ignored) {
			}
		}
	}

	/** False when the game wasn't started by Doohickey. */
	public static boolean available() {
		return secret != null;
	}

	public static List<Account> accounts() throws IOException {
		JsonObject json = request("GET", "/v1/accounts");
		List<Account> list = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray("accounts")) {
			list.add(GSON.fromJson(e, Account.class));
		}
		return list;
	}

	/** Signs in as {@code accountId} (refreshing Microsoft tokens if needed). */
	public static Session session(String accountId) throws IOException {
		return GSON.fromJson(request("POST", "/v1/accounts/" + accountId + "/session"), Session.class);
	}

	private static JsonObject request(String method, String path) throws IOException {
		if (!available()) {
			throw new IOException("Start the game from the Doohickey launcher to switch accounts.");
		}
		HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
		c.setRequestMethod(method);
		c.setRequestProperty("Authorization", "Bearer " + secret);
		c.setConnectTimeout(3000);
		// Refreshing a Microsoft sign-in can take a few seconds.
		c.setReadTimeout(30000);
		if ("POST".equals(method)) {
			c.setDoOutput(true);
			c.setFixedLengthStreamingMode(0);
		}
		try {
			int status = c.getResponseCode();
			InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
			JsonObject body = GSON.fromJson(in == null ? "{}" : read(in), JsonObject.class);
			if (status != 200) {
				throw new IOException(body != null && body.has("error") ? body.get("error").getAsString() : "The launcher answered " + status);
			}
			return body;
		} catch (java.net.ConnectException e) {
			throw new IOException("The Doohickey launcher isn't running.");
		} finally {
			c.disconnect();
		}
	}

	private static String read(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		for (int n; (n = in.read(buf)) > 0; ) {
			out.write(buf, 0, n);
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}
}
