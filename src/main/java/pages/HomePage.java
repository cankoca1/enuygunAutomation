package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.ConfigReader;
import utils.WaitUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Page object for the enuygun.com home page (round-trip search form). */
public class HomePage extends BasePage {

    private static final By ROUND_TRIP_LABEL =
            By.cssSelector("[data-testid='search-round-trip-label']");

    private static final By ORIGIN_INPUT =
            By.cssSelector("[data-testid='endesign-flight-origin-autosuggestion-input']");
    private static final By DESTINATION_INPUT =
            By.cssSelector("[data-testid='endesign-flight-destination-autosuggestion-input']");
    private static final By ORIGIN_AUTOCOMPLETE_FIRST =
            By.cssSelector("[data-testid='endesign-flight-origin-autosuggestion-option-item-0']");
    private static final By DESTINATION_AUTOCOMPLETE_FIRST =
            By.cssSelector("[data-testid='endesign-flight-destination-autosuggestion-option-item-0']");

    private static final By DEPARTURE_DATE_INPUT =
            By.cssSelector("[data-testid='enuygun-homepage-flight-departureDate-datepicker-input']");
    private static final By RETURN_DATE_INPUT =
            By.cssSelector("[data-testid='enuygun-homepage-flight-returnDate-datepicker-input']");
    private static final By CALENDAR_NEXT_MONTH =
            By.cssSelector("[data-testid='datepicker-next-month-button']");

    private static final By SEARCH_BUTTON =
            By.cssSelector("[data-testid='enuygun-homepage-flight-submitButton']");

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage open() {
        String url = ConfigReader.getInstance().get("base.url");
        driver.get(url);
        step("Opened " + url);
        dismissCookieIfPresent();
        return this;
    }

    public SearchResultsPage searchRoundTrip(String origin, String destination,
                                              String departDate, String returnDate) {
        click(ROUND_TRIP_LABEL);
        step("Round-trip selected");

        clearAndType(ORIGIN_INPUT, origin);
        WaitUtils.waitForClickable(driver, ORIGIN_AUTOCOMPLETE_FIRST, 10).click();
        step("Origin set to <b>" + origin + "</b>");

        clearAndType(DESTINATION_INPUT, destination);
        WaitUtils.waitForClickable(driver, DESTINATION_AUTOCOMPLETE_FIRST, 10).click();
        step("Destination set to <b>" + destination + "</b>");

        selectDate(DEPARTURE_DATE_INPUT, departDate);
        step("Departure date set to <b>" + departDate + "</b>");

        selectDate(RETURN_DATE_INPUT, returnDate);
        step("Return date set to <b>" + returnDate + "</b>");

        return clickSearch();
    }

    private SearchResultsPage clickSearch() {
        Set<String> originalHandles = driver.getWindowHandles();

        click(SEARCH_BUTTON);
        step("Search button clicked");

        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> d.getWindowHandles().size() > originalHandles.size());

            for (String handle : driver.getWindowHandles()) {
                if (!originalHandles.contains(handle)) {
                    driver.switchTo().window(handle);
                    step("Switched to results tab: " + driver.getCurrentUrl());
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("No new tab detected, results likely in same window");
        }

        return new SearchResultsPage(driver);
    }

    private void clearAndType(By locator, String text) {
        WebElement el = WaitUtils.waitForClickable(driver, locator);
        el.click();
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        el.sendKeys(text);
    }

    private void selectDate(By inputLocator, String date) {
        WebElement el = WaitUtils.waitForClickable(driver, inputLocator);
        el.click();

        LocalDate target = LocalDate.parse(date, DD_MM_YYYY);
        String isoDate = target.format(DateTimeFormatter.ISO_LOCAL_DATE);

        By dayBtn = By.cssSelector(
                "button[data-testid='datepicker-active-day'][title='" + isoDate + "']");
        try {
            WaitUtils.waitForClickable(driver, dayBtn, 3).click();
            return;
        } catch (Exception ignored) {
            log.debug("Day {} not visible in current month, navigating forward", isoDate);
        }

        for (int i = 0; i < 12; i++) {
            try {
                jsClick(CALENDAR_NEXT_MONTH);
            } catch (Exception e) {
                By arrow = By.cssSelector(
                        ".datepicker-next, [aria-label*='next' i], [aria-label*='Next' i], [class*='next' i]");
                jsClick(arrow);
            }
            try {
                WaitUtils.waitForClickable(driver, dayBtn, 2).click();
                return;
            } catch (Exception ignored) {
            }
        }

        log.warn("Calendar navigation failed for '{}', falling back to keyboard", date);
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"), date, Keys.TAB);
    }
}
