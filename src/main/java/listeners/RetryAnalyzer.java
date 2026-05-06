package listeners;

import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import utils.ConfigReader;

/** Retries only Selenium-level failures up to {@code retry.max}; assertions never retry. */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RetryAnalyzer.class);

    private int attempt = 0;

    @Override
    public boolean retry(ITestResult result) {
        Throwable error = result.getThrowable();

        if (!isRetryable(error)) {
            log.info("Not retrying {} — failure is an assertion ({})",
                    result.getMethod().getQualifiedName(),
                    error == null ? "no throwable" : error.getClass().getSimpleName());
            return false;
        }

        int maxRetry = ConfigReader.getInstance().getInt("retry.max", 1);
        if (attempt < maxRetry) {
            attempt++;
            log.warn("Retrying {} (attempt {}/{}) — {}",
                    result.getMethod().getQualifiedName(), attempt, maxRetry,
                    error.getClass().getSimpleName());
            return true;
        }
        return false;
    }

    /** Retry only when the failure is infrastructure-level (Selenium / driver) — never on assertions. */
    private boolean isRetryable(Throwable error) {
        if (error == null) return false;
        if (error instanceof AssertionError) return false;
        // Walk the cause chain — AssertionError wrapped in RuntimeException still must not retry.
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AssertionError) return false;
            if (t instanceof WebDriverException) return true;
        }
        return false;
    }
}
