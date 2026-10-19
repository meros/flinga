package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.SearchManager;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** G6: what the system and the Assistant hand over, and when a spoken "spela X" may play without asking. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VoiceSearchTest {
    private static Intent intent(String action, String query) {
        Intent i = new Intent(action);
        if (query != null) i.putExtra(SearchManager.QUERY, query);
        return i;
    }

    @Test
    public void searchAndAssistOpenSokWithTheQueryAndNeverPlay() {
        VoiceSearch.Request r = VoiceSearch.parse(intent(Intent.ACTION_SEARCH, "  Bron  "));
        assertEquals("Bron", r.query);
        assertFalse(r.play);
        assertEquals("Bron", VoiceSearch.parse(intent(Intent.ACTION_ASSIST, "Bron")).query);
        assertEquals("a search without a query opens an empty Sök", "", VoiceSearch.parse(intent(Intent.ACTION_SEARCH, null)).query);
    }

    @Test
    public void playFromSearchPlaysWhenItHasAQuery() {
        VoiceSearch.Request r = VoiceSearch.parse(intent(VoiceSearch.ACTION_PLAY_FROM_SEARCH, "Bron"));
        assertEquals("Bron", r.query);
        assertTrue(r.play);
        Intent byTitle = new Intent(VoiceSearch.ACTION_PLAY_FROM_SEARCH).putExtra("android.intent.extra.title", "Rapport");
        assertEquals("the title extra stands in for an empty query", "Rapport", VoiceSearch.parse(byTitle).query);
        VoiceSearch.Request none = VoiceSearch.parse(intent(VoiceSearch.ACTION_PLAY_FROM_SEARCH, ""));
        assertFalse("nothing to play: Sök opens bare", none.play);
    }

    @Test
    public void otherIntentsAreNoSearch() {
        assertNull(VoiceSearch.parse(null));
        assertNull(VoiceSearch.parse(new Intent()));
        assertNull(VoiceSearch.parse(intent(Intent.ACTION_MAIN, "Bron")));
        assertNull(VoiceSearch.parse(intent(Intent.ACTION_VIEW, "Bron")));
    }

    @Test
    public void namesAreComparedWithoutCaseAccentsSpacesAndPunctuation() {
        assertEquals(VoiceSearch.norm("SVT 1"), VoiceSearch.norm("svt1"));
        assertEquals(VoiceSearch.norm("Svt-1"), VoiceSearch.norm("SVT1"));
        assertEquals("bron", VoiceSearch.norm("Brön"));
        assertEquals("", VoiceSearch.norm(null));
    }

    private static Api.Channel channel(String name) {
        Api.Channel c = new Api.Channel();
        c.id = "c-" + name;
        c.name = name;
        return c;
    }

    private static Api.Asset asset(String id, String title) {
        Api.Asset a = new Api.Asset();
        a.id = id;
        a.title = title;
        return a;
    }

    @Test
    public void aChannelNameIsCertainWhenItIsTheOnlyOne() {
        List<Api.Channel> all = Arrays.asList(channel("SVT1"), channel("SVT2"), channel("TV4"));
        assertSame(all.get(0), VoiceSearch.channel("SVT 1", all));
        assertSame(all.get(2), VoiceSearch.channel("tv4", all));
        assertNull("a part of a name is not the name", VoiceSearch.channel("SVT", all));
        assertNull(VoiceSearch.channel("", all));
        assertNull("two channels of one name: ask", VoiceSearch.channel("TV4", Arrays.asList(channel("TV4"), channel("TV 4"))));
    }

    @Test
    public void aResultIsCertainWhenItsTitleIsTheQueryAndNoOtherIs() {
        List<Api.Asset> hits = Arrays.asList(asset("s-1", "Bron"), asset("m-2", "Bron: Making of"), asset("m-3", "Broarna"));
        assertSame(hits.get(0), VoiceSearch.asset("bron", hits));
        assertNull("no title is the query", VoiceSearch.asset("bro", hits));
        List<Api.Asset> twins = new ArrayList<>(hits);
        twins.add(asset("m-9", "BRON"));
        assertNull("the series and a film of one title: Sök shows them", VoiceSearch.asset("Bron", twins));
        assertNull(VoiceSearch.asset("", hits));
        assertNotNull(VoiceSearch.asset("Bron: Making of", hits));
    }
}
