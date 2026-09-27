package forkknight.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The real far call: JDK {@code java.net.http.HttpClient}, no extra
 * dependency. Short connect and read timeouts keep a dead network from
 * hanging the knight, and a User-Agent rides along because some far
 * realms refuse the nameless.
 */
public final class JdkHttpGateway implements HttpGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(READ_TIMEOUT)
            .header("Accept", "application/json")
            .header("User-Agent", "ForkKnight")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new IOException("the wider realm answered " + status + " for " + url);
        }
        return response.body();
    }
}
