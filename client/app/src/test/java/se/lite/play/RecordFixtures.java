package se.lite.play;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the real Api code against Telia once and saves every response as a fixture.
 * Skipped unless -PrecordPrefs=<path to a copy of the app's shared_prefs/api.xml> is given.
 *
 * The recorder never refreshes or creates tokens, so it cannot log the TV out. Streaming tickets are
 * replaced by a placeholder, because their license headers are session secrets.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecordFixtures {

    @Test
    public void record() throws Exception {
        String prefsPath = System.getProperty("record.prefs", "");
        Assume.assumeTrue("set -PrecordPrefs to record", !prefsPath.isEmpty());

        String xml = Fixtures.read(new File(prefsPath));
        SharedPreferences.Editor ed = RuntimeEnvironment.getApplication()
                .getSharedPreferences("api", Context.MODE_PRIVATE).edit();
        Matcher m = Pattern.compile("name=\"(\\w+)\">([^<]*)<").matcher(xml);
        while (m.find()) ed.putString(m.group(1), android.text.Html.fromHtml(m.group(2), 0).toString());
        ed.commit();

        Http.transport = (method, url, headers, body, contentType) -> {
            if (url.contains("/login/refresh") || url.contains("/device/token")) {
                throw new java.io.IOException("recorder does not touch tokens: " + url);
            }
            Http.Response r = Http.network(method, url, headers, body, contentType);
            String key = Fixtures.key(url, body);
            String out = r.body;
            if (key.equals("ticket")) {
                out = "{\"streams\":[{\"url\":\"http://fixture.invalid/manifest.mpd\",\"packaging\":\"DASH_MP4_CTR\","
                        + "\"drm\":{\"type\":\"WIDEVINE\",\"licenseUrl\":\"http://fixture.invalid/license\",\"headers\":{}}}]}";
            }
            System.out.println("recorded " + key + " HTTP " + r.code + " " + out.length() + " B");
            if (r.ok()) Fixtures.write(Fixtures.file(key), out);
            return r;
        };

        Fixtures.write(new File(Fixtures.DIR, "meta.json"),
                new JSONObject().put("recordedAt", System.currentTimeMillis()).toString());

        Api api = new Api(RuntimeEnvironment.getApplication());
        List<Api.Channel> channels = api.loadChannels();
        api.loadNowNext(channels);
        for (int i = 0; i < Math.min(5, channels.size()); i++) api.guide(channels.get(i));
        api.ticket(channels.get(0).playable);
        api.startDeviceLogin();

        for (String q : new String[] {"bonde", "nyheter", "film"}) {
            List<Api.Asset> hits = api.search(q);
            int series = 0, others = 0;
            for (Api.Asset a : hits) {
                if (a.isSeries && series++ < 2) {
                    List<Api.Season> seasons = api.seasons(a);
                    for (int s = 0; s < Math.min(2, seasons.size()); s++) api.episodes(seasons.get(s));
                } else if (!a.isSeries && others++ < 2) {
                    api.resolve(a);
                }
            }
        }
    }
}
