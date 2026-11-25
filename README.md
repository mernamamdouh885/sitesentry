# SiteSentry

SiteSentry is a small Java command-line tool that checks a website's availability and basic page health. It crawls links on the starting page's **same origin** (same scheme, host, and port), records HTTP status and response time, finds broken internal pages, checks for a page title and meta description, and exports Markdown or JSON reports.

This is an early, intentionally focused project: bounded crawling and readable output first; more checks can be added behind tests over time.

## Requirements

- Java 21 or newer
- Maven 3.8+

## Build and test

```bash
mvn test
mvn package
```

The shaded executable JAR is created at `target/sitesentry-0.1.0.jar`.

## Usage

```bash
java -jar target/sitesentry-0.1.0.jar https://example.com
```

Options:

```text
--max-pages N      Maximum pages to request (default 50; maximum 10000)
--depth N          Maximum link depth from the starting page (default 2; maximum 20)
--timeout SECONDS  Per-request timeout (default 10; maximum 300)
--format FORMAT    markdown or json (default markdown)
--output FILE      Save the report to a file; use - for standard output
--help, -h         Show usage
```

Examples:

```bash
java -jar target/sitesentry-0.1.0.jar https://example.com --max-pages 100 --depth 3
java -jar target/sitesentry-0.1.0.jar https://example.com --format json --output report.json
```

By default the Markdown report is printed to standard output. JSON contains `pagesScanned`, `issueCount`, and a `pages` array with each page's URL, status, elapsed time, title, meta description, internal links, and issues.

## Scope and safety

- Only `http` and `https` URLs are accepted; URL credentials are rejected.
- The scanner does not follow off-origin links and has page, depth, and timeout limits.
- This tool makes ordinary HTTP GET requests. Only scan sites you own or are authorized to test, and keep limits conservative for production systems.
- It is a basic availability/metadata checker, not a vulnerability scanner, SEO auditor, or replacement for a browser-based accessibility test.

## Current checks

- HTTP status code and request duration for every scanned page
- Same-origin link discovery and detection of linked pages returning 4xx/5xx
- Missing HTML `<title>` and `meta name="description"`
- Markdown and JSON report output

## Development notes

The project is organized into `core` (crawl and page results), `report` (formatters), and `cli` (arguments and process behavior). Integration tests use a local ephemeral HTTP server, so they do not depend on external websites.

## Roadmap

- Add an option to check links without crawling every linked page
- Add retry policy and a concise summary/exit code for CI use
- Improve handling of non-HTML responses and per-URL request failures
- Add configuration files and richer report summaries

## License

MIT. See [LICENSE](LICENSE).
