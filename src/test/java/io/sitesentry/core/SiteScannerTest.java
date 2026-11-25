package io.sitesentry.core;

import com.sun.net.httpserver.HttpServer;
import io.sitesentry.report.ReportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SiteScannerTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 200,
                "<html><head><title>Home</title><meta name='description' content='Home page'></head>"
                        + "<body><a href='/about'>About</a><a href='/missing'>Broken</a>"
                        + "<a href='https://example.org/outside'>External</a></body></html>"));
        server.createContext("/about", exchange -> respond(exchange, 200,
                "<html><head><title>About</title><meta name='description' content='About page'></head></html>"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "Not found"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void crawlsSameOriginAndFindsBrokenInternalPage() throws Exception {
        List<PageResult> pages = new SiteScanner(Duration.ofSeconds(2), 10, 2).scan(baseUrl);
        assertEquals(3, pages.size());
        assertEquals(200, pages.get(0).statusCode());
        PageResult missing = pages.stream().filter(p -> p.url().endsWith("/missing")).findFirst().orElseThrow();
        assertEquals(404, missing.statusCode());
        assertTrue(missing.issues().stream().anyMatch(issue -> issue.contains("404")));
        assertTrue(pages.stream().noneMatch(p -> p.url().contains("example.org")));
    }

    @Test
    void respectsPageLimit() throws Exception {
        List<PageResult> pages = new SiteScanner(Duration.ofSeconds(2), 1, 3).scan(baseUrl);
        assertEquals(1, pages.size());
    }

    @Test
    void flagsMissingMetadataAndProducesReports() throws Exception {
        server.createContext("/plain", exchange -> respond(exchange, 200, "<html><body>No metadata</body></html>"));
        List<PageResult> pages = new SiteScanner(Duration.ofSeconds(2), 1, 0).scan(baseUrl + "/plain");
        assertTrue(pages.getFirst().issues().contains("Missing page title"));
        assertTrue(pages.getFirst().issues().contains("Missing meta description"));
        assertTrue(ReportWriter.markdown(pages).contains("SiteSentry scan report"));
        assertTrue(ReportWriter.json(pages).contains("\"statusCode\" : 200"));
    }

    @Test
    void onlyAllowsHttpAndHttpsAndRemovesFragments() {
        assertEquals("https://example.com/path?q=1", SiteScanner.normalizeUrl("HTTPS://EXAMPLE.COM/path?q=1#section").toString());
        assertThrows(IllegalArgumentException.class, () -> SiteScanner.normalizeUrl("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> SiteScanner.normalizeUrl("https://user:pass@example.com"));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }
}
