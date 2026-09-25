package forkknight;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Every keyboard shortcut ForkKnight ships, in one immutable place.
 *
 * <p><b>Requirement R1: shortcuts are fixed and must never become
 * editable.</b> There is no rebinding surface in the application and none
 * may be added - the menus show these accelerators as a read-only reminder
 * of what the keys already do. A shortcut that is wrong changes here, in
 * the source, and the test that pins this catalogue changes with it;
 * nothing may rewrite a binding at runtime.
 *
 * <p>The enum is the guarantee: instances are built once at class
 * initialization, every field is final, {@link KeyCodeCombination} is
 * immutable, and the type offers no setter of any kind.
 */
public enum Shortcut {

    /** Realm - Seek a realm to serve. */
    SEEK(KeyCode.O, KeyCombination.CONTROL_DOWN),
    /** Realm - Muster the field (working changes). */
    MUSTER(KeyCode.R, KeyCombination.CONTROL_DOWN),
    /** Realm - Seal the vanguard (commit). */
    SEAL(KeyCode.N, KeyCombination.CONTROL_DOWN),
    /** Realm - Rally the allies (fetch). */
    RALLY(KeyCode.F5),
    /** Realm - Summon the council (repository stats). */
    COUNCIL(KeyCode.I, KeyCombination.CONTROL_DOWN),
    /** Realm - Banners roll (ahead/behind of every banner). */
    ROLL(KeyCode.B, KeyCombination.CONTROL_DOWN),
    /** Realm - Depart the app. */
    DEPART(KeyCode.Q, KeyCombination.CONTROL_DOWN),
    /** Sight - Day sight (light theme). */
    DAY_SIGHT(KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
    /** Sight - Peer into the scryer (search). */
    SCRY(KeyCode.F, KeyCombination.CONTROL_DOWN);

    private final KeyCodeCombination keys;

    Shortcut(KeyCode key, KeyCombination.Modifier... modifiers) {
        this.keys = new KeyCodeCombination(key, modifiers);
    }

    /** The binding this shortcut always has, on every launch. */
    public KeyCodeCombination keys() {
        return keys;
    }
}
