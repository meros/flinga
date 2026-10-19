package se.lite.play;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;

/**
 * Every GraphQL query and mutation in Api has balanced braces. The guide query with one closing
 * brace too many passed every replay test (the fixtures are served regardless) and blanked Tablå
 * on the TV on 2026-10-17 with GRAPHQL_PARSE_FAILED on every channel. This test runs at build
 * time, before anything reaches the server.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QueryShapeTest {
    @Test
    public void everyQueryHasBalancedBraces() throws Exception {
        int checked = 0;
        for (Field f : Api.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
            if (!f.getName().endsWith("_QUERY") && !f.getName().equals("PLAYBACK_FRAGMENT")
                    && !f.getName().equals("SCHEDULE_RECORDING")) continue;
            f.setAccessible(true);
            String q = (String) f.get(null);
            int depth = 0;
            for (int i = 0; i < q.length(); i++) {
                char c = q.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth < 0) throw new AssertionError(
                            f.getName() + " closes a brace that was never opened at index " + i + ": " + q.substring(Math.max(0, i - 40), i + 5));
                }
            }
            assertEquals(f.getName() + " has unbalanced braces: " + q, 0, depth);
            checked++;
        }
        assertEquals("the query list changed: update this test's expectations", 11, checked);
    }
}
