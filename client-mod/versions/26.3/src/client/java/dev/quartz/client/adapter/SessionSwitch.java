package dev.quartz.client.adapter;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.FriendsService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.ProfileResult;
import dev.quartz.client.mixin.MinecraftSessionAccessor;
import dev.quartz.core.accounts.LauncherBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Swaps the signed-in account on 26.3, rebuilding what Minecraft's
 * constructor derives from it: profile, Minecraft services API, chat
 * signing keys, reporting, friends and telemetry. Everything new is built
 * first and only then swapped in, so a failure leaves the old session whole.
 */
final class SessionSwitch {
	private SessionSwitch() {
	}

	static void apply(LauncherBridge.Session s) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftSessionAccessor fields = (MinecraftSessionAccessor) mc;
		UUID uuid = s.id();
		Optional<String> xuid = Optional.ofNullable(s.xuid).filter(x -> !x.isEmpty() && !"0".equals(x));
		User user = new User(s.username, uuid, s.accessToken, xuid, Optional.empty());

		MinecraftServicesDiscoveryService discovery = MinecraftServicesDiscoveryService.create(mc.getProxy(), true);
		UserApiService api = s.online() ? discovery.createUserApiService(s.accessToken) : UserApiService.OFFLINE;
		FriendsService friends = discovery.createFriendsService(s.accessToken);
		RemoteFriendListUpdateHandler friendUpdates = new RemoteFriendListUpdateHandler(friends, mc);
		PlayerSocialManager social = new PlayerSocialManager(mc, api, friends, friendUpdates);
		ProfileKeyPairManager keys = s.online()
			? ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath())
			: ProfileKeyPairManager.EMPTY_KEY_MANAGER;
		ReportingContext reporting = ReportingContext.create(ReportEnvironment.local(), api);
		ClientTelemetryManager telemetry = new ClientTelemetryManager(mc, api, user);
		CompletableFuture<ProfileResult> profile = CompletableFuture.supplyAsync(
			() -> mc.services().sessionService().fetchProfile(uuid, true), Util.nonCriticalIoPool());
		CompletableFuture<UserApiService.UserProperties> properties = CompletableFuture.supplyAsync(() -> {
			try {
				return api.fetchProperties();
			} catch (AuthenticationException e) {
				return UserApiService.OFFLINE_PROPERTIES;
			}
		}, Util.nonCriticalIoPool());

		RemoteFriendListUpdateHandler oldFriendUpdates = fields.quartz$friendListHandler();
		ClientTelemetryManager oldTelemetry = fields.quartz$telemetry();
		fields.quartz$setUser(user);
		fields.quartz$setProfileFuture(profile);
		fields.quartz$setUserApiService(api);
		fields.quartz$setUserPropertiesFuture(properties);
		fields.quartz$setProfileKeyPairManager(keys);
		fields.quartz$setReportingContext(reporting);
		fields.quartz$setFriendListHandler(friendUpdates);
		fields.quartz$setPlayerSocialManager(social);
		fields.quartz$setTelemetry(telemetry);

		oldFriendUpdates.close();
		oldTelemetry.close();
		if (social.isFriendListEnabled()) {
			friendUpdates.start();
		}
	}
}
