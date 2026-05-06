package utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Generates future dates in dd.MM.yyyy format so test data never expires. */
public final class DateUtils {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private DateUtils() {
    }

    public static String[] roundTripDates(int departureDaysFromNow, int tripLengthDays) {
        LocalDate depart = LocalDate.now().plusDays(departureDaysFromNow);
        LocalDate ret = depart.plusDays(tripLengthDays);
        return new String[]{depart.format(FMT), ret.format(FMT)};
    }
}
