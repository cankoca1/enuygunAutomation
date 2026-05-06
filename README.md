# Enuygun Flight Search Automation

UI test automation framework for [enuygun.com](https://www.enuygun.com) flight search, built with **Selenium 4 + TestNG + Maven**, organized around the Page Object Model.

The suite covers three end-to-end scenarios on the round-trip flight flow:

| # | Test class | What it verifies |
|---|---|---|
| 1 | `FlightSearchTest` | Round-trip search + departure-time filter (10:00–18:00). Asserts every visible leg's **departure AND arrival** falls in range. |
| 2 | `PriceSortingTest` | Time + Türk Hava Yolları airline filters, then sort-by-cheapest. Asserts every card is THY and prices are ascending. |
| 3 | `CriticalPathTest` | Critical user journey: search → browse → validate cards → select departure + return → confirm transition to booking. |

---

## Tech stack

- **Java 17** (compiles via `maven.compiler.release=17`, runs fine on JDK 17/21)
- **Maven 3.9+** (Maven Wrapper included — `mvnw` / `mvnw.cmd`)
- **Selenium Java 4.21**
- **TestNG 7.10**
- **WebDriverManager 5.8** (auto-resolves chromedriver / geckodriver)
- **ExtentReports 5.1** (HTML report at `test-output/ExtentReport.html`)
- **SLF4J + Logback** for logs

---

## Project layout

```
.
├── pom.xml
├── mvnw / mvnw.cmd                  Maven wrapper
├── src/
│   ├── main/java/
│   │   ├── pages/                   Page Object Model
│   │   │   ├── BasePage.java
│   │   │   ├── HomePage.java
│   │   │   └── SearchResultsPage.java
│   │   ├── listeners/               TestNG hooks (Extent + retry + screenshots)
│   │   │   ├── ExtentTestManager.java
│   │   │   ├── RetryAnalyzer.java
│   │   │   ├── RetryTransformer.java
│   │   │   └── TestListener.java
│   │   └── utils/                   Cross-cutting helpers
│   │       ├── ConfigReader.java
│   │       ├── DateUtils.java
│   │       ├── DriverFactory.java   Thread-local driver, Chrome/Firefox
│   │       ├── ScreenshotUtils.java
│   │       └── WaitUtils.java       Explicit-wait helpers (no Thread.sleep)
│   ├── main/resources/
│   │   ├── config.properties        Runtime config
│   │   └── logback.xml
│   └── test/
│       ├── java/tests/
│       │   ├── BaseTest.java        @BeforeMethod/@AfterMethod, driver lifecycle
│       │   ├── FlightSearchTest.java
│       │   ├── PriceSortingTest.java
│       │   └── CriticalPathTest.java
│       └── resources/testng.xml     Suite definition (Chrome enabled, Firefox commented)
├── test-output/                     ExtentReport.html (generated)
├── screenshots/                     Failure + post-test screenshots (generated)
└── logs/                            Logback file output (generated)
```

---

## Prerequisites

- **JDK 17 or newer** with `JAVA_HOME` set
- **Maven 3.9+** (or just use the included wrapper)
- **Google Chrome** installed (default browser); Firefox optional
- Internet access (the site is live; WebDriverManager downloads drivers)

Verify with:

```bash
java -version
mvn -version
```

> **Windows tip:** if `JAVA_HOME` points to a non-existent path you'll see *"JAVA_HOME is set to an invalid directory"* before Maven even starts. Fix that first.

---

## Running the tests

### Full suite (all three cases, Chrome)

```bash
mvn test
```

This runs the suite defined in `src/test/resources/testng.xml`. After the run, `test-output/ExtentReport.html` is **opened automatically** by the `exec-maven-plugin` (Windows; on macOS/Linux just open it manually).

### A single test case

```bash
mvn test -Dtest=CriticalPathTest                              # Case 3 only
mvn test -Dtest=FlightSearchTest                              # Case 1 only
mvn test -Dtest=PriceSortingTest                              # Case 2 only
mvn test -Dtest=CriticalPathTest#testCriticalUserJourney      # single method
```

When using `-Dtest=`, also pass `"-Dsurefire.suiteXmlFiles="` to bypass the testng.xml suite, otherwise Surefire still runs everything in the suite:

```bash
mvn test -Dtest=CriticalPathTest -Dsurefire.suiteXmlFiles=
```

### CI mode (no auto-opened report, fail build on red tests)

```bash
mvn test -Pci
```

The default Surefire config has `testFailureIgnore=true` so the build is green even if individual tests fail (handy when developing locally so the report still opens). The `ci` profile flips that to `false`.

### Override config from the CLI

Every key in `src/main/resources/config.properties` can be overridden via `-D`:

```bash
mvn test -Dheadless=true                         # run Chrome headless
mvn test -Dbrowser=firefox                       # use Firefox instead of Chrome
mvn test -Dbase.url=https://www.enuygun.com
mvn test -Dexplicit.wait=15 -Dpage.load.timeout=60
mvn test -Dretry.max=2                           # retry flaky tests up to 2x
```

---

## Configuration

`src/main/resources/config.properties`:

| Key | Default | Description |
|---|---|---|
| `base.url` | `https://www.enuygun.com` | Target site URL |
| `browser` | `chrome` | `chrome` or `firefox` (overridable per-test via `testng.xml @Parameters` or `-Dbrowser`) |
| `headless` | `false` | Run browser without UI window |
| `explicit.wait` | `10` | Default `WebDriverWait` timeout (seconds) |
| `page.load.timeout` | `45` | WebDriver page-load timeout (seconds) |
| `retry.max` | `1` | Max retries per failed `@Test` (0 disables retries) |

Browser selection priority (highest → lowest):
1. `-Dbrowser=…` on the command line
2. `<parameter name="browser" value="…"/>` in `testng.xml`
3. `browser` key in `config.properties`

---

## Reports & artifacts

After every run:

| Path | What's in it |
|---|---|
| `test-output/ExtentReport.html` | Step-by-step HTML report with embedded screenshots on failure |
| `target/surefire-reports/` | Standard Surefire/TestNG XML + text reports |
| `screenshots/` | PNGs taken in `@AfterMethod` and on each test failure |
| `logs/` | Logback file output (rolled per run) |

---

## Framework features

- **Page Object Model** — selectors and interactions live in `pages/*`, tests stay declarative.
- **Thread-safe driver factory** — `ThreadLocal<WebDriver>` enables future parallel runs.
- **Explicit waits everywhere** — `WaitUtils` polls via `WebDriverWait`; no `Thread.sleep`.
- **Retry on transient failures** — `RetryAnalyzer` retries failures *except* `AssertionError`s, so real bugs are not masked.
- **Failure screenshots** — captured by `TestListener` and embedded into the Extent report; the failing flight leg is also auto-scrolled and highlighted before the snapshot.
- **UTF-8 everywhere** — Surefire JVM args force UTF-8 so Turkish characters (`ç ğ ı ö ş ü`) render correctly in console + reports.
- **Defensive selectors** — multi-fallback CSS (e.g. `.flight-item, [class*='flightItem'], …`) tolerates the site's minor DOM variants.
- **Single source of truth for routes/dates** — `DateUtils.roundTripDates(...)` produces always-future dates so the suite never goes stale.

---

## Common scenarios

**Re-run only the failed test from the previous run:** Surefire generates `target/surefire-reports/failsafe-summary.xml` style metadata; for now the simplest path is `-Dtest=…#methodName`.

**Run on Firefox:** uncomment the Firefox `<test>` blocks in `testng.xml`, or pass `-Dbrowser=firefox`.

**Headless on CI:** `mvn test -Pci -Dheadless=true`.

---

## Troubleshooting

- **`JAVA_HOME is set to an invalid directory`** — fix the env var to point to an existing JDK install.
- **`Could not find or load main class org.apache.maven.wrapper.MavenWrapperMain`** — the `.mvn/wrapper` folder is missing or corrupt. Either delete it and let `mvnw` re-bootstrap (needs internet), or call a system-wide `mvn` instead.
- **`WARNING: Unable to find CDP implementation matching <N>`** — harmless mismatch between the Selenium-bundled DevTools version and your Chrome. The tests do not use CDP features.
- **Cookie banner / popup intercepts a click** — `BasePage.dismissCookieIfPresent()` is called on every page object and is safe to invoke repeatedly.
- **Flight cards don't appear (`NoSuchElementException` on `.flight-item, …`)** — the search did not transition to the results tab. Check `screenshots/` for the failure snapshot; usually a transient first-paint flake. The retry analyzer will pick it up on the second attempt.

---

## Extending the suite

1. **Add a Page Object** in `src/main/java/pages/` — extend `BasePage` for shared utilities.
2. **Add a Test class** in `src/test/java/tests/` — extend `BaseTest` for the driver lifecycle.
3. **Wire it up** in `src/test/resources/testng.xml` so `mvn test` picks it up.
4. (Optional) Add a `@DataProvider` for routes/dates and feed off `BaseTest.futureDates(...)`.

---

## License

See [`LICENSE`](LICENSE).
