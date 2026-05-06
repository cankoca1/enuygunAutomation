package tests;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.SearchResultsPage;

import java.util.List;

/**
 * Case 2 — Filter by THY + time, sort by cheapest, verify all flights are THY
 * and prices are in ascending order.
 */
public class PriceSortingTest extends BaseTest {

    private static final int MIN_HOUR = 10;
    private static final int MAX_HOUR = 18;
    private static final String THY_NAME = "Türk Hava Yolları";

    @DataProvider(name = "thyRoutes")
    public Object[][] thyRoutes() {
        String[] dates = futureDates(30, 7);
        return new Object[][]{
                {"istanbul", "ankara", dates[0], dates[1]},
        };
    }

    @Test(dataProvider = "thyRoutes",
            description = "Search, apply time + THY filters, sort by cheapest, verify airline and price order")
    public void testTurkishAirlinesPriceSorting(String origin, String destination,
                                                String departDate, String returnDate) {
        // 1) Land + round-trip search — single search drives all subsequent assertions.
        SearchResultsPage results = homePage
                .open()
                .searchRoundTrip(origin, destination, departDate, returnDate)
                .waitForResults();

        // 2) Pre-filter sanity — results page rendered and non-empty.
        Assert.assertTrue(results.isResultsListDisplayed(),
                "Flight results list should be visible");
        Assert.assertTrue(results.getFlightCount() > 0,
                "There should be at least one flight result before filtering");

        // 3) Apply filters: departure-time range first, then Turkish Airlines.
        results.filterByDepartureTime(MIN_HOUR, MAX_HOUR);
        results.filterByTurkishAirlines();

        // 4) After filters there must still be flights to sort.
        int countAfterFilters = results.getFlightCount();
        Assert.assertTrue(countAfterFilters > 0,
                "There should be at least one THY flight after filters");

        // 5) Sort by cheapest.
        results.sortByCheapest();

        // 6) Verify every visible flight is THY.
        Assert.assertTrue(results.areAllFlightsFromAirline(THY_NAME),
                "All displayed flights should belong to " + THY_NAME);

        // 7) Verify prices are sorted ascending.
        List<Double> prices = results.getPrices();
        Assert.assertTrue(prices.size() >= 2,
                "Need at least 2 prices to validate sort order, found: " + prices.size());

        Assert.assertTrue(results.arePricesSortedAscending(),
                "Prices should be sorted in ascending order, but found: " + prices);
    }
}
