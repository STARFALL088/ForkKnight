package forkknight;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the shipped shortcuts (requirement R1). Changing a binding means
 * changing this test as well - that is the point: a shortcut never
 * changes on its own, and certainly not from a settings screen.
 */
class ShortcutTest {

    @Test
    void everyShortcutIsPinnedToItsExactKeys() {
        assertEquals(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), Shortcut.SEEK.keys());
        assertEquals(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN), Shortcut.MUSTER.keys());
        assertEquals(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), Shortcut.SEAL.keys());
        assertEquals(new KeyCodeCombination(KeyCode.F5), Shortcut.RALLY.keys());
        assertEquals(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN), Shortcut.COUNCIL.keys());
        assertEquals(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN), Shortcut.ROLL.keys());
        assertEquals(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN), Shortcut.DEPART.keys());
        assertEquals(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                Shortcut.DAY_SIGHT.keys());
        assertEquals(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), Shortcut.SCRY.keys());
        assertEquals(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN), Shortcut.PROFILE.keys());
    }

    @Test
    void theCatalogueIsExactlyTheseAndNoOthers() {
        Set<String> names = new HashSet<>(Arrays.asList(
                "SEEK", "MUSTER", "SEAL", "RALLY", "COUNCIL", "ROLL", "DEPART", "DAY_SIGHT", "SCRY",
                "PROFILE"));
        Set<String> actual = new HashSet<>();
        for (Shortcut shortcut : Shortcut.values()) {
            actual.add(shortcut.name());
        }
        assertEquals(names, actual);
    }

    @Test
    void noTwoShortcutsClaimTheSameKeys() {
        Shortcut[] all = Shortcut.values();
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                assertNotEquals(all[i].keys(), all[j].keys(),
                    all[i] + " and " + all[j] + " fight over the same keys");
            }
        }
    }

    @Test
    void theCatalogueCannotBeEditedAtRuntime() {
        // Every field of the enum is final: nothing can be swapped after init.
        for (Field field : Shortcut.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()),
                field.getName() + " must be final - a shortcut may not be rewritten at runtime");
        }
        // And the type exposes no mutator to rewrite a binding with.
        for (Method method : Shortcut.class.getDeclaredMethods()) {
            if (method.getParameterCount() > 0 && method.getName().startsWith("set")) {
                fail("Shortcut." + method.getName() + " looks like a rebinding surface - R1 forbids it");
            }
        }
        assertTrue(Modifier.isFinal(Shortcut.class.getModifiers()) || Shortcut.class.isEnum(),
            "the catalogue type must stay an enum");
    }
}
