package io.sitesentry.core;

import java.util.List;

public record PageResult(
        String url,
        int statusCode,
        long responseTimeMs,
        String title,
        String metaDescription,
        List<String> internalLinks,
        List<String> issues) {
    public PageResult {
        internalLinks = List.copyOf(internalLinks);
        issues = List.copyOf(issues);
    }
}
