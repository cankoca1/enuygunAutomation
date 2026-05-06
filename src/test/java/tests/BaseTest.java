package tests;

import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.*;
import pages.HomePage;
import utils.DateUtils;
import utils.DriverFactory;
import utils.ScreenshotUtils;

public abstract class BaseTest {

    protected WebDriver driver;
    protected HomePage homePage;

    @Parameters("browser")
    @BeforeMethod(alwaysRun = true)
    public void setUp(@Optional String browser) {
        // -Dbrowser=… (CLI) takes precedence over the testng.xml @Parameters value.
        String sysBrowser = System.getProperty("browser");
        if (sysBrowser != null && !sysBrowser.isBlank()) {
            DriverFactory.setBrowser(sysBrowser.trim());
        } else if (browser != null && !browser.isBlank()) {
            DriverFactory.setBrowser(browser);
        }
        driver = DriverFactory.getDriver();
        homePage = new HomePage(driver);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try {
            ScreenshotUtils.capture(driver, result.getMethod().getMethodName());
        } catch (Exception ignored) { }
        DriverFactory.quitDriver();
    }

    protected String[] futureDates(int departureDaysFromNow, int tripLengthDays) {
        return DateUtils.roundTripDates(departureDaysFromNow, tripLengthDays);
    }
}
