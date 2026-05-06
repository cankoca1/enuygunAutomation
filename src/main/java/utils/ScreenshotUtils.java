package utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Saves a PNG screenshot to disk for CI artefact collection (independent of ExtentReports). */
public final class ScreenshotUtils {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotUtils.class);
    private static final String SCREENSHOT_DIR = "screenshots";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ScreenshotUtils() {
    }

    /** Saves a PNG screenshot under {@code screenshots/}. Returns the file path or null on failure. */
    public static String capture(WebDriver driver, String testName) {
        if (driver == null) {
            log.warn("Driver is null — skipping screenshot");
            return null;
        }

        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            Path dir = Paths.get(SCREENSHOT_DIR);
            Files.createDirectories(dir);
            String fileName = testName + "_" + TIMESTAMP.format(LocalDateTime.now()) + ".png";
            Path dest = dir.resolve(fileName);
            Files.write(dest, png);
            log.info("Screenshot saved: {}", dest.toAbsolutePath());

            return dest.toString();
        } catch (IOException e) {
            log.error("Failed to save screenshot for '{}'", testName, e);
            return null;
        }
    }
}
