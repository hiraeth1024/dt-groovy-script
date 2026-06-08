#
wdCLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Compile
mvn compile

# Run the spider
java -cp "target/classes;lib/*" site.yesz.Main
```

Or use `run.bat` which does both steps.

## Architecture

This is a **Groovy-script-driven web crawler runner**. The Java-side is a thin harness (`site.yesz.Main`) that boots a WebMagic spider; all actual crawling and parsing logic lives in Groovy scripts loaded at runtime.

### Core flow

1. `Main.java` implements WebMagic's `PageProcessor`, configures the spider (threads, downloader, proxy), and fires an initial request.
2. On each page fetch, `process(Page)` loads a Groovy script from the filesystem via `GroovyScriptUtil.loadFromFile()`.
3. The Groovy script (implementing `IparseScript`) parses the page through `doParse(ParsePageRequest)` — extracting data, generating child requests for the spider to follow, and optionally downloading files.
4. The spider recurses through child requests, each tagged with a `parser` string that the Groovy script uses as a stage identifier (like a state machine) to know which parsing branch to take.

### Groovy scripts (`src/main/resources/groovy-scripts/`)

Each `.groovy` file defines a class implementing `IparseScript` with two lifecycles:

- **`doParse(ParsePageRequest)`** — The main entry. Uses `page.request.getExtra("parser")` as a stage discriminator (null = first page, then string labels for subsequent stages). Returns a `ParsePageResult` with child requests, data maps, file bytes, and dedup keys.
- **`beforeDownloader(AntiPageRequest)`** — Optional. Configures anti-crawling measures (headers, proxy, Python-based cookie generation, Playwright integration).

Scripts follow a multi-stage pipeline pattern: each stage creates child requests tagged with the next stage name and carries forward data via `request.putExtra(key, value)`. Templates are in `groovy-scripts/template/`.

### Key files

| File | Role |
|---|---|
| `src/main/java/site/yesz/Main.java` | Spider harness — boot config, page processing dispatch, file upload |
| `src/main/resources/groovy-scripts/` | All crawl/parse Groovy scripts, organized by data source |
| `src/main/resources/groovy-scripts/template/` | Reusable script templates |
| `lib/spider-crawler-server-fat.jar` | System-scoped dependency providing `GroovyScriptUtil`, `IparseScript`, WebMagic, and spider infrastructure |
| `src/main/resources/result/result.json` | Output directory for crawl results |

### Configuration points in `Main.java`

- `groovyFileName` — Which Groovy script to load (defaults to the template)
- `startUrl` — Initial URL for the spider
- `methodType` / `contentType` — HTTP method for the initial request (GET, POST_JSON, POST_FORM)
- `taskType` — `PREVIEW_TASK` (only follows first child request) or full crawl
- `useProxy` — Enables proxy rotation via `LocalHttpProxyProvider`
- `uploadUrl` / `downloadUrl` — File service endpoints
