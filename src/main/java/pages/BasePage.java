package pages;

import listeners.ExtentTestManager;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.WaitUtils;

/**
 * Common base for all page objects.
 * Provides {@link #step(String)} (logs to SLF4J + ExtentReports) and a few
 * synchronization-safe interaction primitives.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected BasePage(WebDriver driver) {
        this.driver = driver;
    }

    /** Logs to SLF4J and to the active ExtentReports test node. */
    protected void step(String message) {
        log.info(message);
        ExtentTestManager.step(message);
    }

    protected void click(By locator) {
        WaitUtils.safeClick(driver, locator);
    }

    protected boolean isDisplayed(By locator) {
        try {
            return WaitUtils.waitForVisible(driver, locator, 5).isDisplayed();
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected void scrollToElement(By locator) {
        WebElement el = WaitUtils.waitForPresence(driver, locator);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", el);
    }

    protected void jsClick(By locator) {
        WebElement el = WaitUtils.waitForPresence(driver, locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private static final By COOKIE_BTN = By.id("onetrust-accept-btn-handler");

    protected void dismissCookieIfPresent() {
        try {
            WebElement btn = WaitUtils.waitForClickable(driver, COOKIE_BTN, 3);
            btn.click();
            step("Cookie popup dismissed");
        } catch (Exception ignored) {
        }
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }
}
