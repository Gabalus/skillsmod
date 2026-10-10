package net.puffish.skillsmod.arpg.sandbox;

/** Data-driven finishing recipe. Ingredient is charged once at session start. */
public record CraftBinding(String operation, String targetItemTag, String stationBlockTag,
		String ingredientItem, int ingredientCount) {
	public CraftBinding {
		SandboxCatalog.checkId(operation);
		SandboxCatalog.checkId(targetItemTag);
		SandboxCatalog.checkId(stationBlockTag);
		SandboxCatalog.checkId(ingredientItem);
		if (ingredientCount < 1 || ingredientCount > 64) {
			throw new IllegalArgumentException("Finishing ingredient count must be 1..64");
		}
	}
}
