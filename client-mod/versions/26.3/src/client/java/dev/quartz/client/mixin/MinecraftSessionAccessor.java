package dev.quartz.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** Everything in {@link Minecraft} that belongs to the signed-in account. */
@Mixin(Minecraft.class)
public interface MinecraftSessionAccessor {
	@Mutable
	@Accessor("user")
	void quartz$setUser(User user);

	@Mutable
	@Accessor("profileFuture")
	void quartz$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("userApiService")
	void quartz$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void quartz$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("profileKeyPairManager")
	void quartz$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	void quartz$setReportingContext(ReportingContext context);

	@Mutable
	@Accessor("playerSocialManager")
	void quartz$setPlayerSocialManager(PlayerSocialManager manager);

	@Accessor("remoteFriendListUpdateHandler")
	RemoteFriendListUpdateHandler quartz$friendListHandler();

	@Mutable
	@Accessor("remoteFriendListUpdateHandler")
	void quartz$setFriendListHandler(RemoteFriendListUpdateHandler handler);

	@Accessor("telemetryManager")
	ClientTelemetryManager quartz$telemetry();

	@Mutable
	@Accessor("telemetryManager")
	void quartz$setTelemetry(ClientTelemetryManager manager);
}
