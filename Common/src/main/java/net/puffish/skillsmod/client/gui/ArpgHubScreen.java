package net.puffish.skillsmod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.client.SkillsClientMod;
import net.puffish.skillsmod.client.data.ClientCategoryData;
import net.puffish.skillsmod.client.data.ClientSkillScreenData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * ARPG-first navigation layer over the normal Puffish Skills graph screen.
 * Allocation/networking stays in {@link SkillsScreen}; this screen is only navigation and search.
 */
public final class ArpgHubScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 520;
	private static final int MIN_PANEL_WIDTH = 220;
	private static final int QUICK_BUTTON_HEIGHT = 20;
	private static final int QUICK_BUTTON_GAP = 4;
	private static final int RESULT_HEIGHT = 28;
	private static final int RESULT_GAP = 3;
	private static final int RESULT_SCROLL_STEP = 3;

	private static final List<QuickLink> QUICK_LINKS = List.of(
			new QuickLink("Passive", "arpg_universal", false),
			new QuickLink("Ascendancy", "arpg_asc_", true),
			new QuickLink("Confluence", "arpg_confluence_", true),
			new QuickLink("Skills", "arpg_skill_", true),
			new QuickLink("Atlas", "arpg_atlas", false)
	);

	private final ClientSkillScreenData data;
	private TextFieldWidget search;
	private List<SearchResult> results = List.of();
	private int resultOffset = 0;

	public ArpgHubScreen(ClientSkillScreenData data) {
		super(Text.literal("ARPG Character"));
		this.data = data;
	}

	@Override
	protected void init() {
		var layout = layout();
		for (int i = 0; i < QUICK_LINKS.size(); i++) {
			var link = QUICK_LINKS.get(i);
			int row = i / layout.columns();
			int column = i % layout.columns();
			int x = layout.left() + column * (layout.buttonWidth() + QUICK_BUTTON_GAP);
			int y = layout.quickTop() + row * (QUICK_BUTTON_HEIGHT + QUICK_BUTTON_GAP);
			int width = column == layout.columns() - 1
					? layout.right() - x
					: layout.buttonWidth();
			addQuickButton(link, x, y, width);
		}

		search = new TextFieldWidget(
				textRenderer,
				layout.left(),
				layout.searchTop(),
				layout.panelWidth(),
				20,
				Text.literal("Search ARPG tree")
		);
		search.setPlaceholder(Text.literal("Search nodes, mechanics, stats, ascendancies..."));
		search.setMaxLength(80);
		search.setChangedListener(ignored -> {
			resultOffset = 0;
			refreshResults();
		});
		addDrawableChild(search);
		setInitialFocus(search);
		refreshResults();
	}

	private void addQuickButton(QuickLink link, int x, int y, int width) {
		var category = findCategory(link.path(), link.prefix());
		var button = ButtonWidget.builder(
				Text.literal(link.label()),
				ignored -> findCategory(link.path(), link.prefix())
						.ifPresent(categoryData -> open(categoryData, Optional.empty()))
		)
				.dimensions(x, y, Math.max(34, width), QUICK_BUTTON_HEIGHT)
				.build();
		button.active = category.isPresent();
		addDrawableChild(button);
	}

	private Layout layout() {
		int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, width - 24));
		panelWidth = Math.min(panelWidth, Math.max(1, width - 8));
		int left = Math.max(4, (width - panelWidth) / 2);
		int right = Math.min(width - 4, left + panelWidth);
		panelWidth = Math.max(1, right - left);

		int columns;
		if (panelWidth >= 470) {
			columns = 5;
		} else if (panelWidth >= 330) {
			columns = 3;
		} else {
			columns = 2;
		}
		int rows = (QUICK_LINKS.size() + columns - 1) / columns;
		int buttonWidth = Math.max(34, (panelWidth - QUICK_BUTTON_GAP * (columns - 1)) / columns);
		int quickTop = 49;
		int searchTop = quickTop + rows * (QUICK_BUTTON_HEIGHT + QUICK_BUTTON_GAP) + 5;
		int resultsTop = searchTop + 28;
		int footerY = Math.max(resultsTop + 8, height - 28);
		return new Layout(left, right, panelWidth, columns, buttonWidth, quickTop, searchTop, resultsTop, footerY);
	}

	private Optional<ClientCategoryData> findCategory(String path, boolean prefix) {
		return arpgCategories()
				.filter(category -> {
					var id = category.getConfig().id();
					return prefix ? id.getPath().startsWith(path) : id.getPath().equals(path);
				})
				.max(Comparator.comparing(ClientCategoryData::getLastOpen));
	}

	private java.util.stream.Stream<ClientCategoryData> arpgCategories() {
		return data.streamCategories().filter(category -> {
			Identifier id = category.getConfig().id();
			return id.getNamespace().equals("puffish_skills")
					&& id.getPath().startsWith("arpg_")
					&& !id.getPath().equals("arpg_test");
		});
	}

	private void refreshResults() {
		if (search == null) {
			return;
		}
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		var matches = new ArrayList<SearchResult>();

		for (var category : (Iterable<ClientCategoryData>) arpgCategories()::iterator) {
			var config = category.getConfig();
			String categoryTitle = config.title().getString();
			String categoryDescription = config.description().getString();
			String categoryHaystack = (categoryTitle + " " + categoryDescription + " " + config.id().getPath())
					.toLowerCase(Locale.ROOT);

			if (query.isEmpty() || categoryHaystack.contains(query)) {
				matches.add(new SearchResult(category, Optional.empty(), categoryTitle, categoryDescription));
			}

			if (!query.isEmpty()) {
				for (var skill : config.skills().values()) {
					var definition = config.definitions().get(skill.definitionId());
					if (definition == null) {
						continue;
					}
					String title = definition.title().getString();
					String description = definition.description().getString();
					String haystack = (title + " " + description + " " + skill.id()).toLowerCase(Locale.ROOT);
					if (haystack.contains(query)) {
						matches.add(new SearchResult(category, Optional.of(skill.id()), title, description));
					}
				}
			}
		}

		matches.sort(Comparator
				.comparing((SearchResult result) -> result.skillId().isEmpty() ? 1 : 0)
				.thenComparing(SearchResult::title));
		results = List.copyOf(matches);
		clampResultOffset();
	}

	private void clampResultOffset() {
		int maxOffset = Math.max(0, results.size() - visibleRows());
		resultOffset = Math.max(0, Math.min(resultOffset, maxOffset));
	}

	private void open(ClientCategoryData category, Optional<String> skillId) {
		skillId.ifPresent(id -> {
			var skill = category.getConfig().skills().get(id);
			if (skill != null) {
				category.setX(-skill.x());
				category.setY(-skill.y());
				category.setScale(1.0f);
			}
		});
		if (client != null) {
			client.setScreen(new SkillsScreen(data, Optional.of(category.getConfig().id())));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			var layout = layout();
			int rows = visibleRows();
			for (int row = 0; row < rows; row++) {
				int index = resultOffset + row;
				if (index >= results.size()) {
					break;
				}
				int y = layout.resultsTop() + row * (RESULT_HEIGHT + RESULT_GAP);
				if (mouseX >= layout.left() && mouseX < layout.right()
						&& mouseY >= y && mouseY < y + RESULT_HEIGHT) {
					var result = results.get(index);
					open(result.category(), result.skillId());
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		var layout = layout();
		if (mouseX >= layout.left() && mouseX < layout.right()
				&& mouseY >= layout.resultsTop() && mouseY < layout.footerY()) {
			if (verticalAmount > 0) {
				resultOffset -= RESULT_SCROLL_STEP;
			} else if (verticalAmount < 0) {
				resultOffset += RESULT_SCROLL_STEP;
			}
			clampResultOffset();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (SkillsClientMod.ARPG_KEY_BINDING.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		if (SkillsClientMod.OPEN_KEY_BINDING.matchesKey(keyCode, scanCode)) {
			SkillsClientMod.getInstance().openScreen(Optional.empty());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		var layout = layout();

		context.fill(layout.left() - 6, 12, layout.right() + 6, Math.max(13, height - 12), 0xc9101016);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xfff2d48a);
		context.drawCenteredTextWithShadow(
				textRenderer,
				Text.literal("Passive tree • Ascendancies • Confluence • Skills • Atlas"),
				width / 2,
				32,
				0xffa8a8a8
		);

		int visible = visibleRows();
		for (int row = 0; row < visible; row++) {
			int index = resultOffset + row;
			if (index >= results.size()) {
				break;
			}
			var result = results.get(index);
			int y = layout.resultsTop() + row * (RESULT_HEIGHT + RESULT_GAP);
			boolean hovered = mouseX >= layout.left() && mouseX < layout.right()
					&& mouseY >= y && mouseY < y + RESULT_HEIGHT;
			context.fill(layout.left(), y, layout.right(), y + RESULT_HEIGHT, hovered ? 0xff3b3428 : 0xff211f1d);
			context.fill(
					layout.left(), y, layout.left() + 2, y + RESULT_HEIGHT,
					result.skillId().isPresent() ? 0xffd6a64a : 0xff6e7780
			);

			String category = result.category().getConfig().title().getString();
			String titleText = result.skillId().isPresent() ? result.title() : category;
			context.drawTextWithShadow(
					textRenderer,
					Text.literal(shorten(titleText, Math.max(22, layout.panelWidth() / 8))),
					layout.left() + 8,
					y + 4,
					0xfff0f0f0
			);
			String detail = result.skillId().isPresent()
					? category + " • " + result.description()
					: result.description();
			context.drawTextWithShadow(
					textRenderer,
					Text.literal(shorten(detail, Math.max(26, layout.panelWidth() / 6))),
					layout.left() + 8,
					y + 15,
					0xff909090
			);
		}

		if (results.isEmpty()) {
			context.drawCenteredTextWithShadow(
					textRenderer,
					Text.literal("No ARPG nodes match this search."),
					width / 2,
					layout.resultsTop() + 8,
					0xffb0b0b0
			);
		} else if (results.size() > visible) {
			int from = Math.min(results.size(), resultOffset + 1);
			int to = Math.min(results.size(), resultOffset + visible);
			context.drawCenteredTextWithShadow(
					textRenderer,
					Text.literal(from + "–" + to + " / " + results.size() + " • mouse wheel to scroll"),
					width / 2,
					layout.footerY() - 11,
					0xff888888
			);
		}

		context.drawCenteredTextWithShadow(
				textRenderer,
				Text.literal("P: close • K: classic categories • tree: drag to pan, wheel to zoom"),
				width / 2,
				layout.footerY(),
				0xff777777
		);
		super.render(context, mouseX, mouseY, delta);
	}

	private int visibleRows() {
		var layout = layout();
		int available = layout.footerY() - 15 - layout.resultsTop();
		return Math.max(0, Math.min(10, available / (RESULT_HEIGHT + RESULT_GAP)));
	}

	private static String shorten(String value, int length) {
		if (value.length() <= length) {
			return value;
		}
		return value.substring(0, Math.max(1, length - 1)) + "…";
	}

	private record QuickLink(String label, String path, boolean prefix) { }

	private record Layout(
			int left,
			int right,
			int panelWidth,
			int columns,
			int buttonWidth,
			int quickTop,
			int searchTop,
			int resultsTop,
			int footerY
	) { }

	private record SearchResult(
			ClientCategoryData category,
			Optional<String> skillId,
			String title,
			String description
	) { }
}
