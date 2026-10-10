package net.puffish.skillsmod.arpg.character;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassSelectionViewTest {
	private static final ClassSelectionView.Option WARRIOR = new ClassSelectionView.Option("warrior", "Warrior", 10, 5, 2);

	@Test
	void publishedChoicesAreImmutableAndRejectAmbiguousIds() {
		var options = new ArrayList<>(List.of(WARRIOR));
		var view = new ClassSelectionView("", options);
		options.clear();
		assertEquals(1, view.options().size());
		assertThrows(UnsupportedOperationException.class, () -> view.options().clear());
		assertThrows(IllegalArgumentException.class, () -> new ClassSelectionView("", List.of(WARRIOR, WARRIOR)));
	}

	@Test
	void wireBoundsAndInvalidAttributesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ClassSelectionView("x".repeat(65), List.of()));
		assertThrows(IllegalArgumentException.class, () -> new ClassSelectionView.Option("bad id", "Title", 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new ClassSelectionView.Option("warrior", " ", 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new ClassSelectionView.Option("warrior", "Title", -1, 0, 0));
		var options = new ArrayList<ClassSelectionView.Option>();
		for (int i = 0; i < 33; i++) {
			options.add(new ClassSelectionView.Option("class_" + i, "Class " + i, 0, 0, 0));
		}
		assertThrows(IllegalArgumentException.class, () -> new ClassSelectionView("", options));
	}

	@Test
	void primarySelectionPersistsAndRepeatedRequestsCannotChangeItOrGrantRewards() {
		var character = new ArpgCharacter();
		character.choosePrimary("warrior");
		long experience = character.experience();
		int points = character.passivePoints();
		var restored = ArpgCharacterNbt.read(ArpgCharacterNbt.write(character));
		assertEquals("warrior", restored.primary());
		assertThrows(IllegalStateException.class, () -> restored.choosePrimary("rogue"));
		assertEquals(experience, restored.experience());
		assertEquals(points, restored.passivePoints());
	}
}
