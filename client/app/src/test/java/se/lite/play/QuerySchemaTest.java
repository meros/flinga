package se.lite.play;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * Fields the server's schema rejects take the whole query down with it (found on the TV, 2026-10-18: every Serie
 * said "Kunde inte hämta listan" because of one field). The fixtures never validate a query against the schema, so
 * this pins what the official app's own queries show to be wrong.
 */
public class QuerySchemaTest {
    @Test
    public void theSeriesQueryAsksForNoFieldTheSeriesTypeLacks() {
        // The official app's MobileSeriesDetailsItem (on Series) has no yearProduction; Movie and SportEvent do.
        assertFalse("Series has no yearProduction: the server rejects the query with HTTP 400",
                Api.SERIES_QUERY.contains("yearProduction"));
    }
}
