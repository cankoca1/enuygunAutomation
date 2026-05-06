package utils;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/** Reusable explicit-wait helpers — every method polls via WebDriverWait, never Thread.sleep. */
public final class WaitUtils {

    private static final Logger log = LoggerFactory.getLogger(WaitUtils.class);
    private static final int DEFAULT_TIMEOUT =
            ConfigReader.getInstance().getInt("explicit.wait", 15);

    private WaitUtils() {
    }

    private static WebDriverWait wait(WebDriver driver, int seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds));
    }

    // ── Visibility ──────────────────────────────────────────────

    public static WebElement waitForVisible(WebDriver driver, By locator) {
        return waitForVisible(driver, locator, DEFAULT_TIMEOUT);
    }

    public static WebElement waitForVisible(WebDriver driver, By locator, int timeoutSec) {
        log.debug("Waiting up to {}s for element visible: {}", timeoutSec, locator);
        return wait(driver, timeoutSec).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    // ── Clickability ────────────────────────────────────────────

    public static WebElement waitForClickable(WebDriver driver, By locator) {
        return waitForClickable(driver, locator, DEFAULT_TIMEOUT);
    }

    public static WebElement waitForClickable(WebDriver driver, By locator, int timeoutSec) {
        log.debug("Waiting up to {}s for element clickable: {}", timeoutSec, locator);
        return wait(driver, timeoutSec).until(ExpectedConditions.elementToBeClickable(locator));
    }

    // ── Presence / Invisibility ─────────────────────────────────

    public static WebElement waitForPresence(WebDriver driver, By locator) {
        return waitForPresence(driver, locator, DEFAULT_TIMEOUT);
    }

    public static WebElement waitForPresence(WebDriver driver, By locator, int timeoutSec) {
        log.debug("Waiting up to {}s for element present: {}", timeoutSec, locator);
        return wait(driver, timeoutSec).until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public static boolean waitForInvisible(WebDriver driver, By locator, int timeoutSec) {
        log.debug("Waiting up to {}s for element invisible: {}", timeoutSec, locator);
        return wait(driver, timeoutSec).until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    // ── URL conditions ──────────────────────────────────────────

    public static void waitForUrlContains(WebDriver driver, String urlFragment, int timeoutSec) {
        log.debug("Waiting up to {}s for URL to contain '{}'", timeoutSec, urlFragment);
        wait(driver, timeoutSec).until(ExpectedConditions.urlContains(urlFragment));
    }

    // ── Staleness-aware waits ───────────────────────────────────

    /** Waits for {@code referenceElement} to go stale, then for {@code newLocator} to appear. */
    public static void waitForStalenessAndReappear(WebDriver driver,
                                                    WebElement referenceElement,
                                                    By newLocator,
                                                    int timeoutSec) {
        log.debug("Waiting for staleness then reappear: {}", newLocator);
        try {
            wait(driver, timeoutSec).until(ExpectedConditions.stalenessOf(referenceElement));
        } catch (TimeoutException e) {
            log.debug("Reference element did not go stale — DOM may not have re-rendered");
        }
        wait(driver, timeoutSec).until(ExpectedConditions.visibilityOfElementLocated(newLocator));
    }

    /** Waits until the count of elements matching {@code locator} differs from {@code previousCount}. */
    public static void waitForCountChange(WebDriver driver, By locator,
                                           int previousCount, int timeoutSec) {
        log.debug("Waiting for element count to change from {} for: {}", previousCount, locator);
        wait(driver, timeoutSec).until((ExpectedCondition<Boolean>) d -> {
            int current = d.findElements(locator).size();
            return current != previousCount;
        });
    }

    // ── Safe click with retry ───────────────────────────────────

    /** Clicks {@code locator}, retrying once on stale or intercepted-click exceptions. */
    public static void safeClick(WebDriver driver, By locator) {
        try {
            waitForClickable(driver, locator).click();
        } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
            log.warn("Click intercepted/stale for {} — retrying", locator);
            waitForClickable(driver, locator, 5).click();
        }
    }
}
