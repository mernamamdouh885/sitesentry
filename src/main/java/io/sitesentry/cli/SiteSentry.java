package io.sitesentry.cli;

import io.sitesentry.core.PageResult;
import io.sitesentry.core.SiteScanner;
import io.sitesentry.report.ReportWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class SiteSentry {
    private SiteSentry() { }

    public static void main(String[] args) {
        try {
            if (args.length == 0 || has(args, "--help") || has(args, "-h")) {
                System.out.print(usage());
                return;
            }
            Options options = Options.parse(args);
            List<PageResult> pages = new SiteScanner(Duration.ofSeconds(options.timeoutSeconds),
                    options.maxPages, options.maxDepth).scan(options.url);
            String report = options.format.equals("json") ? ReportWriter.json(pages) : ReportWriter.markdown(pages);
            if (options.output == null || options.output.equals("-")) {
                System.out.println(report);
            } else {
                Files.writeString(Path.of(options.output), report + (report.endsWith("\n") ? "" : "\n"));
                System.out.println("Report written to " + options.output);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run with --help for usage.");
            System.exit(2);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Scan interrupted.");
            System.exit(130);
        }
    }

    private static boolean has(String[] args, String flag) {
        for (String arg : args) if (arg.equals(flag)) return true;
        return false;
    }

    private static String usage() {
        return """
                SiteSentry — a small, bounded website health and link checker

                Usage: java -jar sitesentry-0.1.0.jar <url> [options]

                Options:
                  --max-pages N     Maximum number of same-origin pages to scan (default: 50)
                  --depth N         Maximum link depth from the starting page (default: 2)
                  --timeout SECONDS Per-request timeout (default: 10)
                  --format FORMAT   markdown or json (default: markdown)
                  --output FILE     Write the report to a file; use - for stdout
                  --help, -h        Show this help
                """;
    }

    private static final class Options {
        String url;
        int maxPages = 50;
        int maxDepth = 2;
        int timeoutSeconds = 10;
        String format = "markdown";
        String output;

        static Options parse(String[] args) {
            Options options = new Options();
            options.url = args[0];
            for (int i = 1; i < args.length; i++) {
                String flag = args[i];
                if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
                String value = args[++i];
                try {
                    switch (flag) {
                        case "--max-pages" -> options.maxPages = Integer.parseInt(value);
                        case "--depth" -> options.maxDepth = Integer.parseInt(value);
                        case "--timeout" -> options.timeoutSeconds = Integer.parseInt(value);
                        case "--format" -> options.format = value.toLowerCase(Locale.ROOT);
                        case "--output" -> options.output = value;
                        default -> throw new IllegalArgumentException("Unknown option: " + flag);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected a number for " + flag + ": " + value);
                }
            }
            if (!options.format.equals("json") && !options.format.equals("markdown"))
                throw new IllegalArgumentException("--format must be markdown or json");
            if (options.maxPages < 1 || options.maxPages > 10_000)
                throw new IllegalArgumentException("--max-pages must be between 1 and 10000");
            if (options.maxDepth < 0 || options.maxDepth > 20)
                throw new IllegalArgumentException("--depth must be between 0 and 20");
            if (options.timeoutSeconds < 1 || options.timeoutSeconds > 300)
                throw new IllegalArgumentException("--timeout must be between 1 and 300 seconds");
            return options;
        }
    }
}
