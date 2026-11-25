package io.sitesentry.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sitesentry.core.PageResult;

import java.io.IOException;
import java.util.List;

public final class ReportWriter {
    private ReportWriter() { }

    public static String json(List<PageResult> pages) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        long issues = pages.stream().mapToLong(page -> page.issues().size()).sum();
        return mapper.writeValueAsString(new ScanReport(pages.size(), issues, pages));
    }

    public static String markdown(List<PageResult> pages) {
        long issueCount = pages.stream().mapToLong(page -> page.issues().size()).sum();
        StringBuilder out = new StringBuilder("# SiteSentry scan report\n\n")
                .append("- Pages scanned: **").append(pages.size()).append("**\n")
                .append("- Issues found: **").append(issueCount).append("**\n\n")
                .append("| Status | Time | URL | Title | Issues |\n|---:|---:|---|---|---|\n");
        for (PageResult page : pages) {
            out.append('|').append(page.statusCode()).append('|').append(page.responseTimeMs()).append(" ms|")
                    .append(escape(page.url())).append('|').append(escape(page.title())).append('|')
                    .append(page.issues().isEmpty() ? "None" : escape(String.join("; ", page.issues())))
                    .append("|\n");
        }
        out.append("\n## Page details\n");
        for (PageResult page : pages) {
            out.append("\n### ").append(page.url()).append("\n\n")
                    .append("- Meta description: ").append(page.metaDescription().isBlank() ? "_(missing)_" : page.metaDescription()).append("\n")
                    .append("- Internal links found: ").append(page.internalLinks().size()).append("\n");
            if (!page.issues().isEmpty()) {
                out.append("- Issues:\n");
                page.issues().forEach(issue -> out.append("  - ").append(issue).append('\n'));
            }
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private record ScanReport(int pagesScanned, long issueCount, List<PageResult> pages) { }
}
