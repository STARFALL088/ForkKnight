package forkknight.core;

/**
 * A dispatch on a campaign front - one of the roads a knight may travel.
 * Other tools call this a "branch"; here every branch is a banner to rally
 * under.
 */
public record Banner(String name, boolean active, String tipHash) {

    @Override
    public String toString() {
        return name;
    }
}
