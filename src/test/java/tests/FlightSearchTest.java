package tests;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.SearchResultsPage;

import java.util.List;

/**
 * Case 1 — Round-trip search + departure-time filter (10:00–18:00).
 * Asserts both departure and arrival are within range, so flights with long
 * durations that arrive after 18:00 are also caught.
 */
public class FlightSearchTest extends BaseTest {

    private static final int MIN_HOUR = 10;
    private static final int MAX_HOUR = 18;

    @DataProvider(name = "flightRoutes")
    public Object[][] flightRoutes() {
        String[] dates1 = futureDates(30, 7);
        String[] dates2 = futureDates(45, 7);
        return new Object[][]{
                {"istanbul", "ankara", dates1[0], dates1[1]},
                {"istanbul", "izmir", dates2[0], dates2[1]},
        };
    }

    @Test(dataProvider = "flightRoutes",
            description = "Search, verify list & route, apply time filter, verify times in range")
    public void testBasicFlightSearchWithTimeFilter(String origin, String destination,
                                                    String departDate, String returnDate) {
        // 1) Land + round-trip search — single search drives all subsequent assertions.
        SearchResultsPage results = homePage
                .open()
                .searchRoundTrip(origin, destination, departDate, returnDate)
                .waitForResults();

        // 2) Results are rendered and non-empty.
        Assert.assertTrue(results.isResultsListDisplayed(),
                "Flight results list should be visible");
        Assert.assertTrue(results.getFlightCount() > 0,
                "There should be at least one flight result");

        // 3) Route integrity — URL reflects the searched origin/destination.
        Assert.assertTrue(results.isRouteMatchingUrl(origin, destination),
                "Search results should match route: " + origin + " → " + destination);

        // 4) Apply the departure-time filter.
        results.filterByDepartureTime(MIN_HOUR, MAX_HOUR);

        // 5) Verify times are present and every leg's departure AND arrival fall in [MIN_HOUR, MAX_HOUR].
        List<String> times = results.getDepartureTimes();
        Assert.assertFalse(times.isEmpty(), "Departure times should be visible after filter");

        Assert.assertTrue(results.areAllFlightsWithinTimeRange(MIN_HOUR, MAX_HOUR),
                "All displayed flights should have departure AND arrival between "
                        + MIN_HOUR + ":00 and " + MAX_HOUR + ":00. Departure times found: " + times);
    }
}
