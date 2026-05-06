package listeners;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import utils.ConfigReader;
import utils.DriverFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Drives ExtentReports lifecycle, screenshot capture, and retry-attempt labelling. */
public class TestListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(TestListener.class);
    private static ExtentReports extent;

    private final Map<String, Integer> attemptCounter = new ConcurrentHashMap<>();

    @Override
    public void onStart(ITestContext context) {
        if (extent == null) {
            synchronized (TestListener.class) {
                if (extent == null) {
                    ExtentSparkReporter spark = new ExtentSparkReporter("test-output/ExtentReport.html");
                    spark.config().setTheme(Theme.STANDARD);
                    spark.config().setDocumentTitle("Enuygun Test Report");
                    spark.config().setReportName("Flight Search Automation");
                    spark.config().setTimeStampFormat("dd.MM.yyyy HH:mm:ss");
                    spark.config().setCss(".badge-primary { background-color: #2563eb; }"
                            + ".test-content .left-col { max-width: 100%; }");

                    extent = new ExtentReports();
                    extent.setSystemInfo("OS", System.getProperty("os.name"));
                    extent.setSystemInfo("Java", System.getProperty("java.version"));
                    extent.setSystemInfo("Base URL",
                            ConfigReader.getInstance().get("base.url", "https://www.enuygun.com"));
                    extent.attachReporter(spark);
                }
            }
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        String key = methodKey(result);
        int attempt = attemptCounter.merge(key, 1, Integer::sum);

        String testName = result.getMethod().getMethodName();
        if (attempt > 1) {
            testName += " [Retry " + (attempt - 1) + "]";
        }

        String description = result.getMethod().getDescription();
        ExtentTest test = extent.createTest(testName, description != null ? description : "");

        String contextName = result.getTestContext().getName();
        test.assignCategory(contextName);

        if (attempt > 1) {
            test.info("Retry attempt <b>" + (attempt - 1) + "</b> — previous run failed");
        }

        Object[] params = result.getParameters();
        if (params != null && params.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Object p : params) sb.append("<b>").append(p).append("</b>").append(" , ");
            test.info("Parameters: " + sb.substring(0, sb.length() - 3));
        }

        ExtentTestManager.set(test);
        log.info("STARTING: {} (attempt {})", result.getMethod().getQualifiedName(), attempt);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        long duration = result.getEndMillis() - result.getStartMillis();
        log.info("PASSED: {} ({}ms)", result.getMethod().getQualifiedName(), duration);

        ExtentTest test = ExtentTestManager.get();
        attachScreenshot(test, "Final State");
        test.pass("<b>Test passed</b> in " + formatDuration(duration));
        ExtentTestManager.clear();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        long duration = result.getEndMillis() - result.getStartMillis();
        log.error("FAILED: {} — {}", result.getMethod().getQualifiedName(),
                result.getThrowable().getMessage());

        ExtentTest test = ExtentTestManager.get();
        attachScreenshot(test, "Failure State");

        boolean willRetry = result.getMethod().getRetryAnalyzerClass() != null
                && isRetryAvailable(result);

        if (willRetry) {
            test.warning("<b>Test failed</b> in " + formatDuration(duration)
                    + " — will be retried");
            test.warning(result.getThrowable());
        } else {
            test.fail(result.getThrowable());
        }
        ExtentTestManager.clear();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.warn("SKIPPED: {}", result.getMethod().getQualifiedName());

        ExtentTest test = ExtentTestManager.get();
        if (test == null) {
            String testName = result.getMethod().getMethodName();
            test = extent.createTest(testName + " [Skipped]");
            test.assignCategory(result.getTestContext().getName());
        }

        // Driver is still alive here (quitDriver runs in @AfterMethod), so we can capture the skip state.
        attachScreenshot(test, "Skip State");

        if (result.getThrowable() != null) {
            test.skip(result.getThrowable());
        } else {
            test.skip("Test skipped");
        }
        ExtentTestManager.clear();
    }

    @Override
    public void onFinish(ITestContext context) {
        if (extent != null) {
            extent.flush();
            log.info("ExtentReport saved: test-output/ExtentReport.html");
        }
    }

    private boolean isRetryAvailable(ITestResult result) {
        String key = methodKey(result);
        int currentAttempt = attemptCounter.getOrDefault(key, 1);
        try {
            int maxRetry = ConfigReader.getInstance().getInt("retry.max", 1);
            return currentAttempt <= maxRetry;
        } catch (Exception e) {
            return false;
        }
    }

    private String methodKey(ITestResult result) {
        return result.getTestContext().getName()
                + "#" + result.getMethod().getQualifiedName()
                + Arrays.toString(result.getParameters());
    }

    private void attachScreenshot(ExtentTest test, String title) {
        try {
            WebDriver driver = DriverFactory.peekDriver();
            if (driver == null) return;

            String base64 = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
            test.info(title,
                    MediaEntityBuilder.createScreenCaptureFromBase64String(base64).build());
        } catch (Exception e) {
            log.debug("Could not attach screenshot: {}", e.getMessage());
        }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }
}
