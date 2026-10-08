package net.puffish.skillsmod.server.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.combat.CombatPillar;
import net.puffish.skillsmod.arpg.combat.CombatState;
import net.puffish.skillsmod.arpg.combat.CombatStateNbt;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.arpg.sandbox.SandboxState;
import net.puffish.skillsmod.arpg.sandbox.SandboxStateNbt;
import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.character.ArpgCharacterNbt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PlayerData {
	private final Map<Identifier, CategoryData> categories;
	private ArpgCharacter arpg = new ArpgCharacter();
	private SandboxState sandbox = SandboxState.empty();
	private CombatState combat = CombatState.fresh(CombatPillar.MARTIAL);

	private PlayerData(Map<Identifier, CategoryData> categories) {
		this.categories = categories;
	}

	public static PlayerData empty() {
		return new PlayerData(new HashMap<>());
	}

	public static PlayerData read(NbtCompound nbt) {
		var categories = new HashMap<Identifier, CategoryData>();

		var categoriesNbt = nbt.getCompound("categories");
		for (var id : categoriesNbt.getKeys()) {
			var elementNbt = categoriesNbt.get(id);
			if (elementNbt instanceof NbtCompound categoryNbt) {
				categories.put(SkillsMod.convertIdentifier(Identifier.of(id)), CategoryData.read(categoryNbt));
			}
		}

		var result = new PlayerData(categories);
		result.sandbox = SandboxStateNbt.read(nbt.getCompound("sandbox"));
		result.arpg = ArpgCharacterNbt.read(nbt.getCompound("arpg"));
		result.combat = CombatStateNbt.read(
				nbt.getCompound("combat"),
				CombatPillar.forDiscipline(result.arpg.primary())
		);
		return result;
	}

	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.put("sandbox", SandboxStateNbt.write(sandbox));
		nbt.put("arpg", ArpgCharacterNbt.write(arpg));
		nbt.put("combat", CombatStateNbt.write(combat));
		var categoriesNbt = new NbtCompound();
		for (var entry : categories.entrySet()) {
			categoriesNbt.put(
					entry.getKey().toString(),
					entry.getValue().writeNbt(new NbtCompound())
			);
		}
		nbt.put("categories", categoriesNbt);

		return nbt;
	}

	public boolean isCategoryUnlocked(CategoryConfig category) {
		var categoryData = categories.get(category.id());
		if (categoryData != null) {
			return categoryData.isUnlocked();
		}
		return category.general().unlockedByDefault();
	}

	public SandboxState getSandbox() {
		return sandbox;
	}

	public void setSandbox(SandboxState state) {
		sandbox = Objects.requireNonNull(state);
	}

	public ArpgCharacter getArpg() {
		return arpg;
	}

	public CombatState getCombat() {
		return combat;
	}

	public boolean setCombat(CombatState combat) {
		if (combat == null) {
			throw new IllegalArgumentException("Combat state cannot be null");
		}
		if (this.combat.equals(combat)) {
			return false;
		}
		this.combat = combat;
		return true;
	}

	/** Switching pillars always starts the target grammar from its canonical resource profile. */
	public boolean setCombatPillar(CombatPillar pillar) {
		if (pillar == null) {
			throw new IllegalArgumentException("Combat pillar cannot be null");
		}
		if (combat.pillar() == pillar) {
			return false;
		}
		combat = CombatState.fresh(pillar);
		return true;
	}

	public CategoryData getOrCreateCategoryData(CategoryConfig category) {
		return categories.computeIfAbsent(category.id(), key -> CategoryData.create(category.general()));
	}

	public void removeCategoryData(CategoryConfig category) {
		categories.remove(category.id());
	}
}
