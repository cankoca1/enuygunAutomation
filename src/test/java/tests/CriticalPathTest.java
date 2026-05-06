package tests;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.SearchResultsPage;

import java.util.List;

/**
 * Case 3 — Critical user path (single E2E flow per data row).
 *
 * <h2>Documented critical path on enuygun.com</h2>
 * <p>
 * The most critical journey for a flight-booking site is the conversion
 * funnel that takes a user from intent to a booking-able state. On enuygun's
 * round-trip flow this requires four UI actions after the search:
 * </p>
 * <ol>
 *   <li><b>Land</b> on the homepage.</li>
 *   <li><b>Search</b> for a round-trip flight (origin, destination, dates).</li>
 *   <li><b>Browse</b> the results — every visible flight card must expose
 *       airline name and a positive price.</li>
 *   <li><b>Select departure</b> — click "Seç" on the first departure card,
 *       then click "Seç ve ilerle" on the same card to confirm.</li>
 *   <li><b>Select return</b> — click "Seç" on the first return card, then
 *       click "Seç ve ilerle" to confirm.</li>
 *   <li><b>Proceed</b> to the booking step — verified by URL change or by
 *       the booking container becoming visible.</li>
 * </ol>
 * 
 * search → browse → validate cards → select departure + return → verify booking transition.
 */
public class CriticalPathTest extends BaseTest {

    @DataProvider(name = "criticalPathData")
    public Object[][] criticalPathData() {
        String[] dates = futureDates(30, 7);
        return new Object[][]{
                {"istanbul", "ankara", dates[0], dates[1]},
        };
    }

    @Test(dataProvider = "criticalPathData",
            description = "E2E critical path: search → browse → validate → select → verify transition")
    public void testCriticalUserJourney(String origin, String destination,
                                        String departDate, String returnDate) {
        // 1) Land + search — single round-trip call drives the rest of the test.
        SearchResultsPage results = homePage
                .open()
                .searchRoundTrip(origin, destination, departDate, returnDate)
                .waitForResults();

        // 2) Browse — results page is rendered and non-empty.
        Assert.assertTrue(results.isResultsListDisplayed(),
                "Flight results list should be visible after search");

        int flightCount = results.getFlightCount();
        Assert.assertTrue(flightCount > 0,
                "Results should contain at least one flight, found: " + flightCount);

        // 3) Route integrity — URL must reflect the searched origin/destination.
        Assert.assertTrue(results.isRouteMatchingUrl(origin, destination),
                "Results should match route: " + origin + " → " + destination);

        // 4) Card data quality — airlines and prices must be populated and positive.
        List<String> airlines = results.getAirlineNames();
        List<Double> prices = results.getPrices();

        Assert.assertFalse(airlines.isEmpty(), "Every card should expose airline name");
        Assert.assertFalse(prices.isEmpty(),   "Every card should expose price");
        Assert.assertTrue(prices.stream().allMatch(p -> p > 0),
                "All prices must be positive, found: " + prices);

        double firstPrice = results.getFirstFlightPrice();
        Assert.assertTrue(firstPrice > 0,
                "First flight price must be positive (was: " + firstPrice + ")");

        // 5) Selection — capture URL before walking the round-trip selection chain.
        String urlBefore = results.getCurrentUrl();
        results.selectFirstFlight()
               .completeRoundTripSelection();

        // 6) Transition — page must move to the booking step (URL change or container visible).
        boolean transitioned = results.hasPageTransitioned(urlBefore)
                || results.isBookingStepDisplayed();

        Assert.assertTrue(transitioned,
                "Round-trip selection should transition to booking step "
                        + "(URL change or booking container visible).");
    }
}
