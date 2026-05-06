package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.WaitUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Page object for the flight search results page on enuygun.com. */
public class SearchResultsPage extends BasePage {

    // ── Loading indicator ───────────────────────────────────────
    private static final By LOADER =
            By.cssSelector("[data-testid='weg-loader'], .weg-loader, [class*='loader'], [class*='spinner']");

    // ── Flight result list ──────────────────────────────────────
    private static final By FLIGHT_LIST =
            By.cssSelector(".flight-list, [class*='flightList'], [class*='flight-list'], [data-testid*='flightList']");
    private static final By FLIGHT_ITEM =
            By.cssSelector(".flight-item, [class*='flightItem'], [class*='flight-item'], [data-testid*='flightInfo']");

    /** Primary time row; we fall back to per-card data-testid pairing if absent. */
    private static final By FLIGHT_SUMMARY_TIME_ROW =
            By.cssSelector("[class*='flight-summary-time']");
    private static final By SUMMARY_TIME_CELL =
            By.cssSelector("[class*='summary-time']");
    private static final By DEPARTURE_TIME_TID =
            By.cssSelector("[data-testid='departureTime']");
    private static final By ARRIVAL_TIME_TID =
            By.cssSelector("[data-testid='arrivalTime']");

    // ── Airline name on each card ───────────────────────────────
    private static final By AIRLINE_NAME =
            By.cssSelector(".summary-marketing-airlines");

    // ── Price on each card ──────────────────────────────────────
    private static final By FLIGHT_PRICE =
            By.cssSelector("[data-testid='flightInfoPrice']");

    // ── Sort buttons ────────────────────────────────────────────
    private static final By SORT_CHEAPEST =
            By.cssSelector("[data-testid='sortButtons0']");

    // ── Airline filter sidebar (absolute XPaths verified on live DOM) ──
    private static final By AIRLINE_FILTER_HEADER = By.xpath(
            "//*[@id=\"SearchRoot\"]/div[2]/div[2]/div[3]/div[6]/div[1]");
    private static final By THY_FILTER_CHECKBOX = By.xpath(
            "//*[@id=\"SearchRoot\"]/div[2]/div[2]/div[3]/div[6]/div[2]/div/label[3]/span[2]");
    private static final By AIRLINE_FILTER_BODY = By.xpath(
            "//*[@id=\"SearchRoot\"]/div[2]/div[2]/div[3]/div[6]/div[2]");

    // ── Departure time filter (absolute XPaths verified on live DOM) ──
    private static final By DEPARTURE_FILTER_HEADER = By.xpath(
            "/html/body/div[3]/div[4]/div[2]/div[2]/div[3]/div[4]/div[1]");
    private static final By DEPARTURE_TIME_PRESET = By.xpath(
            "/html/body/div[3]/div[4]/div[2]/div[2]/div[3]/div[4]/div[2]/div/div[1]/div[3]/p[3]");

    // ── Select / book button ────────────────────────────────────
    private static final By SELECT_FLIGHT_BTN =
            By.cssSelector(".action-select-btn");

    /** "Seç ve ilerle" button revealed after the first departure-flight click. */
    private static final By PROCEED_AFTER_DEPARTURE = By.xpath(
            "//*[@id=\"flight-0\"]/div[1]/div[6]/div[2]/button/div/i");

    /** First return-flight select target — second {@code .flight-item__wrapper} inside {@code #flight-0}. */
    private static final By SELECT_RETURN_FLIGHT_BTN = By.xpath(
            "(//*[@id='flight-0']/div[contains(concat(' ', normalize-space(@class), ' '), ' flight-item__wrapper ')])[2]");

    /** "Seç ve ilerle" button revealed after the return-flight click. */
    private static final By PROCEED_AFTER_RETURN = By.cssSelector(
            "#flight-0 > div.flight-item__wrapper.package-opened > div.package-row-design.collapse.show > div > div:nth-child(1)");

    // ── Booking / passenger step ────────────────────────────────
    private static final By BOOKING_CONTAINER =
            By.cssSelector(".passenger-info, .booking-container, [class*='passengerInfo']");

    public SearchResultsPage(WebDriver driver) {
        super(driver);
    }

