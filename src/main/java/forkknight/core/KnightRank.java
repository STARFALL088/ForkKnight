package forkknight.core;

/**
 * The fixed ranks a knight may bear as his title. Fixed on purpose: a
 * profile's title is chosen from this enum, never free text, so the
 * ledger only ever holds words the realm knows.
 */
public enum KnightRank {

    Squire,
    Knight,
    Paladin,
    Champion,
    Warden;

    /** The rank a stored title answers to; unknown words fall back to Squire. */
    public static KnightRank of(String title) {
        for (KnightRank rank : values()) {
            if (rank.name().equalsIgnoreCase(title)) {
                return rank;
            }
        }
        return Squire;
    }
}
