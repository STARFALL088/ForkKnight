package forkknight.core;

/**
 * A sigil placed on a feat so it can be referenced forever - what other
 * tools call an annotated or lightweight "tag".
 */
public record Sigil(String name, Feat feat) {

    @Override
    public String toString() {
        return name;
    }
}