    // ── Waiting ─────────────────────────────────────────────────
    public SearchResultsPage waitForResults() {
        step("Waiting for results page...");

        try {
            WaitUtils.waitForUrlContains(driver, "ucak-bileti", 10);
            step("Results page URL detected");
        } catch (Exception e) {
            log.debug("URL did not change to results pattern — may already be on results page");
        }

        dismissCookieIfPresent();

        try {
            WaitUtils.waitForInvisible(driver, LOADER, 3);
        } catch (Exception ignored) { }

        WaitUtils.waitForPresence(driver, FLIGHT_ITEM, 15);
        step("Flight results loaded (<b>" + driver.findElements(FLIGHT_ITEM).size() + " flights</b>)");
        return this;
    }

    // ── Queries ─────────────────────────────────────────────────
    public boolean isResultsListDisplayed() {
        return isDisplayed(FLIGHT_LIST);
    }
    public int getFlightCount() {
        return driver.findElements(FLIGHT_ITEM).size();
    }
    /** Pair of (departure, arrival) plus the DOM row, used to scroll to a failing leg. */
    private record DepartureArrival(String departure, String arrival, WebElement rowElement) {
        String legLabel() {
            return departure + " - " + arrival;
        }
    }

    /**
     * Collects visible (departure, arrival) pairs from {@code .flight-summary-time}
     * rows, falling back to per-card {@code data-testid} pairing if no rows match.
     */
    private List<DepartureArrival> collectDepartureArrivalFromCards() {
        LinkedHashSet<String> seenLabels = new LinkedHashSet<>();
        List<DepartureArrival> legs = new ArrayList<>();

        for (WebElement timeRow : driver.findElements(FLIGHT_SUMMARY_TIME_ROW)) {
            try {
                if (!timeRow.isDisplayed()) {
                    continue;
                }
                List<String> cells = visibleHHmmCells(timeRow, SUMMARY_TIME_CELL);
                if (cells.size() >= 2) {
                    addLegIfNew(legs, seenLabels, cells.get(0), cells.get(1), timeRow);
                }
            } catch (Exception ignored) {
            }
        }
        if (!legs.isEmpty()) {
            return legs;
        }

        for (WebElement card : driver.findElements(FLIGHT_ITEM)) {
            try {
                if (!card.isDisplayed()) {
                    continue;
                }
                List<String> deps = visibleHHmmCells(card, DEPARTURE_TIME_TID);
                List<String> arrs = visibleHHmmCells(card, ARRIVAL_TIME_TID);
                if (deps.isEmpty() || arrs.isEmpty()) {
                    continue;
                }
                addLegIfNew(legs, seenLabels, deps.get(0), arrs.get(0), card);
            } catch (Exception ignored) {
            }
        }
        return legs;
    }

    private void addLegIfNew(List<DepartureArrival> legs, Set<String> seen,
                             String dep, String arr, WebElement rowElement) {
        DepartureArrival leg = new DepartureArrival(dep, arr, rowElement);
        if (seen.add(leg.legLabel())) {
            legs.add(leg);
        }
    }

