package forkknight.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The far call: an HTTP request across the wider realm (a GitHub-style
 * REST endpoint) whose JSON answer is read by {@link Json}.
 *
 * <p>The transport hides behind {@link HttpGateway}, so everything past
 * the wire - names, URLs, shapes, first lines of commit words - is proved
 * with a canned gateway in the tests, while the JDK gateway itself is
 * proved against a local endpoint.
 */
public final class Beacon {

    /** The wider realm's well-known answering point. */
    private static final String ENDPOINT = "https://api.github.com";

    /** How many of the remote realm's newest feats to ask for. */
    private static final int NEWEST = 10;

    /** Owner and realm names, so nothing odd can ride into the URL. */
    private static final Pattern NAME =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    private final HttpGateway gateway;

    public Beacon(HttpGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway,
            "a beacon needs a gateway");
    }

    /** What the wider realm answers about one remote realm. */
    public FarReport report(String owner, String realm)
            throws IOException, InterruptedException {
        String body = gateway.get(ENDPOINT + "/repos/" + path(owner, realm));
        Map<String, Object> object = Json.object(Json.parse(body));
        return new FarReport(
            text(object, "full_name", owner + "/" + realm),
            text(object, "description", null),
            number(object, "stargazers_count"),
            number(object, "forks_count"),
            number(object, "open_issues_count"),
            text(object, "default_branch", null),
            text(object, "pushed_at", null));
    }

    /** The newest feats the remote realm has sealed. */
    public List<FarFeat> newestFeats(String owner, String realm)
            throws IOException, InterruptedException {
        String body = gateway.get(ENDPOINT + "/repos/" + path(owner, realm)
            + "/commits?per_page=" + NEWEST);
        List<Object> entries = Json.array(Json.parse(body));
        List<FarFeat> feats = new ArrayList<>();
        for (Object entry : entries) {
            feats.add(readFeat(Json.object(entry)));
        }
        return feats;
    }

    private static FarFeat readFeat(Map<String, Object> commit) {
        String sha = text(commit, "sha", "");
        Map<String, Object> words = child(commit, "commit");
        String message = text(words, "message", "");
        int firstLine = message.indexOf('\n');
        if (firstLine >= 0) {
            message = message.substring(0, firstLine);
        }
        Map<String, Object> author = child(words, "author");
        return new FarFeat(sha, message, text(author, "name", "unknown"),
            text(author, "date", null));
    }

    private static String path(String owner, String realm) {
        if (owner == null || !NAME.matcher(owner).matches()) {
            throw new IllegalArgumentException(
                "'" + owner + "' is no holder's name");
        }
        if (realm == null || !NAME.matcher(realm).matches()) {
            throw new IllegalArgumentException(
                "'" + realm + "' is no realm's name");
        }
        return owner + "/" + realm;
    }

    private static String text(Map<String, Object> object, String key,
            String fallback) {
        String value = Json.string(object.get(key));
        return value == null ? fallback : value;
    }

    private static long number(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value == null ? 0L : Json.integer(value);
    }

    private static Map<String, Object> child(Map<String, Object> parent,
            String key) {
        Object value = parent.get(key);
        return value instanceof Map ? Json.object(value) : Map.of();
    }

    /** The wider realm's report on one remote realm. */
    public record FarReport(String fullName, String tale, long stars,
            long forks, long openIssues, String banner, String lastSealed) {
    }

    /** One feat the remote realm has sealed. */
    public record FarFeat(String sha, String message, String hero,
            String day) {
    }
}
