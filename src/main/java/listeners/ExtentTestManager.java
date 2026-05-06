package listeners;

import com.aventstack.extentreports.ExtentTest;

/** Thread-local holder for the current {@link ExtentTest}, decoupling pages from the listener. */
public final class ExtentTestManager {

    private static final ThreadLocal<ExtentTest> TEST = new ThreadLocal<>();

    private ExtentTestManager() { }

    public static void set(ExtentTest test) {
        TEST.set(test);
    }

    public static ExtentTest get() {
        return TEST.get();
    }

    public static void clear() {
        TEST.remove();
    }

    /** Logs an info step to the current node; no-op if no node is active. */
    public static void step(String message) {
        ExtentTest t = TEST.get();
        if (t != null) {
            t.info(message);
        }
    }
}
