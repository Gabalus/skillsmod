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
 * Keeps allocation/networking in {@link SkillsScreen}, but makes large passive graphs practical to browse.
 */
public final class ArpgHubScreen extends Screen {
	private static final int PANEL_WIDTH = 430;
	private static final int RESULT_HEIGHT = 28;
	private static final int RESULT_GAP = 3;

	private final ClientSkillScreenData data;
	private TextFieldWidget search;
	private List<SearchResult> results = List.of();

	public ArpgHubScreen(ClientSkillScreenData data) {
		super(Text.literal("ARPG Character"));
		this.data = data;
	}

	@Override
	protected void init() {
		int left = Math.max(10, (width - PANEL_WIDTH) / 2);
		int buttonWidth = 80;
		int gap = 5;
		int totalWidth = buttonWidth * 5 + gap * 4;
		int buttonLeft = (width - totalWidth) / 2;

		addQuickButton("Passive", "arpg_universal", false, buttonLeft, 48, buttonWidth);
		addQuickButton("Ascendancy", "arpg_asc_", true, buttonLeft + (buttonWidth + gap), 48, buttonWidth);
		addQuickButton("Confluence", "arpg_confluence_", true, buttonLeft + (buttonWidth + gap) * 2, 48, buttonWidth);
		addQuickButton("Skills", "arpg_skill_", true, buttonLeft + (buttonWidth + gap) * 3, 48, buttonWidth);
		addQuickButton("Atlas", "arpg_atlas", false, buttonLeft + (buttonWidth + gap) * 4, 48, buttonWidth);

		search = new TextFieldWidget(textRenderer, left, 79, Math.min(PANEL_WIDTH, width - 20), 20, Text.literal("Search ARPG tree"));
		search.setPlaceholder(Text.literal("Search nodes, mechanics, stats, ascendancies..."));
		search.setMaxLength(80);
		search.setChangedListener(ignored -> refreshResults());
		addDrawableChild(search);
		setInitialFocus(search);
		refreshResults();
	}

	private void addQuickButton(String label, String path, boolean prefix, int x, int y, int width) {
		var category = findCategory(path, prefix);
		var button = ButtonWidget.builder(Text.literal(label), ignored -> findCategory(path, prefix).ifPresent(categoryData -> open(categoryData, Optional.empty())))
				.dimensions(x, y, width, 20)
				.build();
		button.active = category.isPresent();
		addDrawableChild(button);
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
			String categoryHaystack = (categoryTitle + " " + categoryDescription + " " + config.id().getPath()).toLowerCase(Locale.ROOT);

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
	}

	private void open(ClientCategoryData category, Optional<String> skillId) {
		skillId.ifPresent(id -> {
			var skill = category.getConfig().skills().get(id);
			if (skill != null) {
				// SkillsScreen translates the graph around its screen center, so negative node coordinates focus it.
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
			int left = Math.max(10, (width - PANEL_WIDTH) / 2);
			int top = 110;
			int visible = visibleRows();
			for (int i = 0; i < Math.min(visible, results.size()); i++) {
				int y = top + i * (RESULT_HEIGHT + RESULT_GAP);
				if (mouseX >= left && mouseX < Math.min(width - 10, left + PANEL_WIDTH)
						&& mouseY >= y && mouseY < y + RESULT_HEIGHT) {
					var result = results.get(i);
					open(result.category(), result.skillId());
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
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
		int left = Math.max(10, (width - PANEL_WIDTH) / 2);
		int right = Math.min(width - 10, left + PANEL_WIDTH);

		context.fill(left - 8, 14, right + 8, height - 18, 0xc9101016);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xfff2d48a);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("Passive tree • Ascendancies • Confluence • Skill specializations • Atlas"),
				width / 2, 34, 0xffa8a8a8);

		int top = 110;
		int visible = visibleRows();
		for (int i = 0; i < Math.min(visible, results.size()); i++) {
			var result = results.get(i);
			int y = top + i * (RESULT_HEIGHT + RESULT_GAP);
			boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + RESULT_HEIGHT;
			context.fill(left, y, right, y + RESULT_HEIGHT, hovered ? 0xff3b3428 : 0xff211f1d);
			context.fill(left, y, left + 2, y + RESULT_HEIGHT, result.skillId().isPresent() ? 0xffd6a64a : 0xff6e7780);

			String category = result.category().getConfig().title().getString();
			String titleText = result.skillId().isPresent() ? result.title() : category;
			context.drawTextWithShadow(textRenderer, Text.literal(shorten(titleText, 48)), left + 8, y + 4, 0xfff0f0f0);
			String detail = result.skillId().isPresent()
					? category + " • " + result.description()
					: result.description();
			context.drawTextWithShadow(textRenderer, Text.literal(shorten(detail, 62)), left + 8, y + 15, 0xff909090);
		}

		if (results.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal("No ARPG nodes match this search."), width / 2, 122, 0xffb0b0b0);
		} else if (results.size() > visible) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.literal("Showing " + visible + " of " + results.size() + " matches — refine the search"),
					width / 2, height - 43, 0xff888888);
		}

		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("P: close hub  •  K: classic categories  •  tree: drag to pan, wheel to zoom"),
				width / 2, height - 30, 0xff777777);
		super.render(context, mouseX, mouseY, delta);
	}

	private int visibleRows() {
		return Math.max(1, Math.min(9, (height - 165) / (RESULT_HEIGHT + RESULT_GAP)));
	}

	private static String shorten(String value, int length) {
		if (value.length() <= length) {
			return value;
		}
		return value.substring(0, Math.max(1, length - 1)) + "…";
	}

	private record SearchResult(
			ClientCategoryData category,
			Optional<String> skillId,
			String title,
			String description
	) { }
}
