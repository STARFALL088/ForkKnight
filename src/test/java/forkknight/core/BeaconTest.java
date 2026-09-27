package forkknight.core;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeaconTest {

    /** Answers with canned bodies and remembers every URL asked for. */
    private static final class StubGateway implements HttpGateway {
        final List<String> asked = new ArrayList<>();
        private final String[] answers;
        private int next;

        StubGateway(String... answers) {
            this.answers = answers;
        }

        @Override
        public String get(String url) {
            asked.add(url);
            if (next >= answers.length) {
                throw new AssertionError("asked twice: " + url);
            }
            return answers[next++];
        }
    }

    private static final String REPO_JSON = """
        {
          "full_name": "STARFALL088/ForkKnight",
          "description": "A knightly Git client",
          "stargazers_count": 7,
          "forks_count": 2,
          "open_issues_count": 3,
          "default_branch": "main",
          "pushed_at": "2026-09-25T17:52:27Z"
        }
        """;

    private static final String COMMITS_JSON = """
        [
          {
            "sha": "6005f5c0000000000000000000000000000000aa",
            "commit": {
              "message": "first line kept\\nbody line dropped",
              "author": {"name": "STARFALL088", "date": "2026-09-25T17:52:27Z"}
            }
          },
          {
            "sha": "1a1701e0000000000000000000000000000000bb",
            "commit": {
              "message": "single line feat",
              "author": {"name": "keeper", "date": "2026-09-25T16:00:00Z"}
            }
          }
        ]
        """;

    @Test
    void reportReadsEveryFieldOfTheAnswer() throws Exception {
        StubGateway gateway = new StubGateway(REPO_JSON);
        Beacon.FarReport report = new Beacon(gateway)
            .report("STARFALL088", "ForkKnight");

        assertEquals("STARFALL088/ForkKnight", report.fullName());
        assertEquals("A knightly Git client", report.tale());
        assertEquals(7, report.stars());
        assertEquals(2, report.forks());
        assertEquals(3, report.openIssues());
        assertEquals("main", report.banner());
        assertEquals("2026-09-25T17:52:27Z", report.lastSealed());
        assertEquals("https://api.github.com/repos/STARFALL088/ForkKnight",
            gateway.asked.get(0));
    }

    @Test
    void newestFeatsKeepTheirFirstLineAndHeroes() throws Exception {
        StubGateway gateway = new StubGateway(COMMITS_JSON);
        List<Beacon.FarFeat> feats = new Beacon(gateway)
            .newestFeats("STARFALL088", "ForkKnight");

        assertEquals(2, feats.size());
        assertEquals("first line kept", feats.get(0).message());
        assertEquals("STARFALL088", feats.get(0).hero());
        assertEquals("2026-09-25T17:52:27Z", feats.get(0).day());
        assertEquals("single line feat", feats.get(1).message());
        assertEquals("keeper", feats.get(1).hero());
        assertTrue(gateway.asked.get(0).endsWith(
            "/repos/STARFALL088/ForkKnight/commits?per_page=10"));
    }

    @Test
    void refusesNamesThatCouldRideIntoTheUrl() {
        StubGateway gateway = new StubGateway();
        Beacon beacon = new Beacon(gateway);

        assertThrows(IllegalArgumentException.class,
            () -> beacon.report("STARFALL088/..", "ForkKnight"));
        assertThrows(IllegalArgumentException.class,
            () -> beacon.report("STARFALL088", "a/b"));
        assertThrows(IllegalArgumentException.class,
            () -> beacon.report("", "ForkKnight"));
        assertTrue(gateway.asked.isEmpty());
    }

    @Test
    void missingFieldsFallBackInsteadOfExploding() throws Exception {
        StubGateway gateway = new StubGateway("{\"full_name\":\"x/y\"}");
        Beacon.FarReport report = new Beacon(gateway).report("x", "y");

        assertEquals("x/y", report.fullName());
        assertNull(report.tale());
        assertEquals(0, report.stars());
        assertEquals(0, report.openIssues());
    }

    // ------------------ the real transport ------------------

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/answer", exchange -> {
            byte[] bytes = body.getBytes("UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/answer";
    }

    @Test
    void theJdkGatewayReallyCarriesAnHttpAnswer() throws Exception {
        String url = serve(200, REPO_JSON);
        String body = new JdkHttpGateway().get(url);
        var object = Json.object(Json.parse(body));
        assertEquals("STARFALL088/ForkKnight", object.get("full_name"));
    }

    @Test
    void theJdkGatewayRefusesAnythingButTwoXx() throws Exception {
        String url = serve(404, "{\"message\":\"no such realm\"}");
        IOException failure = assertThrows(IOException.class,
            () -> new JdkHttpGateway().get(url));
        assertTrue(failure.getMessage().contains("404"));
    }
}
