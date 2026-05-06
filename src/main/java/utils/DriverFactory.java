package utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Thread-local WebDriver factory.
 * Browser selection priority: {@link #setBrowser(String)} override → {@code browser} property → chrome.
 */
public final class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);
    private static final ThreadLocal<WebDriver> DRIVER_POOL = new ThreadLocal<>();
    private static final ThreadLocal<String> BROWSER_OVERRIDE = new ThreadLocal<>();

    private DriverFactory() {
    }

    /** Sets the browser for the current thread before driver creation. */
    public static void setBrowser(String browser) {
        BROWSER_OVERRIDE.set(browser);
    }

    public static WebDriver getDriver() {
        if (DRIVER_POOL.get() == null) {
            DRIVER_POOL.set(createDriver());
        }
        return DRIVER_POOL.get();
    }

    /** Returns the current driver without creating one (used by listeners for screenshots). */
    public static WebDriver peekDriver() {
        return DRIVER_POOL.get();
    }

    public static void quitDriver() {
        WebDriver driver = DRIVER_POOL.get();
        if (driver != null) {
            log.info("Quitting browser");
            driver.quit();
            DRIVER_POOL.remove();
        }
        BROWSER_OVERRIDE.remove();
    }

    private static WebDriver createDriver() {
        ConfigReader config = ConfigReader.getInstance();

        String override = BROWSER_OVERRIDE.get();
        String browser = (override != null && !override.isBlank())
                ? override.toLowerCase()
                : config.get("browser", "chrome").toLowerCase();

        boolean headless = config.getBoolean("headless", false);
        int pageLoadTimeout = config.getInt("page.load.timeout", 30);

        WebDriver driver = switch (browser) {
            case "firefox" -> {
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions opts = buildFirefoxOptions(headless);
                log.info("Starting Firefox (headless={})", headless);
                yield new FirefoxDriver(opts);
            }
            default -> {
                WebDriverManager.chromedriver().setup();
                ChromeOptions opts = buildChromeOptions(headless);
                log.info("Starting Chrome (headless={})", headless);
                yield new ChromeDriver(opts);
            }
        };

        driver.manage().window().maximize();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeout));
        return driver;
    }

    private static FirefoxOptions buildFirefoxOptions(boolean headless) {
        FirefoxOptions opts = new FirefoxOptions();
        opts.setPageLoadStrategy(PageLoadStrategy.EAGER);
        // Private profile keeps cookies / localStorage isolated per launch.
        opts.addArguments("-private");
        opts.addPreference("browser.privatebrowsing.autostart", true);
        if (headless) opts.addArguments("--headless");
        return opts;
    }

    private static ChromeOptions buildChromeOptions(boolean headless) {
        ChromeOptions opts = new ChromeOptions();
        opts.setPageLoadStrategy(PageLoadStrategy.EAGER);
        // --incognito isolates profile per launch without the 30s+ homepage regression we hit when also disabling cache.
        opts.addArguments(
                "--disable-notifications",
                "--disable-popup-blocking",
                "--start-maximized",
                "--incognito");
        if (headless) opts.addArguments("--headless=new");
        return opts;
    }
}
