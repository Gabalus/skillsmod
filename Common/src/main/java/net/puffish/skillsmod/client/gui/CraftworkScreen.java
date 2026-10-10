package net.puffish.skillsmod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.sandbox.CraftworkView;
import java.util.List;

/** Live crafting feedback; buttons submit commands, never quality or inventory mutations. */
public final class CraftworkScreen extends Screen {
	private CraftworkView view;
	private String message;
	private String selected = "";
	private long receivedTick;
	private int refreshTicks;
	private boolean pending;

	public CraftworkScreen(CraftworkView view, String message) {
		super(Text.literal("Craftwork"));
		this.view = view;
		this.message = message;
	}

	public void update(CraftworkView view, String message) {
		this.view = view;
		if (!message.isEmpty()) {
			this.message = message;
		}
		this.pending = false;
		this.receivedTick = client == null || client.world == null ? 0 : client.world.getTime();
		clearChildren();
		init();
	}

	private int left() {
		return Math.max(4, (width - panelWidth()) / 2);
	}

	private int top() {
		return Math.max(4, (height - 266) / 2);
	}

	private int panelWidth() {
		return Math.min(408, width - 8);
	}

	@Override
	protected void init() {
		if (client != null && client.world != null) {
			receivedTick = client.world.getTime();
		}
		var recipes = recipes();
		if (!recipes.contains(selected)) {
			selected = recipes.isEmpty() ? "" : recipes.get(0);
		}
		int x = left() + 12;
		int y = top();
		int inner = panelWidth() - 24;
		var study = button("Study recipes", x + inner - 90, y + 8, 90, () -> send("craftwork study"));
		study.active = !pending;
		button("<", x, y + 46, 24, () -> cycle(-1));
		button(">", x + inner - 24, y + 46, 24, () -> cycle(1));
		var start = button("Start " + shortId(selected), x + 28, y + 46, inner - 56,
				() -> send("craftwork start " + selected));
		start.active = view.operation().isEmpty() && view.recipes().getOrDefault(selected, false) && !pending;
		int columns = inner >= 220 ? 4 : 3;
		int rowsAvailable = Math.max(1, (height - 28 - (y + 150)) / 24);
		int count = Math.min(Math.min(8, view.actions().size()), rowsAvailable * columns);
		int buttonWidth = (inner - (columns - 1) * 4) / columns;
		for (int i = 0; i < count; i++) {
			String action = view.actions().get(i);
			var actionButton = button(action, x + (i % columns) * (buttonWidth + 4), y + 150 + (i / columns) * 24,
					buttonWidth, () -> send("craftwork act " + view.step() + " " + action));
			actionButton.active = !view.complete() && !view.failed() && !pending;
		}
		int footer = Math.min(height - 28, Math.max(y + 220, y + 150 + ((count + columns - 1) / columns) * 24));
		var finish = button("Finish", x, footer, (inner - 8) / 3, () -> send("craftwork finish"));
		// Finish also supports recovery of a saved receipt on a pending item.
		finish.active = (view.complete() || view.operation().isEmpty()) && !pending;
		var cancel = button("Cancel", x + (inner - 8) / 3 + 4, footer, (inner - 8) / 3, () -> send("craftwork cancel"));
		cancel.active = !pending;
		button("Close", x + 2 * ((inner - 8) / 3 + 4), footer, (inner - 8) / 3, this::close);
	}

	private ButtonWidget button(String title, int x, int y, int width, Runnable action) {
		return addDrawableChild(ButtonWidget.builder(Text.literal(title), ignored -> action.run()).dimensions(x, y, width, 20).build());
	}

	private List<String> recipes() {
		return view.recipes().keySet().stream().sorted().toList();
	}

	private void cycle(int offset) {
		var recipes = recipes();
		if (!recipes.isEmpty()) {
			selected = recipes.get(Math.floorMod(recipes.indexOf(selected) + offset, recipes.size()));
			clearChildren();
			init();
		}
	}

	private void send(String command) {
		if (client != null && client.player != null && !pending) {
			pending = true;
			message = "";
			client.player.networkHandler.sendChatCommand(command);
			clearChildren();
			init();
		}
	}

	@Override
	public void tick() {
		if (client == null || client.player == null) {
			return;
		}
		if (++refreshTicks >= 10) {
			refreshTicks = 0;
			client.player.networkHandler.sendChatCommand("craftwork refresh");
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static String shortId(String id) {
		return id.isEmpty() ? "recipe" : id.substring(id.indexOf(':') + 1).replace('_', ' ');
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int x = left();
		int y = top();
		int inner = panelWidth() - 24;
		context.fill(x, y, x + panelWidth(), y + Math.min(266, height - 8), 0xEE151B25);
		context.drawTextWithShadow(textRenderer, "CRAFTWORK", x + 12, y + 12, 0xE8C685);
		context.drawTextWithShadow(textRenderer, "Smithing " + view.smithing() + "  |  Runecraft " + view.runecraft(), x + 12, y + 28, 0xC0C8D4);
		String status = view.operation().isEmpty() ? (view.recipes().getOrDefault(selected, false) ? "Knowledge unlocked" : "Knowledge or mastery required")
				: shortId(view.operation()) + "  " + view.step() + "/" + view.total() + "  next: " + view.expected();
		context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, inner), x + 12, y + 74, 0xFFFFFF);
		if ("arpg:thermal_forge".equals(view.mechanic())) {
			long now = client == null || client.world == null ? receivedTick : client.world.getTime();
			int temperature = view.complete() ? 0 : (int) Math.max(0, view.temperature() - Math.max(0, now - receivedTick));
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Heat " + temperature + "  |  Draw 875 / Heavy 775 / Quench 600", inner), x + 12, y + 96, 0xF3AB72);
			context.fill(x + 12, y + 112, x + 12 + inner, y + 120, 0xFF343E4C);
			context.fill(x + 12, y + 112, x + 12 + inner * Math.min(1000, temperature) / 1000, y + 120, 0xFFD57843);
		} else if ("arpg:rune_route".equals(view.mechanic())) {
			context.drawTextWithShadow(textRenderer, "Rune route: (" + view.x() + ", " + view.y() + ")  |  Seal at (0, 0)", x + 12, y + 96, 0xA6C6FF);
		} else {
			context.drawTextWithShadow(textRenderer, view.recipeCosts().getOrDefault(selected, "Hold your item near its crafting station"), x + 12, y + 96, 0xA6B4C7);
		}
		context.drawTextWithShadow(textRenderer, "Mistakes " + view.mistakes() + "  |  " + (view.failed() ? "FAILED — cancel" : "Quality is evaluated by the server"), x + 12, y + 132, 0xD2D9E4);
		context.drawTextWithShadow(textRenderer, "Held item: Forge " + view.forgeQuality() + " / Rune " + view.runeQuality(), x + 12, Math.min(y + 202, height - 42), 0xC0C8D4);
		if (!message.isEmpty()) {
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, inner), x + 12, y + 86, 0xFF8A80);
		}
		super.render(context, mouseX, mouseY, delta);
	}
}
