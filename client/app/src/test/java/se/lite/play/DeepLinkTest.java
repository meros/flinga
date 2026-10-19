package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** G6: the app's own links parse to one place, and anything else is no link (the app opens as from its icon). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DeepLinkTest {
    private static DeepLink parse(String s) {
        return DeepLink.parse(Uri.parse(s));
    }

    @Test
    public void aChannelLinkCarriesTheId() {
        DeepLink l = parse("flinga://channel/c-1234");
        assertEquals(DeepLink.CHANNEL, l.kind);
        assertEquals("c-1234", l.id);
    }

    @Test
    public void aTitleLinkCarriesTheIdAndWhetherToPlay() {
        DeepLink open = parse("flinga://title/s-6b00061dc059998b");
        assertEquals(DeepLink.TITLE, open.kind);
        assertEquals("s-6b00061dc059998b", open.id);
        assertFalse(open.play);
        assertTrue(parse("flinga://title/m-1e692472e5bfbd60?play=1").play);
        assertFalse("only play=1 plays", parse("flinga://title/m-1?play=0").play);
    }

    @Test
    public void theGuideAndSearchLinks() {
        assertEquals(DeepLink.GUIDE, parse("flinga://guide").kind);
        assertEquals(DeepLink.GUIDE, parse("flinga://guide/").kind);
        DeepLink s = parse("flinga://search?q=Bonde%20s%C3%B6ker%20fru");
        assertEquals(DeepLink.SEARCH, s.kind);
        assertEquals("Bonde söker fru", s.query);
        assertEquals("a bare search opens an empty Sök", "", parse("flinga://search").query);
        assertEquals("", parse("flinga://search?q=%20").query);
    }

    @Test
    public void theSchemeAndHostAreCaseInsensitive() {
        assertEquals(DeepLink.GUIDE, parse("FLINGA://GUIDE").kind);
    }

    @Test
    public void everythingElseIsNoLink() {
        assertNull(DeepLink.parse(null));
        assertNull(parse("https://example.com/channel/x"));
        assertNull(parse("flinga://nowhere"));
        assertNull("no id", parse("flinga://channel"));
        assertNull("no id", parse("flinga://channel/"));
        assertNull("two segments", parse("flinga://title/s-1/extra"));
        assertNull("opaque", parse("flinga:channel/x"));
        assertNull("no host", parse("flinga:///channel/x"));
        assertNull("an id that is far too long", parse("flinga://title/" + "x".repeat(129)));
    }

    @Test
    public void theTitleLinkWatchNextBuildsParsesBackToItself() {
        DeepLink t = DeepLink.parse(DeepLink.title("m-9|x y", true));
        assertEquals("m-9|x y", t.id);
        assertTrue(t.play);
        assertFalse(DeepLink.parse(DeepLink.title("m-9", false)).play);
    }

    @Test
    public void theWatchNextIntentFiresTheTitleLinkAtThisPackageOnly() throws Exception {
        String uri = WatchNext.intentUri("s-1", "se.lite.play");
        Intent i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
        assertEquals(Intent.ACTION_VIEW, i.getAction());
        assertEquals("se.lite.play", i.getPackage());
        DeepLink l = DeepLink.parse(i.getData());
        assertNotNull(l);
        assertEquals("s-1", l.id);
        assertTrue(l.play);
    }
}
