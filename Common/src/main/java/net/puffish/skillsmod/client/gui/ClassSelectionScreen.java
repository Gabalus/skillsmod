package net.puffish.skillsmod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.character.ClassSelectionView;
import net.puffish.skillsmod.client.SkillsClientMod;
import net.puffish.skillsmod.client.network.packets.out.ChooseClassOutPacket;

/** Select, review and confirm a primary class; the server owns the resulting choice. */
public final class ClassSelectionScreen extends Screen {
	private ClassSelectionView view;
	private String selected = "";
	private String message = "";
	private int page;
	private int waitingTicks;
	private boolean waiting;
	private ButtonWidget confirm;

	public ClassSelectionScreen(ClassSelectionView view) {
		super(Text.literal("Choose your class"));
		this.view = view;
	}

	public void update(ClassSelectionView view, String message) {
		this.view = view;
		this.message = message;
		waiting = false;
		if (view.options().stream().noneMatch(option -> option.id().equals(selected))) {
			selected = "";
		}
		clearAndInit();
	}

	private int columns() {
		return width >= 360 ? 2 : 1;
	}

	private int rows() {
		return Math.max(1, Math.min(3, (height - 190) / 32));
	}

	private int pageSize() {
		return columns() * rows();
	}

	@Override
	protected void init() {
		int panel = Math.max(120, Math.min(580, width - 24));
		int left = (width - panel) / 2;
		int pages = Math.max(1, (view.options().size() + pageSize() - 1) / pageSize());
		page = Math.min(page, pages - 1);
		int card = (panel - (columns() - 1) * 8) / columns();
		for (int i = 0; i < pageSize() && page * pageSize() + i < view.options().size(); i++) {
			var option = view.options().get(page * pageSize() + i);
			addDrawableChild(ButtonWidget.builder(Text.literal((selected.equals(option.id()) ? "✓ " : "") + option.title()),
					button -> {
						selected = option.id();
						message = "";
						clearAndInit();
					}).dimensions(left + (i % columns()) * (card + 8), 60 + (i / columns()) * 32, card, 26).build()).active = !waiting;
		}
		if (pages > 1) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> {
				page--;
				clearAndInit();
			}).dimensions(left, height - 62, 80, 20).build()).active = page > 0 && !waiting;
			addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> {
				page++;
				clearAndInit();
			}).dimensions(left + panel - 80, height - 62, 80, 20).build()).active = page + 1 < pages && !waiting;
		}
		confirm = addDrawableChild(ButtonWidget.builder(Text.literal(waiting ? "Waiting for server…" : "Confirm class"), button -> {
			waiting = true;
			waitingTicks = 0;
			SkillsClientMod.getInstance().getPacketSender().send(new ChooseClassOutPacket(selected));
			clearAndInit();
		}).dimensions(left, height - 30, Math.max(80, (panel - 8) / 2), 20).build());
		confirm.active = !waiting && !selected.isEmpty();
		addDrawableChild(ButtonWidget.builder(Text.literal("Choose later"), button -> close())
				.dimensions(left + (panel + 8) / 2, height - 30, Math.max(80, (panel - 8) / 2), 20).build());
	}

	@Override
	public void tick() {
		if (waiting && ++waitingTicks >= 200) {
			waiting = false;
			message = "No reply yet. You can retry; the server will confirm the saved choice.";
			clearAndInit();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xfff0dba5);
		int subtitleY = 36;
		for (var line : textRenderer.wrapLines(Text.literal("Your starting identity — combine classes as you progress"), width - 24)) {
			context.drawCenteredTextWithShadow(textRenderer, line, width / 2, subtitleY, 0xffb8c4cf);
			subtitleY += 11;
		}
		int panel = Math.max(120, Math.min(580, width - 24));
		int left = (width - panel) / 2;
		int y = 64 + rows() * 32;
		var option = view.options().stream().filter(value -> value.id().equals(selected)).findFirst();
		if (option.isPresent()) {
			var value = option.get();
			context.drawTextWithShadow(textRenderer, Text.literal(value.title() + " | STR " + value.strength()
					+ " • DEX " + value.dexterity() + " • INT " + value.intelligence()), left, y, 0xffeeeeee);
			y += 16;
			String description = switch (value.id()) {
				case "warrior" -> "Melee pressure, armor and weapon mastery.";
				case "rogue" -> "Melee precision, critical strikes and fast attacks.";
				case "templar" -> "Shield combat, holy power and durable hybrid paths.";
				case "ranger" -> "Ranged weapons, projectiles and evasive positioning.";
				case "arcanist" -> "Prepared spells, elemental power and spell specialization.";
				case "shaman" -> "Nature, blood and cold magic paths.";
				default -> "Build a playstyle through passives, equipment and skill specializations.";
			};
			for (var line : textRenderer.wrapLines(Text.literal(description), panel)) {
				context.drawTextWithShadow(textRenderer, line, left, y, 0xffb8c4cf);
				y += 11;
			}
		} else {
			context.drawTextWithShadow(textRenderer, Text.literal(view.options().isEmpty() ? "No classes are configured." : "Select a class to review its starting attributes."), left, y, 0xffb8c4cf);
		}
		String note = message.isEmpty() ? "Primary choice is permanent. A second class unlocks at level 20." : message;
		int lineY = height - 94;
		for (var line : textRenderer.wrapLines(Text.literal(note), panel)) {
			context.drawTextWithShadow(textRenderer, line, left, lineY, message.isEmpty() ? 0xffe4c278 : 0xffff8888);
			lineY += 11;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