    private List<String> visibleHHmmCells(WebElement root, By by) {
        List<String> out = new ArrayList<>();
        for (WebElement cell : root.findElements(by)) {
            try {
                if (!cell.isDisplayed()) {
                    continue;
                }
                String t = cell.getText().trim();
                if (t.matches("\\d{2}:\\d{2}")) {
                    out.add(t);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Departure times only (row order), for time-filter assertions. */
    public List<String> getDepartureTimes() {
        return collectDepartureArrivalFromCards().stream()
                .map(DepartureArrival::departure)
                .toList();
    }
    public List<String> getAirlineNames() {
        return driver.findElements(AIRLINE_NAME).stream()
                .map(el -> el.getText().trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }
    public List<Double> getPrices() {
        List<Double> prices = new ArrayList<>();
        for (WebElement el : driver.findElements(FLIGHT_PRICE)) {
            String text = el.getText().trim();
            if (!text.isEmpty()) {
                prices.add(parsePrice(text));
            }
        }
        return prices;
    }

    // ── Filters ─────────────────────────────────────────────────
    public SearchResultsPage filterByTurkishAirlines() {
        scrollToElement(AIRLINE_FILTER_HEADER);
        click(AIRLINE_FILTER_HEADER);
        step("Airline filter expanded");

        WaitUtils.waitForVisible(driver, AIRLINE_FILTER_BODY, 5);
        int countBefore = driver.findElements(FLIGHT_ITEM).size();

        WebElement thy = WaitUtils.waitForClickable(driver, THY_FILTER_CHECKBOX, 5);
        scrollToElement(THY_FILTER_CHECKBOX);
        thy.click();
        step("THY airline filter applied");

        waitForResultsRefresh(countBefore);
        return this;
    }

    /** Expand the filter, pick the preset, then drag the upper slider handle to {@code maxHour}. */
    public SearchResultsPage filterByDepartureTime(int minHour, int maxHour) {
        int countBefore = driver.findElements(FLIGHT_ITEM).size();

        scrollToElement(DEPARTURE_FILTER_HEADER);
        click(DEPARTURE_FILTER_HEADER);
        step("Departure time filter expanded");

        WaitUtils.waitForClickable(driver, DEPARTURE_TIME_PRESET, 5).click();
        step("Departure time preset selected (target range "
                + toHourStr(minHour * 60) + "–" + toHourStr(maxHour * 60) + ")");

        By handle2 = By.cssSelector(".rc-slider-handle-2");
        WebElement handle = WaitUtils.waitForVisible(driver, handle2, 5);
        adjustSlider(handle, handle2, maxHour * 60);

        waitForResultsRefresh(countBefore);
        return this;
    }

    /** rc-slider snaps the nearest handle to a click on the track — we dispatch a native mousedown+mouseup at the target percentage. */
    private void adjustSlider(WebElement handle, By handleLocator, int targetMinutes) {
        int current = Integer.parseInt(handle.getAttribute("aria-valuenow"));
        if (current == targetMinutes) {
            step("Slider already at target: <b>" + toHourStr(targetMinutes) + "</b>");
            return;
        }

        WebElement track = handle.findElement(
                By.xpath("./ancestor::div[contains(@class,'rc-slider')][1]"));

        String script =
                "var track = arguments[0];" +
                "var target = arguments[1];" +
                "var rect = track.getBoundingClientRect();" +
                "var pct = target / 1439;" +
                "var x = rect.left + (pct * rect.width);" +
                "var y = rect.top + (rect.height / 2);" +
                "var opts = {clientX: x, clientY: y, bubbles: true, cancelable: true};" +
                "track.dispatchEvent(new MouseEvent('mousedown', opts));" +
                "track.dispatchEvent(new MouseEvent('mouseup', opts));";

        ((JavascriptExecutor) driver).executeScript(script, track, targetMinutes);

        handle = driver.findElement(handleLocator);
        int after = Integer.parseInt(handle.getAttribute("aria-valuenow"));
        step("Time filter slid: <b>" + toHourStr(current) + " → " + toHourStr(after) + "</b> (target " + toHourStr(targetMinutes) + ")");
    }
    public SearchResultsPage sortByCheapest() {
        int countBefore = driver.findElements(FLIGHT_ITEM).size();
        WebElement firstItem = driver.findElements(FLIGHT_ITEM).isEmpty()
                ? null : driver.findElements(FLIGHT_ITEM).get(0);

        click(SORT_CHEAPEST);
        step("Sorted results by cheapest price");

        if (firstItem != null) {
            waitForResultsRefreshByStaleness(firstItem);
        } else {
            WaitUtils.waitForVisible(driver, FLIGHT_LIST, 15);
        }
        return this;
    }
    // ── Flight selection ────────────────────────────────────────
    public SearchResultsPage selectFirstFlight() {
        String airline = driver.findElements(AIRLINE_NAME).isEmpty()
                ? "?" : driver.findElements(AIRLINE_NAME).get(0).getText().trim();
        double price = getFirstFlightPrice();

        WebElement btn = WaitUtils.waitForClickable(driver, SELECT_FLIGHT_BTN, 10);
        scrollToElement(SELECT_FLIGHT_BTN);
        btn.click();
        step("Selected first flight — airline: <b>" + airline + "</b>, price: <b>" + price + " TL</b>");
        return this;
    }

    /**
     * Walks the round-trip chain after {@link #selectFirstFlight()}:
     * confirm departure → select return → confirm return.
     * Staleness waits between clicks ensure we operate on the freshly-rendered DOM.
     */
    public SearchResultsPage completeRoundTripSelection() {
        // 1) Confirm the departure flight.
        WebElement proceedDep = WaitUtils.waitForClickable(driver, PROCEED_AFTER_DEPARTURE, 15);
        scrollToElement(PROCEED_AFTER_DEPARTURE);
        proceedDep.click();
        step("Clicked <b>'Seç ve ilerle'</b> on departure flight");

        // 2) Wait for the page to transition to return-flight selection.
        WaitUtils.waitForStalenessAndReappear(driver, proceedDep, SELECT_RETURN_FLIGHT_BTN, 30);
        step("Return-flight selection step is rendered");

        // 3) Pick the first return flight.
        WebElement returnSelect = WaitUtils.waitForClickable(driver, SELECT_RETURN_FLIGHT_BTN, 10);
        scrollToElement(SELECT_RETURN_FLIGHT_BTN);
        returnSelect.click();
        step("Selected first return flight");

        // 4) Wait up to 2 s for the return "Seç ve ilerle" button to become visible.
        WaitUtils.waitForVisible(driver, PROCEED_AFTER_RETURN, 2);
        step("Return-flight confirmation step is rendered");

        // 5) Confirm the return flight.
        WebElement proceedRet = WaitUtils.waitForClickable(driver, PROCEED_AFTER_RETURN, 15);
        scrollToElement(PROCEED_AFTER_RETURN);
        proceedRet.click();
        step("Clicked <b>'Seç ve ilerle'</b> on return flight");

        return this;
    }
    public double getFirstFlightPrice() {
        WebElement priceEl = WaitUtils.waitForVisible(driver, FLIGHT_PRICE);
        return parsePrice(priceEl.getText());
    }
    public boolean isBookingStepDisplayed() {
        try {
            WaitUtils.waitForUrlContains(driver, "booking", 8);
            step("Booking step detected — URL contains <b>booking</b>");
            return true;
        } catch (Exception e) {
            boolean container = isDisplayed(BOOKING_CONTAINER);
            if (container) {
                step("Booking container element is visible");
            }
            return container;
        }
    }

    /** Waits up to 20 s for the URL to change away from {@code originalUrl}. */
    public boolean hasPageTransitioned(String originalUrl) {
        boolean changed = false;
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(d -> !d.getCurrentUrl().equals(originalUrl));
            changed = true;
        } catch (TimeoutException ignored) {
        }
        step("URL transition check: " + (changed ? "page changed" : "same page"));
        return changed;
    }

    // ── Route verification ─────────────────────────────────────

    /** True when the current URL contains both origin and destination slugs. */
    public boolean isRouteMatchingUrl(String origin, String destination) {
        String url = driver.getCurrentUrl().toLowerCase();
        String o = origin.toLowerCase().replaceAll("[^a-z]", "");
        String d = destination.toLowerCase().replaceAll("[^a-z]", "");
        boolean matches = url.contains(o) && url.contains(d);
        step("Route check — origin <b>" + o + "</b>: " + url.contains(o) + ", destination <b>" + d + "</b>: " + url.contains(d));
        return matches;
    }

    // ── Validation helpers ──────────────────────────────────────

    /**
     * Asserts both departure and arrival hour of every visible flight fall in {@code [minHour, maxHour]}.
     * Stricter than the site's filter, which only constrains departure.
     */
    public boolean areAllFlightsWithinTimeRange(int minHour, int maxHour) {
        List<DepartureArrival> legs = collectDepartureArrivalFromCards();
        String rangeLabel = toHourStr(minHour * 60) + " – " + toHourStr(maxHour * 60);

        if (legs.isEmpty()) {
            step("Time check (.flight-summary-time .summary-time): <b>0</b> complete dep+arr pairs — cannot assert <b>"
                    + rangeLabel + "</b>");
            log.warn("No departure/arrival pairs found to validate");
            return false;
        }

        List<String> legLabels = legs.stream().map(DepartureArrival::legLabel).toList();
        String fullLegList = String.join(" / ", legLabels);
        int n = legs.size();
        step("Asserting <b>departure & arrival</b> in <b>" + rangeLabel + "</b> for <b>" + n
                + "</b> leg(s) (<b>.flight-summary-time</b> → <b>.summary-time</b>): <b>" + fullLegList + "</b>");

        for (DepartureArrival leg : legs) {
            int depH = parseHour(leg.departure());
            if (depH < minHour || depH > maxHour) {
                step("Leg <b>" + leg.legLabel() + "</b>: departure <b>" + leg.departure()
                        + "</b> outside <b>" + rangeLabel + "</b>");
                log.warn("Departure {} outside [{}, {}]", leg.departure(), minHour, maxHour);
                scrollToFailingLeg(leg);
                return false;
            }
            int arrH = parseHour(leg.arrival());
            if (arrH < minHour || arrH > maxHour) {
                step("Leg <b>" + leg.legLabel() + "</b>: arrival <b>" + leg.arrival()
                        + "</b> outside <b>" + rangeLabel + "</b>");
                log.warn("Arrival {} outside [{}, {}]", leg.arrival(), minHour, maxHour);
                scrollToFailingLeg(leg);
                return false;
            }
        }
        step("All <b>" + n + "</b> leg(s): departure and arrival within <b>" + rangeLabel + "</b>");
        return true;
    }

    /** Centers and highlights the failing leg so the listener's failure screenshot captures it. */
    private void scrollToFailingLeg(DepartureArrival leg) {
        try {
            WebElement row = leg.rowElement();
            if (row == null) {
                return;
            }
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "arguments[0].scrollIntoView({block:'center', behavior:'instant'});"
                            + "arguments[0].style.boxShadow = '0 0 0 3px #d32f2f inset';"
                            + "arguments[0].style.transition = 'box-shadow 200ms';",
                    row);

            new WebDriverWait(driver, Duration.ofSeconds(2))
                    .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                            "var r = arguments[0].getBoundingClientRect();"
                                    + "return r.top >= 0 && r.bottom <= (window.innerHeight || document.documentElement.clientHeight);",
                            row)));

            step("Scrolled to failing leg <b>" + leg.legLabel() + "</b> for screenshot");
        } catch (Exception e) {
            log.debug("Could not scroll to failing leg: {}", e.getMessage());
        }
    }
    public boolean areAllFlightsFromAirline(String expectedAirline) {
        List<String> airlines = getAirlineNames();
        if (airlines.isEmpty()) {
            step("Airline check: <b>0</b> airline names found — cannot assert");
            log.warn("No airline names found to validate");
            return false;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(airlines);
        step("Asserting all <b>" + airlines.size() + "</b> flights are <b>" + expectedAirline
                + "</b>. Unique airlines on page: <b>" + String.join(", ", unique) + "</b>");

        String expected = expectedAirline.toLowerCase();
        for (String airline : airlines) {
            if (!airline.toLowerCase().contains(expected)) {
                step("Unexpected airline: <b>" + airline + "</b> (expected <b>" + expectedAirline + "</b>)");
                log.warn("Unexpected airline: '{}' (expected '{}')", airline, expectedAirline);
                return false;
            }
        }
        step("All <b>" + airlines.size() + "</b> flights are <b>" + expectedAirline + "</b>");
        return true;
    }
    public boolean arePricesSortedAscending() {
        List<Double> prices = getPrices();
        if (prices.isEmpty()) {
            step("Price sort check: <b>0</b> prices found on page — assertion <b>FAILED</b>");
            log.warn("No prices found on page — cannot validate sort order");
            return false;
        }
        if (prices.size() == 1) {
            step("Price sort check: only <b>1</b> price on page — trivially sorted");
            log.info("Single price on page — treated as trivially sorted");
            return true;
        }
        step("Asserting <b>ascending</b> price order over <b>" + prices.size() + "</b> price(s): <b>"
                + prices + "</b>");

        for (int i = 1; i < prices.size(); i++) {
            if (prices.get(i) < prices.get(i - 1)) {
                step("Order broken at index <b>" + i + "</b>: <b>" + prices.get(i)
                        + "</b> < previous <b>" + prices.get(i - 1) + "</b>");
                log.warn("Price at index {} ({}) < previous ({})",
                        i, prices.get(i), prices.get(i - 1));
                return false;
            }
        }
        step("Prices are sorted ascending: <b>" + prices.get(0) + " → " + prices.get(prices.size() - 1) + "</b>");
        return true;
    }

    // ── Private helpers ─────────────────────────────────────────

    /** Waits for the flight count to change from {@code previousCount}, then for the list to be visible. */
    private void waitForResultsRefresh(int previousCount) {
        try {
            WaitUtils.waitForCountChange(driver, FLIGHT_ITEM, previousCount, 10);
        } catch (Exception e) {
            log.debug("Flight count did not change — results may already be filtered");
        }
        WaitUtils.waitForVisible(driver, FLIGHT_LIST, 15);
    }

    /** Waits for {@code referenceElement} to go stale, then for the list to reappear. */
    private void waitForResultsRefreshByStaleness(WebElement referenceElement) {
        WaitUtils.waitForStalenessAndReappear(driver, referenceElement, FLIGHT_LIST, 15);
    }

    private String toHourStr(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private int parseHour(String timeText) {
        Matcher m = Pattern.compile("(\\d{1,2}):\\d{2}").matcher(timeText);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        throw new IllegalArgumentException("Cannot parse hour from: " + timeText);
    }

    /** Parses Turkish-formatted prices like "2.270 TL" → 2270.0. */
    private double parsePrice(String raw) {
        String cleaned = raw.replaceAll("[^0-9.,]", "");
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else {
            cleaned = cleaned.replace(".", "");
        }
        return Double.parseDouble(cleaned);
    }
}
