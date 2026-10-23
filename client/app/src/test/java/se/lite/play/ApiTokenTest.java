package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * E9 (2026-10-23): a data request never waits for ever, and a session token about to expire is replaced before it is
 * used, not after a 401 round trip. A fake clock ({@link Clock#offsetMs}) and a fake transport.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ApiTokenTest {
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private Context app;

    private static final String CHANNELS = "{\"data\":{\"channels\":{\"pageInfo\":{\"hasNextPage\":false},\"channelItems\":[]}}}";

    @Before
    public void setUp() {
        Clock.offsetMs = 0;
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-test").putString("accessToken", "old").putString("refreshToken", "r1").commit();
    }

    @After
    public void tearDown() {
        Http.deadlineMs = 12_000;
        Clock.offsetMs = 0;
    }

    private void serve(String validTo) {
        Http.transport = (method, url, headers, body, type) -> {
            String auth = headers == null ? null : headers.get("Authorization");
            calls.add(url.contains("login/refresh") ? "refresh" : "graphql " + auth);
            if (url.contains("login/refresh")) {
                return new Http.Response(200, "{\"accessToken\":\"new\",\"refreshToken\":\"r2\",\"validTo\":\"" + validTo + "\"}");
            }
            return new Http.Response(200, CHANNELS);
        };
    }

    @Test
    public void aTokenThatExpiresSoonIsRefreshedBeforeTheRequest() throws Exception {
        long now = Clock.now();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().putLong("validTo", now + 60_000).commit(); // 1 min left
        serve("2099-01-01T00:00:00Z");
        new Api(app).loadChannels();
        assertEquals(List.of("refresh", "graphql Bearer new"), calls);
    }

    @Test
    public void aTokenWithTimeLeftIsUsedAsItIs() throws Exception {
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().putLong("validTo", Clock.now() + 3600_000).commit();
        serve("2099-01-01T00:00:00Z");
        new Api(app).loadChannels();
        assertEquals(List.of("graphql Bearer old"), calls);
    }

    @Test
    public void theRefreshKeepsTheNewExpiry() throws Exception {
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().putLong("validTo", 1).commit(); // long expired
        serve("2099-01-01T00:00:00Z");
        Api api = new Api(app);
        api.loadChannels();
        long until = app.getSharedPreferences("api", Context.MODE_PRIVATE).getLong("validTo", 0);
        assertEquals(Api.validUntil("2099-01-01T00:00:00Z", null), until);
        calls.clear();
        api.loadChannels();
        assertEquals("the new token is not refreshed again", List.of("graphql Bearer new"), calls);
    }

    @Test
    public void theExpiryIsReadFromTheAnswerOrTheToken() {
        assertEquals(1_800_000_000_000L, Api.validUntil("2027-01-15T08:00:00Z", null));
        assertEquals(1_800_000_000_000L, Api.validUntil("2027-01-15T09:00:00.000+01:00", null));
        assertEquals(1_800_000_000_000L, Api.validUntil("1800000000", null));
        assertEquals(1_800_000_000_000L, Api.validUntil("1800000000000", null));
        // A JWT whose payload is {"exp":1800000000}.
        String payload = android.util.Base64.encodeToString("{\"exp\":1800000000}".getBytes(),
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        assertEquals(1_800_000_000_000L, Api.validUntil("", "h." + payload + ".s"));
        assertEquals("unknown: no refresh ahead, a 401 still refreshes", 0, Api.validUntil("", "opaque"));
    }

    @Test
    public void aQueryThatNeverAnswersFailsAtTheDeadline() {
        Http.deadlineMs = 300;
        Http.transport = (method, url, headers, body, type) -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Http.Response(200, CHANNELS);
        };
        long t0 = System.nanoTime();
        try {
            new Api(app).loadChannels();
            fail("a query with no answer must fail");
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue("it failed at the deadline, after " + ms + " ms", ms < 3_000);
            assertTrue(String.valueOf(e.getMessage()), String.valueOf(e.getMessage()).contains("no answer"));
        }
    }
}
