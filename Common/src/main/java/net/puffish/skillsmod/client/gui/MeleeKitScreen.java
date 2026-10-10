package net.puffish.skillsmod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.combat.MeleeKit;
import net.puffish.skillsmod.arpg.combat.MeleeKitView;
import net.puffish.skillsmod.client.SkillsClientMod;

import java.util.Locale;

/** A preparation screen with explicit refresh; combat always runs through server commands. */
public final class MeleeKitScreen extends Screen {
	private MeleeKitView view;
	private MeleeKit.Attack selected = MeleeKit.Attack.HEAVY;
	private int scroll;
	private int maximumScroll;
	private int waitingTicks;
	private boolean waiting;
	private String feedback = "";

	public MeleeKitScreen(MeleeKitView view) {
		super(Text.literal("Melee kit"));
		this.view = view;
	}

	public void update(MeleeKitView view) {
		this.view = view;
		waiting = false;
		feedback = "";
		clearAndInit();
	}

	private int panel() {
		return Math.max(1, Math.min(560, width - 16));
	}

	private int left() {
		return (width - panel()) / 2;
	}

	private String input(MeleeKit.Attack attack) {
		return attack == MeleeKit.Attack.HEAVY ? "heavy" : "driving";
	}

	private MeleeKitView.Action action() {
		return view.actions().stream().filter(action -> action.attack() == selected).findFirst().orElse(null);
	}

	@Override
	protected void init() {
		if (view.actions().stream().noneMatch(action -> action.attack() == selected)) {
			selected = view.actions().isEmpty() ? MeleeKit.Attack.HEAVY : view.actions().getFirst().attack();
		}
		int x = left();
		int half = Math.max(1, (panel() - 6) / 2);
		for (int i = 0; i < view.actions().size(); i++) {
			var attack = view.actions().get(i).attack();
			addDrawableChild(ButtonWidget.builder(Text.literal((selected == attack ? "• " : "") + attack.title()), button -> {
				selected = attack;
				scroll = 0;
				clearAndInit();
			}).dimensions(x + i * (half + 6), 44, half, 20).build());
		}
		int third = Math.max(1, (panel() - 12) / 3);
		var action = action();
		var use = addDrawableChild(ButtonWidget.builder(Text.literal("Use skill"), button -> send("arpg melee " + input(selected), false))
				.dimensions(x, height - 48, third, 20).build());
		use.active = !waiting && action != null && action.available();
		var tree = addDrawableChild(ButtonWidget.builder(Text.literal(action != null && action.specialized() ? "Open tree" : "Specialize"),
				button -> send("arpg melee tree " + input(selected), true)).dimensions(x + third + 6, height - 48, third, 20).build());
		tree.active = !waiting && action != null;
		var refresh = addDrawableChild(ButtonWidget.builder(Text.literal(waiting ? "Waiting…" : "Refresh"), button -> {
			if (client != null && client.getNetworkHandler() != null) {
				waiting = true;
				waitingTicks = 0;
				feedback = "";
				client.getNetworkHandler().sendChatCommand("arpg melee refresh");
				clearAndInit();
			}
		}).dimensions(x + 2 * (third + 6), height - 48, third, 20).build());
		refresh.active = !waiting;
		addDrawableChild(ButtonWidget.builder(Text.literal("Native controls"), button -> send("arpg melee", true))
				.dimensions(x, height - 24, half, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Character"), button -> SkillsClientMod.getInstance().openArpgScreen())
				.dimensions(x + half + 6, height - 24, half, 20).build());
	}

	private void send(String command, boolean chat) {
		if (client != null && client.getNetworkHandler() != null) {
			client.setScreen(chat ? new ChatScreen("") : null);
			client.getNetworkHandler().sendChatCommand(command);
		}
	}

	@Override
	public void tick() {
		if (waiting && ++waitingTicks >= 200) {
			waiting = false;
			feedback = "No reply yet. Refresh to retry.";
			clearAndInit();
		}
	}

	private int lines(DrawContext context, String text, int y, int color) {
		for (var line : textRenderer.wrapLines(Text.literal(text), Math.max(1, panel() - 16))) {
			context.drawTextWithShadow(textRenderer, line, left() + 8, y, color);
			y += 11;
		}
		return y + 6;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(left() - 4, 4, left() + panel() + 4, height - 2, 0xe018202c);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xfff0dba5);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Snapshot • scroll for details • Refresh updates"), width / 2, 27, 0xffb8c4cf);
		int bottom = Math.max(70, height - 54);
		context.enableScissor(left(), 70, left() + panel(), bottom);
		int y = 74 - scroll;
		y = lines(context, "Weapon: " + view.equipment() + " | " + view.stance(), y, 0xffe4c278);
		y = lines(context, String.format(Locale.ROOT, "Stamina: %.1f / %.1f", view.stamina(), view.maximumStamina()), y, 0xffeeeeee);
		var action = action();
		if (action != null) {
			y = lines(context, selected == MeleeKit.Attack.HEAVY
					? "Measured Strike: commit to your weapon's finishing attack."
					: "Driving Slash: use your weapon's advancing attack.", y, 0xffeeeeee);
			y = lines(context, "Requires level " + action.level() + " | cost: " + (action.cost() < 0 ? "unavailable"
					: String.format(Locale.ROOT, "%.1f stamina", action.cost())) + " | recovery: " + action.recovery() / 20.0 + "s", y, 0xffb8c4cf);
			y = lines(context, action.available() ? "Available at snapshot. Epic Fight rechecks on use."
					: action.rejection().isEmpty() ? "Recover before using this attack." : action.rejection(), y,
					action.available() ? 0xff8ee2a6 : 0xffffb184);
			y = lines(context, action.specialized() ? "Specialization: " + action.experience() + " XP | " + action.points() + " / 20 points earned"
					: "Specialize to choose damage or stamina efficiency. This uses one specialization slot.", y, 0xffb8c4cf);
			y = lines(context, "Accepted activations earn 10 skill XP, including misses. Rejected requests earn none.", y, 0xffb8c4cf);
			y = lines(context, "Bind " + (selected == MeleeKit.Attack.HEAVY ? "ARPG Heavy Strike" : "ARPG Driving Slash") + " in Controls for combat.", y, 0xffe4c278);
		}
		y = lines(context, feedback.isEmpty() ? view.message() : feedback, y, 0xffb8c4cf);
		context.disableScissor();
		maximumScroll = Math.max(0, y + scroll - bottom);
		scroll = Math.min(scroll, maximumScroll);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= left() && mouseX < left() + panel() && mouseY >= 70 && mouseY < height - 54) {
			scroll = Math.max(0, Math.min(maximumScroll, scroll - (int) (verticalAmount * 24)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
