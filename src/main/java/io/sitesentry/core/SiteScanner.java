package io.sitesentry.core;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Crawls pages on one origin, with explicit page and depth limits. */
public final class SiteScanner {
    private final HttpClient client;
    private final Duration timeout;
    private final int maxPages;
    private final int maxDepth;
    private final String userAgent;

    public SiteScanner(Duration timeout, int maxPages, int maxDepth) {
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (maxPages < 1) throw new IllegalArgumentException("maxPages must be at least 1");
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth cannot be negative");
        this.timeout = timeout;
        this.maxPages = maxPages;
        this.maxDepth = maxDepth;
        this.userAgent = "SiteSentry/0.1 (+https://github.com/mernamamdouh885/sitesentry)";
        this.client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public List<PageResult> scan(String startUrl) throws IOException, InterruptedException {
        URI start = normalizeUrl(startUrl);
        Map<String, PageResult> scanned = new LinkedHashMap<>();
        ArrayDeque<QueueItem> queue = new ArrayDeque<>();
        Set<String> queued = new LinkedHashSet<>();
        queue.add(new QueueItem(start, 0));
        queued.add(start.toString());

        while (!queue.isEmpty() && scanned.size() < maxPages) {
            QueueItem item = queue.removeFirst();
            PageResult page = scanPage(item.uri());
            scanned.put(item.uri().toString(), page);
            if (item.depth() >= maxDepth || page.statusCode() >= 400) continue;
            for (String link : page.internalLinks()) {
                if (scanned.size() + queue.size() >= maxPages) break;
                URI target;
                try { target = normalizeUrl(item.uri().resolve(link).toString()); }
                catch (IllegalArgumentException ignored) { continue; }
                if (sameOrigin(start, target) && queued.add(target.toString())) {
                    queue.addLast(new QueueItem(target, item.depth() + 1));
                }
            }
        }
        return List.copyOf(scanned.values());
    }

    public static URI normalizeUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https"))
                throw new IllegalArgumentException("Only http:// and https:// URLs are supported: " + raw);
            if (uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("URL must have a host and must not contain credentials: " + raw);
            URI clean = new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(),
                    uri.getPath().isEmpty() ? "/" : uri.getPath(), uri.getQuery(), null);
            return clean;
        } catch (java.net.URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid URL: " + raw, e);
        }
    }

    private PageResult scanPage(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("User-Agent", userAgent).header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .GET().build();
        long started = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        int status = response.statusCode();
        List<String> links = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        String title = "";
        String description = "";
        String contentType = response.headers().firstValue("content-type").orElse("");

        if (status >= 400) issues.add("HTTP request failed with status " + status);
        if (contentType.toLowerCase(Locale.ROOT).contains("html") && status < 400) {
            Document doc = Jsoup.parse(response.body(), uri.toString());
            title = doc.title().trim();
            description = doc.selectFirst("meta[name=description]") == null ? ""
                    : doc.selectFirst("meta[name=description]").attr("content").trim();
            if (title.isBlank()) issues.add("Missing page title");
            if (description.isBlank()) issues.add("Missing meta description");
            for (Element anchor : doc.select("a[href]")) {
                String href = anchor.attr("href").trim();
                if (href.isEmpty() || href.startsWith("#") || href.startsWith("mailto:")
                        || href.startsWith("tel:") || href.startsWith("javascript:")) continue;
                try {
                    URI target = normalizeUrl(uri.resolve(href).toString());
                    if (sameOrigin(uri, target)) links.add(target.toString());
                } catch (IllegalArgumentException ignored) { /* Ignore malformed and non-HTTP links. */ }
            }
        }
        return new PageResult(uri.toString(), status, elapsedMs, title, description,
                links.stream().distinct().toList(), issues);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private record QueueItem(URI uri, int depth) { }
}
