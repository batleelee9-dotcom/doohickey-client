package dev.quartz.client.hud;

import dev.quartz.core.config.ClientConfig;
import dev.quartz.client.pvp.PvpTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Every built-in HUD module. Default positions are fractions of free space. */
public final class HudModules {
	public static final List<HudModule> ALL = List.of(
		core("fps"), core("cps"), new Ping(), core("coordinates"), core("keystrokes"),
		new Armor(), new Potions(), new Reach(), new Memory(), new ToggleStatus()
	);

	private HudModules() {
	}

	/** A module shared with every Minecraft version (core/hud), by id. */
	private static HudModule core(String id) {
		return dev.quartz.core.hud.Hud.ELEMENTS.stream().filter(e -> e.id.equals(id)).findFirst().map(CoreHudModule::new).orElseThrow();
	}

	static final class Ping extends HudModule {
		Ping() {
			super("ping", "Ping", true, 0.0f, 0.12f);
		}

		private String text() {
			Player player = mc().player;
			if (player == null || mc().getConnection() == null || mc().isLocalServer()) {
				return "0 ms";
			}
			PlayerInfo info = mc().getConnection().getPlayerInfo(player.getUUID());
			return (info == null ? 0 : info.getLatency()) + " ms";
		}

		public int width() {
			return boxWidth(text());
		}

		public int height() {
			return 16;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			textBox(g, text(), width());
		}
	}

	/** Worn armor and the held item, with durability. */
	static final class Armor extends HudModule {
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};

		Armor() {
			super("armor", "Armor status", true, 1.0f, 0.55f);
		}

		private List<ItemStack> items() {
			List<ItemStack> out = new ArrayList<>();
			Player p = mc().player;
			if (p == null) {
				return out;
			}
			for (EquipmentSlot slot : SLOTS) {
				ItemStack stack = p.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					out.add(stack);
				}
			}
			return out;
		}

		public boolean hasContent() {
			return !items().isEmpty();
		}

		public int width() {
			return 64;
		}

		public int height() {
			return Math.max(1, items().size()) * 18;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			ClientConfig c = ClientConfig.get();
			List<ItemStack> items = items();
			if (items.isEmpty() && preview) {
				g.text(font(), "Armor", 20, 5, c.textColor | 0xFF000000, c.textShadow);
				return;
			}
			int y = 0;
			for (ItemStack stack : items) {
				g.item(stack, 0, y);
				String label;
				int color = c.textColor | 0xFF000000;
				if (stack.isDamageableItem()) {
					int left = stack.getMaxDamage() - stack.getDamageValue();
					label = String.valueOf(left);
					float frac = left / (float) stack.getMaxDamage();
					color = frac > 0.5f ? 0xFF7CFC7C : frac > 0.2f ? 0xFFFFD166 : 0xFFFF6B6B;
				} else {
					label = stack.getCount() > 1 ? "x" + stack.getCount() : "";
				}
				g.text(font(), label, 20, y + 5, color, c.textShadow);
				y += 18;
			}
		}
	}

	/** Active effects with icon, level and time left. */
	static final class Potions extends HudModule {
		Potions() {
			super("potions", "Potion effects", true, 1.0f, 0.3f);
		}

		private List<MobEffectInstance> effects() {
			Player p = mc().player;
			return p == null ? List.of() : new ArrayList<>(p.getActiveEffects());
		}

		public boolean hasContent() {
			return !effects().isEmpty();
		}

		public int width() {
			return 110;
		}

		public int height() {
			return Math.max(1, effects().size()) * 20;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			ClientConfig c = ClientConfig.get();
			List<MobEffectInstance> effects = effects();
			if (effects.isEmpty() && preview) {
				g.text(font(), "Speed II  1:30", 22, 6, c.textColor | 0xFF000000, c.textShadow);
				return;
			}
			int y = 0;
			for (MobEffectInstance e : effects) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), 0, y + 1, 18, 18);
				String name = e.getEffect().value().getDisplayName().getString();
				if (e.getAmplifier() > 0) {
					name += " " + roman(e.getAmplifier() + 1);
				}
				g.text(font(), name, 22, y + 1, c.textColor | 0xFF000000, c.textShadow);
				g.text(font(), duration(e), 22, y + 10, e.endsWithin(200) ? 0xFFFF6B6B : 0xFFAAAAAA, c.textShadow);
				y += 20;
			}
		}

		private static String duration(MobEffectInstance e) {
			if (e.isInfiniteDuration()) {
				return "∞";
			}
			int secs = e.getDuration() / 20;
			return (secs / 60) + ":" + String.format("%02d", secs % 60);
		}

		private static String roman(int n) {
			return switch (n) {
				case 2 -> "II";
				case 3 -> "III";
				case 4 -> "IV";
				case 5 -> "V";
				default -> String.valueOf(n);
			};
		}
	}

	/** Distance of your last hit on an entity. */
	static final class Reach extends HudModule {
		Reach() {
			super("reach", "Reach display", false, 0.0f, 0.24f);
		}

		private String text() {
			double reach = PvpTweaks.lastReach();
			return reach <= 0 ? "— blocks" : String.format("%.2f blocks", reach);
		}

		public int width() {
			return boxWidth(text());
		}

		public int height() {
			return 16;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			textBox(g, text(), width());
		}
	}

	static final class Memory extends HudModule {
		Memory() {
			super("memory", "Memory", false, 0.0f, 0.3f);
		}

		private String text() {
			Runtime rt = Runtime.getRuntime();
			long used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
			return used + " / " + rt.maxMemory() / 1_048_576 + " MB";
		}

		public int width() {
			return boxWidth(text());
		}

		public int height() {
			return 16;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			textBox(g, text(), width());
		}
	}

	/** "[Sprinting (Toggled)]" style indicator for the toggle keys. */
	static final class ToggleStatus extends HudModule {
		ToggleStatus() {
			super("toggle_status", "Toggle sprint/sneak status", true, 0.0f, 1.0f);
		}

		private String text() {
			if (PvpTweaks.sneakToggled()) {
				return "[Sneaking (Toggled)]";
			}
			if (ClientConfig.get().sprintToggled) {
				Player p = mc().player;
				return p != null && p.isSprinting() ? "[Sprinting (Toggled)]" : "[Sprint Toggled]";
			}
			return "";
		}

		public boolean hasContent() {
			return !text().isEmpty();
		}

		public int width() {
			return Math.max(40, font().width(text().isEmpty() ? "[Sprint Toggled]" : text()) + 4);
		}

		public int height() {
			return 10;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			ClientConfig c = ClientConfig.get();
			String text = text().isEmpty() && preview ? "[Sprint Toggled]" : text();
			g.text(font(), text, 2, 1, c.textColor | 0xFF000000, true);
		}
	}
}
