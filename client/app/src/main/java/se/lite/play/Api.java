package se.lite.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Telia Play endpoints this client needs, and nothing else: login, the channel list,
 * and the streaming ticket. Endpoint values come from the official app 9.23.2
 * (Nk/a.java:180, StreamingTicketSecureResourceV2.buildPayload).
 */
final class Api {
    static final String COUNTRY = "SE";
    static final String CLIENT_NAME = "androidtv";
    static final String CLIENT_VERSION = "9.23.2";
    static final String BRAND = "TELIA";
    static final String LOGIN_BASE = "https://logingateway.teliaplay.se/logingateway/";
    static final String STREAMING_BASE = "https://streaminggateway.clientapi-prod.live.tv.telia.net/";
    static final String GRAPHQL = "https://graphql-telia.t6a.net/graphql/";

    /** Anything the streaming gateway can play: a channel, a past broadcast, an episode, a movie. */
    static final class Playable {
        String title, videoId, videoIdType, watchMode, accessControl;
        long startPositionMs;
        /** For catch-up and start-over of a channel's programme (null for on-demand from search). */
        Channel channel;
        Program programme;
        /** On-demand from the catalogue: the media id ("m-…") whose progress Telia keeps; null otherwise. */
        String mediaId;
        /** The series of an episode (for Min lista and the next episode); null for films and channels. */
        String seriesId, seriesTitle;
        int season;
        long durationMs;
        /** "Spela från början": ignore the saved position once. */
        boolean fromStart;
    }

    static final class Channel {
        String id, name;
        /** Logo artwork for dark backgrounds (icons2.compact.dark), URL-decoded; null when missing. */
        String logoUrl;
        /** Logo artwork for light backgrounds (icons2.compact.light); null when missing. */
        String logoLightUrl;
        int number;
        Playable playable;
        Program now, next;
    }

    static final class Program {
        String id, title;
        long startMs, endMs;
        Playable catchup, startOver;
        /** Start of the actual broadcast (the linear item), which can differ from startMs by minutes. */
        long broadcastStartMs;
        /** End of the actual broadcast; 0 when unknown. */
        long broadcastEndMs;
    }

    static final class Asset {
        String id, title, kindLabel = "";
        /** Search hits: "Serie", "Film", "Avsnitt", "Sport", and the provider from the label ("SVT Play"). */
        String kind = "", provider = "";
        /** Episodes: the number in the season (0 when unknown) and the bare episode title. */
        int number;
        String name = "";
        boolean isSeries;
        /** Telia's saved position (ms) and the nominal duration; 0 when unknown or not started. */
        long positionMs, durationMs;
        /** Telia's percent watched (rounded to 5) and minutes left; -1 minutes when not started. */
        int percent, minutesLeft = -1;
        /** Episodes: the series and the season number (0 when unknown). */
        String seriesId, seriesTitle;
        int season;
        /** In Min lista; null when the response did not say. */
        Boolean favorite;
        /** Series: the episode Telia suggests (resume, else the next unwatched); null when none. */
        String suggestedId;
        /**
         * F16: the image source from the API (URL-encoded, scaled through {@link Images#scaled}): the search
         * poster (a 16:9 card with the title art), the series backdrop, or the episode screenshot. Null when none.
         */
        String imageUrl;
        /** Series: the backdrop for the hero, its muted background colour (0 = unknown, the hero placeholder). */
        String heroUrl;
        int heroColor;
        /** Series: genre ("Reality-TV") and age rating ("7+"); empty when unknown. */
        String genre = "", ageRating = "";
        /** Series and films: the description ("Korta svenska rön …"); null when the response had none. */
        String description;
        /** IMDb rating as shown ("7.9"), the year of production ("2023"), whether it can be recorded. */
        String imdbRating = "", yearProduction = "";
        Boolean recordable;
        /** True when `playable` is final; search hits resolve it on selection. */
        boolean resolved;
        Playable playable;
    }

    static final class Season {
        /** title is the readable season name ("Säsong 2"); episodeCount is -1 when unknown. */
        String id, title;
        int episodeCount = -1;
    }

    static final class Stream {
        String manifestUrl, licenseUrl;
        Map<String, String> licenseHeaders = new HashMap<>();
        /** The ticket's own session id (sent again when the ticket is released) and what it plays. */
        String sessionId;
        Playable playable;
        /** tracking.PLAYBACKTRACKING of the ticket: progress events go there; null when the ticket has none. */
        String trackingSessionId;
        long progressIntervalMs;
        /**
         * The ticket's timeline (skip intro and recap, official app's SGTimelineItem): items of type
         * OPENING_CREDITS, CLOSING_CREDITS or RECAP, each with offset and duration in ms. Empty when
         * the ticket carries none (every catch-up and recording measured so far).
         */
        List<TimelineItem> timeLine = new ArrayList<>();
    }

    /** One entry of a ticket's timeLine: a span the viewer can hop over. */
    static final class TimelineItem {
        static final String OPENING = "OPENING_CREDITS", CLOSING = "CLOSING_CREDITS", RECAP = "RECAP";
        final String type;
        final long offsetMs, durationMs;

        TimelineItem(String type, long offsetMs, long durationMs) {
            this.type = type;
            this.offsetMs = offsetMs;
            this.durationMs = durationMs;
        }
    }

    /** One poster of a Telia panel (Fortsätt titta, Min lista). */
    static final class Saved {
        /** The series ("s-…") or the media ("m-…") the poster links to. */
        String id, title, provider = "";
        boolean series;
        /** The media to play: the episode in progress or next for a series, the film itself otherwise. */
        String mediaId;
        /** The poster's image source (URL-encoded, scaled through {@link Images#scaled}); null when the list gave none. */
        String imageUrl;
        long positionMs;
        int percent;
        /** Minutes left as Telia rounds them ("12 min återstår" gives 12); -1 when not started. */
        int minutesLeft = -1;
    }

    static final class DeviceCode {
        String deviceCode, userCode, verificationUri;
        int intervalSec = 5, expiresInSec = 600;
    }

    // One fragment-free query: only the fields this client reads.
    private static final String CHANNELS_QUERY =
            "query getUserChannels($offset: Int!, $limit: Int!) { channels(offset: $offset, limit: $limit) {"
                    + " pageInfo { hasNextPage nextPageOffset } channelItems { id channelNumber name"
                    + " icons2 { compact { dark { source } light { source } } }"
                    + " playback { play { playbackSpec { accessControl videoId videoIdType watchMode } } } } } }";

    private final SharedPreferences prefs;
    private final String deviceId;
    private final String bootId = "b-" + UUID.randomUUID();

    Api(Context ctx) {
        prefs = ctx.getSharedPreferences("api", Context.MODE_PRIVATE);
        String id = prefs.getString("deviceId", null);
        if (id == null) {
            id = "d-" + UUID.randomUUID();
            prefs.edit().putString("deviceId", id).apply();
        }
        deviceId = id;
    }

    // ---- Channels -------------------------------------------------------------------------

    List<Channel> loadChannels() throws Exception {
        List<Channel> out = new ArrayList<>();
        int offset = 0;
        while (true) {
            JSONObject vars = new JSONObject().put("offset", offset).put("limit", 100);
            JSONObject channels = graphql("getUserChannels", CHANNELS_QUERY, vars).getJSONObject("channels");
            JSONArray items = channels.getJSONArray("channelItems");
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                JSONObject play = optPath(it, "playback", "play", "playbackSpec");
                if (play == null) continue; // not in the subscription
                Channel c = new Channel();
                c.id = it.getString("id");
                c.name = it.optString("name");
                c.number = it.optInt("channelNumber");
                c.playable = playable(play, c.name);
                c.logoUrl = logoSource(optPath(it, "icons2", "compact", "dark"));
                c.logoLightUrl = logoSource(optPath(it, "icons2", "compact", "light"));
                out.add(c);
            }
            JSONObject page = channels.getJSONObject("pageInfo");
            if (!page.optBoolean("hasNextPage")) break;
            offset = page.getInt("nextPageOffset");
        }
        return out;
    }

    // ---- Streaming ticket -----------------------------------------------------------------

    Stream ticket(Playable ch) throws Exception {
        JSONObject tvClient = new JSONObject()
                .put("name", CLIENT_NAME)
                .put("version", CLIENT_VERSION)
                .put("os", "Android")
                .put("osVersion", Build.VERSION.RELEASE)
                .put("vendor", Build.MANUFACTURER)
                .put("vendorModel", Build.MODEL);
        JSONObject device = new JSONObject()
                .put("tvClient", tvClient)
                .put("deviceId", deviceId)
                .put("packagings", new JSONArray().put("DASH_MP4_CTR"))
                .put("drmType", "WIDEVINE")
                .put("capabilities", new JSONArray())
                .put("screen", new JSONObject()
                        .put("width", Resources.getSystem().getDisplayMetrics().widthPixels)
                        .put("height", Resources.getSystem().getDisplayMetrics().heightPixels));
        String sessionId = "s-" + UUID.randomUUID();
        JSONObject body = new JSONObject()
                .put("sessionId", sessionId)
                .put("whiteLabelBrand", BRAND)
                .put("watchMode", ch.watchMode)
                .put("accessControl", ch.accessControl)
                .put("device", device)
                .put("preferences", new JSONObject()
                        .put("audioLanguage", new JSONArray())
                        .put("accessibility", new JSONArray()));

        String url = STREAMING_BASE + "streaminggateway/rest/secure/v2/streamingticket/"
                + ch.videoIdType + "/" + ch.videoId;
        Map<String, String> h = authHeaders();
        h.put("tv-client-boot-id", bootId);
        Http.Response r = Http.request("POST", url, h, body.toString(), "application/json;charset=UTF-8");
        if (r.code == 401 && refresh()) {
            h.putAll(authHeaders());
            r = Http.request("POST", url, h, body.toString(), "application/json;charset=UTF-8");
        }
        if (!r.ok()) throw new Exception("ticket HTTP " + r.code + ": " + trim(r.body));

        JSONObject ticket = new JSONObject(r.body);
        JSONArray streams = ticket.getJSONArray("streams");
        for (int i = 0; i < streams.length(); i++) {
            JSONObject s = streams.getJSONObject(i);
            JSONObject drm = s.optJSONObject("drm");
            if (drm != null && !"WIDEVINE".equalsIgnoreCase(drm.optString("type"))) continue;
            Stream out = new Stream();
            out.manifestUrl = s.getString("url");
            out.sessionId = sessionId;
            out.playable = ch;
            JSONObject track = optPath(ticket, "tracking", "PLAYBACKTRACKING");
            if (track != null && !track.optString("sessionId").isEmpty()) {
                out.trackingSessionId = track.optString("sessionId");
                out.progressIntervalMs = track.optLong("progressInterval");
            }
            JSONArray tl = ticket.optJSONArray("timeLine");
            out.timeLine = parseTimeline(tl);
            if (!out.timeLine.isEmpty()) {
                StringBuilder sb = new StringBuilder("timeLine of \"" + ch.title + "\": " + out.timeLine.size());
                for (TimelineItem t : out.timeLine) sb.append(" ").append(t.type).append("@").append(t.offsetMs).append("+").append(t.durationMs);
                android.util.Log.i("TeliaLite", sb.toString());
            }
            if (drm != null) {
                out.licenseUrl = drm.optString("licenseUrl");
                JSONObject headers = drm.optJSONObject("headers");
                if (headers != null) {
                    for (Iterator<String> k = headers.keys(); k.hasNext(); ) {
                        String key = k.next();
                        out.licenseHeaders.put(key, headers.getString(key));
                    }
                }
            }
            return out;
        }
        throw new Exception("no Widevine DASH stream in ticket");
    }

    /** The ticket's timeLine: the skip-intro/recap spans, type and offset/duration (SGTimelineItem's fields). */
    static List<TimelineItem> parseTimeline(JSONArray tl) {
        List<TimelineItem> out = new ArrayList<>();
        if (tl == null) return out;
        for (int k = 0; k < tl.length(); k++) {
            JSONObject it = tl.optJSONObject(k);
            if (it == null) continue;
            String type = it.optString("type", "");
            if (type.isEmpty()) continue;
            out.add(new TimelineItem(type, it.optLong("offset", 0), it.optLong("duration", 0)));
        }
        return out;
    }

    // ---- GraphQL --------------------------------------------------------------------------

    private JSONObject graphql(String op, String query, JSONObject vars) throws Exception {
        String body = new JSONObject().put("operationName", op).put("query", query).put("variables", vars).toString();
        Http.Response r = Http.request("POST", GRAPHQL, graphqlHeaders(), body, "application/json");
        if (r.code == 401 && refresh()) r = Http.request("POST", GRAPHQL, graphqlHeaders(), body, "application/json");
        if (!r.ok()) throw new Exception(op + " HTTP " + r.code + ": " + trim(r.body));
        JSONObject json = new JSONObject(r.body);
        if (json.has("errors") && json.optJSONObject("data") == null) {
            throw new Exception(op + ": " + trim(json.getJSONArray("errors").toString()));
        }
        return json.getJSONObject("data");
    }

    private Map<String, String> graphqlHeaders() {
        Map<String, String> h = authHeaders();
        // The headers of the app's GraphQL interceptor (p372ri/b.java).
        h.put("x-country", COUNTRY);
        h.put("tv-client-name", CLIENT_NAME);
        h.put("tv-client-version", CLIENT_VERSION);
        h.put("tv-client-boot-id", bootId);
        h.put("tv-client-os-name", "Android");
        h.put("tv-client-os-version", Build.VERSION.RELEASE);
        h.put("tv-client-vendor", Build.MANUFACTURER);
        h.put("tv-client-vendor-model", Build.MODEL);
        h.put("tv-client-tz", java.util.TimeZone.getDefault().getID());
        return h;
    }

    // ---- Auth -----------------------------------------------------------------------------

    boolean isLoggedIn() {
        return prefs.getString("refreshToken", null) != null;
    }

    private Map<String, String> authHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + prefs.getString("accessToken", ""));
        return h;
    }

    // OAuth device-code flow (LoginApi.fetchActivationCode / fetchDeviceAccessToken). The server
    // answers HTTP 500 for deviceType ANDROIDTV; WEB is what the app sends on refresh too.
    DeviceCode startDeviceLogin() throws Exception {
        String url = LOGIN_BASE + "rest/v1/device/authorization?deviceId=" + deviceId
                + "&deviceType=WEB&whiteLabelBrand=Telia";
        Map<String, String> h = new HashMap<>();
        h.put("X-Country", COUNTRY);
        h.put("tv-client-name", CLIENT_NAME);
        Http.Response r = Http.request("GET", url, h, null, null);
        if (!r.ok()) throw new Exception("device code HTTP " + r.code + ": " + trim(r.body));
        JSONObject j = new JSONObject(r.body);
        DeviceCode dc = new DeviceCode();
        dc.deviceCode = j.getString("deviceCode");
        dc.userCode = j.getString("userCode");
        dc.verificationUri = j.optString("verificationUriComplete", j.optString("verificationUri"));
        dc.intervalSec = Math.max(2, j.optInt("interval", 5));
        dc.expiresInSec = j.optInt("expiresIn", 600);
        return dc;
    }

    /** Returns true once the user has approved the code; false while it is still pending. */
    boolean pollDeviceLogin(DeviceCode dc) throws Exception {
        Map<String, String> h = new HashMap<>();
        h.put("X-Country", COUNTRY);
        h.put("Accept", "application/json");
        String body = "deviceCode=" + java.net.URLEncoder.encode(dc.deviceCode, "UTF-8");
        Http.Response r = Http.request("POST", LOGIN_BASE + "rest/v1/device/token", h, body,
                "application/x-www-form-urlencoded");
        android.util.Log.i("TeliaLite", "device/token HTTP " + r.code + " " + trim(r.body));
        if (!r.ok()) return false;
        saveSession(new JSONObject(r.body));
        return true;
    }

    // LoginApi.refreshToken: POST rest/v1/login/refresh (p086di/g.java).
    synchronized boolean refresh() {
        try {
            JSONObject body = new JSONObject()
                    .put("refreshToken", prefs.getString("refreshToken", ""))
                    .put("deviceId", deviceId)
                    .put("deviceType", "WEB");
            Map<String, String> h = new HashMap<>();
            h.put("tv-client-name", CLIENT_NAME);
            h.put("Accept", "application/json");
            h.put("X-Configuration", "WEB");
            Http.Response r = Http.request("POST", LOGIN_BASE + "rest/v1/login/refresh", h, body.toString(),
                    "application/json");
            if (!r.ok()) {
                android.util.Log.w("TeliaLite", "refresh HTTP " + r.code + " " + trim(r.body));
                return false;
            }
            saveSession(new JSONObject(r.body));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void saveSession(JSONObject s) throws Exception {
        prefs.edit()
                .putString("accessToken", s.getString("accessToken"))
                .putString("refreshToken", s.getString("refreshToken"))
                .apply();
    }

    // ---- Helpers --------------------------------------------------------------------------

    /** Builds a Playable from a GraphQL PlaybackSpec object; null when the spec is missing. */
    static Playable playable(JSONObject spec, String title) {
        if (spec == null || spec.optString("videoId").isEmpty()) return null;
        Playable p = new Playable();
        p.title = title;
        p.videoId = spec.optString("videoId");
        p.videoIdType = spec.optString("videoIdType");
        p.watchMode = spec.optString("watchMode");
        p.accessControl = spec.optString("accessControl");
        return p;
    }

    // ---- Guide, search, series (queries follow from the static analysis) ------------------

    // All queries below were tested against the server on 2026-10-15. Field names come from the
    // official app's query texts (getUserChannels, getSingleChannelMetadata, mobileSearch,
    // getSeries, getPlayerEpisodesForSeason, getMediaPlayback), cut down to what this client reads.

    private static final String SPEC = "playbackSpec { videoId videoIdType watchMode accessControl }";
    private static final String PLAYBACK_FRAGMENT = " fragment P on Playback { play {"
            + " subscription { item { " + SPEC + " } }"
            + " rental { item { " + SPEC + " } }"
            + " est { item { " + SPEC + " } }"
            + " linear { item { startTime { timestamp } endTime { timestamp } " + SPEC
            + " startover { " + SPEC + " } } }"
            // NPVR (recordings, official app's Recording fragment): a recorded programme plays like catch-up.
            // The two braces after npvr close play and the fragment: the error that blanked Tablå on the
            // TV (2026-10-17) was one closing brace too many here.
            + " npvr { item { " + SPEC + " } startover { " + SPEC + " } } } }";

    private static final String NOW_NEXT_QUERY =
            "query nowNext($offset: Int!, $limit: Int!, $from: Timestamp!, $to: Timestamp!) {"
                    + " channels(offset: $offset, limit: $limit) { pageInfo { hasNextPage nextPageOffset }"
                    + " channelItems { id programsByTime(input: { from: $from to: $to includeStartedBeforeFrom: true }) {"
                    + " programItems { id title startTime { timestamp } endTime { timestamp } } } } } }";

    private static final String GUIDE_QUERY =
            "query guide($channelId: String!, $from: Timestamp!, $to: Timestamp!) { channel(id: $channelId) {"
                    + " programsByTime(input: { from: $from to: $to includeStartedBeforeFrom: true }) {"
                    + " programItems { id title startTime { timestamp } endTime { timestamp } media { __typename"
                    + " ... on Episode { id playback { ...P } } ... on Movie { id playback { ...P } }"
                    + " ... on SportEvent { id playback { ...P } } } } } } }" + PLAYBACK_FRAGMENT;

    private static final String LINEAR_ONLY = " fragment L on Playback { play { linear { item {"
            + " startTime { timestamp } endTime { timestamp } startover { " + SPEC + " } } } } }";

    private static final String CURRENT_QUERY =
            "query current($channelId: String!, $from: Timestamp!, $to: Timestamp!) { channel(id: $channelId) {"
                    + " programsByTime(input: { from: $from to: $to includeStartedBeforeFrom: true }) {"
                    + " programItems { id title startTime { timestamp } endTime { timestamp } media { __typename"
                    + " ... on Episode { playback { ...L } } ... on Movie { playback { ...L } }"
                    + " ... on SportEvent { playback { ...L } } } } } } }" + LINEAR_ONLY;

    private static final String SEARCH_QUERY =
            "query search($query: String!, $limit: Int!) {"
                    + " search2(input: { limit: $limit q: $query searchType: { rentals: NONE } }) {"
                    + " posters { a11y { contentLabel } image { source } link { link3 { __typename to } } } } }";

    // userData.favorite is Min lista; suggestedEpisode is Telia's own "what to play next" for the series
    // (getSeries / SuggestedEpisode in the app). Verified 2026-10-15: Storage Wars suggests S13 A11, the
    // episode its Fortsätt titta poster points at.
    static final String SERIES_QUERY =
            "query series($id: String!) { series(id: $id) { id title description userData { favorite } suggestedEpisode { id }"
                    // F16, from the app's SeriesDetailsItem: backdrop16x9 is the clean key art (showcard16x9
                    // has the provider logo composed in), muteBgColor its dark background colour.
                    + " images { backdrop16x9 { source colors { muteBgColor { hex } } } } genre"
                    + " ageRating { content { __typename ... on MetadataItemString { text } } }"
                    // No yearProduction: Series has no such field (the server answers 400 and the whole query fails;
                    // the official app's MobileSeriesDetailsItem has none either, only Movie and SportEvent do).
                    + " ratings { imdb { readableScore } } isRecordable"
                    + " seasonLinks { items { id seasonNumber { number readable } numberOfEpisodes { number } } } } }";

    // userData.progress.position is in milliseconds (605002 = 10:05 with "12 min återstår" of a 22 min episode).
    private static final String EPISODES_QUERY =
            "query episodes($id: String!) { season(seasonId: $id) {"
                    + " episodes2(input: { limit: 300 offset: 0 }) { episodeList { episode {"
                    + " id title episodeNumber { number } duration { seconds } userData { progress { position percent timeLeft } }"
                    + " images { screenshot16x9 { source } }" // F16, from the app's MediaEpisode
                    + " playback { ...P } } } } } }" + PLAYBACK_FRAGMENT;

    private static final String MEDIA_QUERY =
            "query media($id: String!) { media(id: $id) { __typename"
                    + " ... on Movie { id title duration { seconds } userData { favorite progress { position percent timeLeft } }"
                    + " playback { ...P } }"
                    + " ... on Episode { id title duration { seconds } seasonNumber { number } episodeNumber { number }"
                    + " series { id title } userData { favorite progress { position percent timeLeft } } playback { ...P } }"
                    + " ... on SportEvent { id title playback { ...P } } } }" + PLAYBACK_FRAGMENT;

    // The app's own panels (p336pm/e.java, p370rg/c.java): "known-panel|container-continue-watching" and
    // "known-panel|container-my-list", fetched with getPanelPanelX. Cut down to the fields read here.
    static final String PANEL_CONTINUE = "known-panel|container-continue-watching";
    static final String PANEL_MY_LIST = "known-panel|container-my-list";
    private static final String PANEL_QUERY =
            "query panel($id: String!, $limit: Int!) { panelX(id: $id) { __typename ... on PosterListPanel {"
                    + " posters(config: { limit: $limit }) { items { id a11y { contentLabel }"
                    + " image { source } link { link3 { __typename to } }"
                    + " progress { __typename ... on Progress { id position percent timeLeft } } } } } } }";

    // The app's AddToFavorites / RemoveFromFavorites (Min lista), type SERIES or MEDIA. Verified with one
    // add and remove of Storage Wars on 2026-10-15: AddToMyListResultOk, then RemoveFromMyListResultOk.
    private static final String ADD_MY_LIST =
            "mutation addToMyList($input: AddToMyListInput!) { addToMyList2(input: $input) { __typename } }";
    private static final String REMOVE_MY_LIST =
            "mutation removeFromMyList($input: RemoveFromMyListInput!) { removeFromMyList2(input: $input) { __typename } }";
    // RemoveListFromContinueWatchingV2: ids are the poster payload ids (s-… or m-…). Not run against the server.
    private static final String HIDE_CONTINUE =
            "mutation hideContinue($input: RemoveListFromContinueWatchingInputV2!) {"
                    + " removeListFromContinueWatchingV2(input: $input) { __typename } }";

    // Schedule a recording of a series or media (the official app's ScheduleRecording2, p247li/A5.java).
    // Returns the message Telia gives ("... kommer att spelas in") or throws with its error code.
    String scheduleRecording(String id, boolean series) throws Exception {
        JSONObject vars = new JSONObject().put("recordingId", id)
                .put("recordingType", series ? "SERIES" : "MEDIA");
        JSONObject res = graphql("scheduleRecording2", SCHEDULE_RECORDING, vars).getJSONObject("scheduleRecording2");
        if ("ScheduleRecordingResultError".equals(res.optString("__typename"))) {
            throw new Exception("scheduleRecording: " + res.optString("code", "unknown") + " " + res.optString("message", ""));
        }
        JSONObject msg = res.optJSONObject("scheduleRecordingMessage");
        return msg == null ? "Inspelningen är schemalagd" : msg.optString("text", "Inspelningen är schemalagd");
    }

    private static final String SCHEDULE_RECORDING =
            "mutation scheduleRecording2($recordingId: ID!, $recordingType: MutationPayloadType!) {"
                    + " scheduleRecording2(input: { id: $recordingId type: $recordingType } ) { __typename"
                    + " ... on ScheduleRecordingResultOk { scheduleRecordingMessage { text } }"
                    + " ... on ScheduleRecordingResultError { code message } } }";

    /** Fills now/next for every channel with one request (27 channels: about 5 kB, 0.2 s). */
    void loadNowNext(List<Channel> channels) throws Exception {
        Map<String, Channel> byId = new HashMap<>();
        for (Channel c : channels) byId.put(c.id, c);
        long now = Clock.now();
        int offset = 0;
        while (true) {
            JSONObject vars = new JSONObject().put("offset", offset).put("limit", 100)
                    .put("from", now).put("to", now + 60 * 60_000L);
            JSONObject res = graphql("nowNext", NOW_NEXT_QUERY, vars).getJSONObject("channels");
            JSONArray items = res.getJSONArray("channelItems");
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                Channel c = byId.get(it.getString("id"));
                if (c == null) continue;
                JSONArray progs = it.getJSONObject("programsByTime").getJSONArray("programItems");
                // By time, not by place: a gap in the schedule leaves nothing on air now, and the first programme
                // of the window is then the next one, not the one now (B2).
                c.now = null;
                c.next = null;
                for (int k = 0; k < progs.length(); k++) {
                    Program p = program(progs.getJSONObject(k));
                    if (p.startMs <= now && now < p.endMs) c.now = p;
                    else if (p.startMs > now && c.next == null) c.next = p;
                }
            }
            JSONObject page = res.getJSONObject("pageInfo");
            if (!page.optBoolean("hasNextPage")) break;
            offset = page.getInt("nextPageOffset");
        }
    }

    /** The last 24 hours and the next 6 hours of one channel, with catch-up and start-over specs. */
    List<Program> guide(Channel ch) throws Exception {
        long now = Clock.now();
        return programmes(ch, now - 24 * 3600_000L, now + 6 * 3600_000L);
    }

    /**
     * The programmes of one channel between two times, with their catch-up and start-over options.
     * Each option remembers its channel and programme, so playback can continue with the next one.
     */
    List<Program> programmes(Channel ch, long fromMs, long toMs) throws Exception {
        JSONObject vars = new JSONObject().put("channelId", ch.id).put("from", fromMs).put("to", toMs);
        JSONArray items = graphql("guide", GUIDE_QUERY, vars).getJSONObject("channel")
                .getJSONObject("programsByTime").getJSONArray("programItems");
        List<Program> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            Program p = program(it);
            JSONObject play = optPath(it, "media", "playback", "play");
            p.catchup = onDemand(play, p.title);
            startOver(p, optPath(play, "linear", "item"));
            for (Playable x : new Playable[] {p.catchup, p.startOver}) {
                if (x == null) continue;
                x.channel = ch;
                x.programme = p;
            }
            out.add(p);
        }
        return out;
    }

    List<Asset> search(String q) throws Exception {
        JSONObject vars = new JSONObject().put("query", q).put("limit", 60);
        JSONArray posters = graphql("search", SEARCH_QUERY, vars).getJSONObject("search2").getJSONArray("posters");
        List<Asset> out = new ArrayList<>();
        for (int i = 0; i < posters.length(); i++) {
            JSONObject p = posters.getJSONObject(i);
            JSONObject link = optPath(p, "link", "link3");
            if (link == null) continue;
            String type = link.optString("__typename");
            Asset a = new Asset();
            a.id = link.optString("to");
            String label = optPath(p, "a11y") != null ? p.getJSONObject("a11y").optString("contentLabel") : "";
            // contentLabel is "Title, Provider"; split the provider off into the kind label.
            int comma = label.lastIndexOf(", ");
            a.title = comma > 0 ? label.substring(0, comma) : label;
            String provider = comma > 0 ? label.substring(comma + 2).trim() : "";
            switch (type) {
                case "LinkSeries": a.isSeries = true; a.kind = "Serie"; break;
                case "LinkMovie": a.kind = "Film"; break;
                case "LinkEpisode": a.kind = "Avsnitt"; break;
                case "LinkSportEvent": a.kind = "Sport"; break;
                default: continue; // channels, pages and store links are not part of this client
            }
            a.provider = provider;
            a.kindLabel = join(a.kind, provider);
            a.imageUrl = source(p.optJSONObject("image"));
            out.add(a);
        }
        return out;
    }

    /** Fetches the playback options of a movie, episode or sport event found by search. */
    Playable resolve(Asset a) throws Exception {
        Asset m = media(a.id, a.title);
        if (m == null) return null;
        a.positionMs = m.positionMs;
        a.durationMs = m.durationMs;
        if (m.favorite != null) a.favorite = m.favorite;
        return m.playable;
    }

    /**
     * A movie, episode or sport event by id, with its playback option, Telia's saved position and, for
     * an episode, its series. Null when the id is unknown.
     */
    Asset media(String id, String fallbackTitle) throws Exception {
        JSONObject media = graphql("media", MEDIA_QUERY, new JSONObject().put("id", id)).optJSONObject("media");
        if (media == null) return null;
        Asset a = new Asset();
        a.id = media.optString("id", id);
        a.name = media.optString("title", fallbackTitle == null ? "" : fallbackTitle);
        a.title = a.name;
        String type = media.optString("__typename");
        a.kind = "Movie".equals(type) ? "Film" : "Episode".equals(type) ? "Avsnitt" : "Sport";
        JSONObject num = media.optJSONObject("episodeNumber"), season = media.optJSONObject("seasonNumber");
        a.number = num != null ? num.optInt("number") : 0;
        a.season = season != null ? season.optInt("number") : 0;
        JSONObject series = media.optJSONObject("series");
        if (series != null) {
            a.seriesId = series.optString("id", null);
            a.seriesTitle = series.optString("title", "");
        }
        userData(a, media);
        JSONObject play = optPath(media, "playback", "play");
        Playable p = onDemand(play, a.title);
        if (p == null) {
            JSONObject linear = optPath(play, "linear", "item");
            p = linear == null ? null : playable(linear.optJSONObject("playbackSpec"), a.title);
        } else {
            onDemandOf(p, a);
        }
        a.playable = p;
        a.resolved = true;
        return a;
    }

    /** Duration, saved position and Min lista state from a media object. */
    private static void userData(Asset a, JSONObject media) {
        JSONObject dur = media.optJSONObject("duration");
        if (dur != null) a.durationMs = dur.optLong("seconds") * 1000;
        JSONObject ud = media.optJSONObject("userData");
        if (ud == null) return;
        if (ud.has("favorite") && !ud.isNull("favorite")) a.favorite = ud.optBoolean("favorite");
        JSONObject pr = ud.optJSONObject("progress");
        if (pr != null) {
            a.positionMs = Math.max(0, pr.optLong("position"));
            a.percent = pr.optInt("percent");
            a.minutesLeft = minutes(pr.optString("timeLeft", ""));
        }
    }

    /** "12 min återstår" → 12; -1 when there is no number. The nominal duration can be minutes off. */
    private static int minutes(String timeLeft) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*min").matcher(timeLeft == null ? "" : timeLeft);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Ties an on-demand option to its media, so its progress can be saved and reported. */
    private static void onDemandOf(Playable p, Asset a) {
        p.mediaId = a.id;
        p.seriesId = a.seriesId;
        p.seriesTitle = a.seriesTitle;
        p.season = a.season;
        p.durationMs = a.durationMs;
        p.startPositionMs = a.positionMs;
    }

    List<Season> seasons(Asset series) throws Exception {
        JSONObject sj = graphql("series", SERIES_QUERY, new JSONObject().put("id", series.id)).getJSONObject("series");
        JSONObject ud = sj.optJSONObject("userData");
        if (ud != null && ud.has("favorite") && !ud.isNull("favorite")) series.favorite = ud.optBoolean("favorite");
        JSONObject sug = sj.optJSONObject("suggestedEpisode");
        series.suggestedId = sug == null ? null : sug.optString("id", null);
        JSONObject backdrop = optPath(sj, "images", "backdrop16x9");
        series.heroUrl = source(backdrop);
        series.heroColor = color(optPath(backdrop, "colors", "muteBgColor"));
        series.genre = sj.isNull("genre") ? "" : sj.optString("genre", "");
        JSONObject age = optPath(sj, "ageRating", "content");
        series.ageRating = age == null || age.isNull("text") ? "" : age.optString("text", "");
        // The metadata the official app shows and this client now carries too (getMobileSeries).
        series.description = sj.isNull("description") ? null : sj.optString("description", null);
        series.imdbRating = optPath(sj, "ratings", "imdb") == null ? ""
                : optPath(sj, "ratings", "imdb").optString("readableScore", "");
        JSONObject year = sj.optJSONObject("yearProduction");
        series.yearProduction = year == null ? "" : year.optString("readable", "");
        if (sj.has("isRecordable") && !sj.isNull("isRecordable")) series.recordable = sj.optBoolean("isRecordable");
        JSONArray items = sj.getJSONObject("seasonLinks").getJSONArray("items");
        List<Season> out = new ArrayList<>();
        for (int i = items.length() - 1; i >= 0; i--) { // newest season first
            JSONObject it = items.getJSONObject(i);
            Season s = new Season();
            s.id = it.getString("id");
            JSONObject num = it.optJSONObject("seasonNumber");
            s.title = num != null ? num.optString("readable", "Säsong") : "Säsong";
            JSONObject count = it.optJSONObject("numberOfEpisodes");
            if (count != null) s.episodeCount = count.optInt("number", -1);
            out.add(s);
        }
        return out;
    }

    List<Asset> episodes(Season season) throws Exception {
        return episodes(season, null, null);
    }

    /** The episodes of one season; `seriesId` and `seriesTitle` tie each one to its series. */
    List<Asset> episodes(Season season, String seriesId, String seriesTitle) throws Exception {
        JSONArray list = graphql("episodes", EPISODES_QUERY, new JSONObject().put("id", season.id))
                .getJSONObject("season").getJSONObject("episodes2").getJSONArray("episodeList");
        List<Asset> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = list.getJSONObject(i).getJSONObject("episode");
            Asset a = new Asset();
            a.id = e.getString("id");
            JSONObject num = e.optJSONObject("episodeNumber");
            a.number = num != null ? num.optInt("number") : 0;
            a.name = e.optString("title");
            // "3. Dags att välja"; a title that is only the number ("Avsnitt 3") is not numbered twice.
            boolean bare = a.name.trim().equalsIgnoreCase("Avsnitt " + a.number) || a.name.trim().equals(String.valueOf(a.number));
            a.title = (a.number > 0 && !bare ? a.number + ". " : "") + a.name;
            a.kind = "Avsnitt";
            a.seriesId = seriesId;
            a.seriesTitle = seriesTitle;
            String digits = season.title == null ? "" : season.title.replaceAll("\\D+", "");
            a.season = digits.isEmpty() || digits.length() > 4 ? 0 : Integer.parseInt(digits);
            userData(a, e);
            a.imageUrl = source(optPath(e, "images", "screenshot16x9"));
            a.playable = onDemand(optPath(e, "playback", "play"), a.name);
            if (a.playable != null) onDemandOf(a.playable, a);
            a.resolved = true;
            out.add(a);
        }
        return out;
    }

    /**
     * The current programme of one channel with its start-over option (null when nothing is on).
     * About 1 kB; prefetched on every tune so "from the start" is instant.
     */
    Program current(Channel ch) throws Exception {
        long now = Clock.now();
        JSONObject vars = new JSONObject().put("channelId", ch.id).put("from", now).put("to", now + 1000);
        JSONArray items = graphql("current", CURRENT_QUERY, vars).getJSONObject("channel")
                .getJSONObject("programsByTime").getJSONArray("programItems");
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            Program p = program(it);
            if (p.startMs > now || p.endMs <= now) continue;
            startOver(p, optPath(it, "media", "playback", "play", "linear", "item"));
            if (p.startOver != null) {
                p.startOver.channel = ch;
                p.startOver.programme = p;
            }
            return p;
        }
        return null;
    }

    /**
     * Takes the start-over spec of `linear` when it is this slot's broadcast. `linear` can describe
     * another airing of the same media (a rerun days later), and the broadcast times differ from the
     * EPG times by seconds to minutes (EPG 20:00:00, broadcast 20:00:10), so the match is "overlaps
     * the slot and starts within 10 minutes of it", not equality.
     */
    private static void startOver(Program p, JSONObject linear) {
        if (linear == null) return;
        JSONObject st = linear.optJSONObject("startTime"), en = linear.optJSONObject("endTime");
        if (st == null) return;
        long ls = st.optLong("timestamp"), le = en == null ? 0 : en.optLong("timestamp");
        boolean overlaps = ls < p.endMs && (le == 0 || le > p.startMs);
        if (!overlaps || Math.abs(ls - p.startMs) > 10 * 60_000L) return;
        p.startOver = playable(optPath(linear, "startover", "playbackSpec"), p.title);
        p.broadcastStartMs = ls;
        p.broadcastEndMs = le;
    }

    private static Program program(JSONObject it) {
        Program p = new Program();
        p.id = it.optString("id");
        p.title = it.optString("title");
        JSONObject start = it.optJSONObject("startTime"), end = it.optJSONObject("endTime");
        p.startMs = start == null ? 0 : start.optLong("timestamp");
        p.endMs = end == null ? 0 : end.optLong("timestamp");
        return p;
    }

    /** The first on-demand option: the subscription, then rentals and purchases the user owns. */
    private static Playable onDemand(JSONObject play, String title) {
        if (play == null) return null;
        for (String k : new String[] {"subscription", "rental", "est"}) {
            JSONArray arr = play.optJSONArray(k);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                Playable p = playable(optPath(arr.optJSONObject(i), "item", "playbackSpec"), title);
                if (p != null) return p;
            }
        }
        // NPVR (a recording of this title): after the subscription options, before nothing.
        JSONObject npvr = play.optJSONObject("npvr");
        Playable p = npvr == null ? null
                : playable(optPath(npvr, "item", "playbackSpec"), title);
        if (p == null && npvr != null) p = playable(optPath(npvr, "startover", "playbackSpec"), title);
        return p;
    }

    // ---- Fortsätt titta, Min lista, progress (F15) ----------------------------------------

    /**
     * The posters of a Telia panel, newest first as the server orders them. For Fortsätt titta a series
     * poster carries the episode to play in progress.id ("m-…") with its position in milliseconds; a
     * position of 0 means Telia already moved on to the next episode.
     */
    List<Saved> panel(String panelId, int limit) throws Exception {
        JSONObject vars = new JSONObject().put("id", panelId).put("limit", limit);
        return parsePanel(graphql("panel", PANEL_QUERY, vars).optJSONObject("panelX"));
    }

    /** The posters of a panelX answer; the title, provider and progress come from the poster, the art from its image. */
    static List<Saved> parsePanel(JSONObject panel) throws Exception {
        JSONArray items = panel == null ? null : optPath(panel, "posters") == null ? null
                : panel.getJSONObject("posters").optJSONArray("items");
        List<Saved> out = new ArrayList<>();
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            JSONObject link = optPath(it, "link", "link3");
            if (link == null) continue;
            String type = link.optString("__typename");
            if (!"LinkSeries".equals(type) && !"LinkMovie".equals(type) && !"LinkEpisode".equals(type)) continue;
            Saved s = new Saved();
            s.id = link.optString("to");
            s.series = "LinkSeries".equals(type);
            String label = optPath(it, "a11y") == null ? "" : it.getJSONObject("a11y").optString("contentLabel");
            int comma = label.lastIndexOf(", ");
            s.title = comma > 0 ? label.substring(0, comma) : label;
            s.provider = comma > 0 ? label.substring(comma + 2).trim() : "";
            JSONObject pr = it.optJSONObject("progress");
            s.mediaId = s.series ? null : s.id;
            if (pr != null && "Progress".equals(pr.optString("__typename"))) {
                if (!pr.optString("id").isEmpty()) s.mediaId = pr.optString("id");
                s.positionMs = Math.max(0, pr.optLong("position"));
                s.percent = pr.optInt("percent");
                s.minutesLeft = minutes(pr.optString("timeLeft", ""));
            }
            s.imageUrl = source(it.optJSONObject("image"));
            if (s.id.isEmpty() || s.title.isEmpty()) continue;
            out.add(s);
        }
        return out;
    }

    /** Adds to or removes from Min lista. `series` picks the type: SERIES for a series, MEDIA otherwise. */
    void setMyList(String id, boolean series, boolean add) throws Exception {
        JSONObject vars = new JSONObject().put("input", new JSONObject().put("id", id).put("type", series ? "SERIES" : "MEDIA"));
        JSONObject data = graphql(add ? "addToMyList" : "removeFromMyList", add ? ADD_MY_LIST : REMOVE_MY_LIST, vars);
        JSONObject r = data.optJSONObject(add ? "addToMyList2" : "removeFromMyList2");
        String t = r == null ? "" : r.optString("__typename");
        if (!t.endsWith("ResultOk")) throw new Exception("my list: " + t);
    }

    /** Removes a series or film from Fortsätt titta on every device. */
    void hideContinue(String id) throws Exception {
        JSONObject vars = new JSONObject().put("input", new JSONObject().put("ids", new JSONArray().put(id)));
        graphql("hideContinue", HIDE_CONTINUE, vars);
    }

    static final String TRACKING_BASE = "https://playbacktrackinggateway.clientapi-prod.live.tv.telia.net/";

    /**
     * One playback event for the ticket's tracking session (WireStatisticsModule in the app): type
     * SESSION_START, PROGRESS or SESSION_END, position and duration in milliseconds. Telia's
     * userData.progress follows these. No-op for tickets without tracking.
     */
    void playbackEvent(Stream s, String type, long positionMs, long durationMs) throws Exception {
        if (s == null || s.trackingSessionId == null) return;
        JSONObject body = new JSONObject()
                .put("sessionId", s.trackingSessionId)
                .put("type", type)
                .put("time", System.currentTimeMillis())
                .put("position", Math.max(0, positionMs))
                .put("duration", Math.max(0, durationMs));
        Map<String, String> h = authHeaders();
        h.put("Client", "telia-play-android"); // wireClient of the app's prod configuration (p148gm/a.java)
        Http.Response r = Http.request("POST", TRACKING_BASE + "event", h, body.toString(), "application/json; charset=utf-8");
        if (!r.ok()) throw new Exception("tracking HTTP " + r.code);
    }

    /**
     * Releases the ticket with the last position, as the app does when playback ends
     * (StreamingTicketSecureResourceV2.deleteRequest): DELETE …/streamingticket/{type}/{id}?position=<ms>.
     */
    void releaseTicket(Stream s, long positionMs) throws Exception {
        if (s == null || s.playable == null || s.sessionId == null) return;
        Playable p = s.playable;
        String url = STREAMING_BASE + "streaminggateway/rest/secure/v2/streamingticket/" + p.videoIdType + "/" + p.videoId
                + "?position=" + Math.max(0, positionMs) + "&sessionId=" + s.sessionId + "&whiteLabelBrand=" + BRAND;
        Map<String, String> h = authHeaders();
        h.put("tv-client-boot-id", bootId);
        h.put("videoId", p.videoId);
        h.put("videoIdType", p.videoIdType);
        Http.request("DELETE", url, h, null, null);
    }

    /** The URL-decoded `source` of a logo variant, or null (the name is drawn in the slot instead). */
    private static String logoSource(JSONObject variant) {
        String src = variant == null ? "" : variant.optString("source");
        if (src.isEmpty()) return null;
        try {
            return java.net.URLDecoder.decode(src, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** The `source` of an image object, as the API sends it (URL-encoded); null when missing. */
    private static String source(JSONObject image) {
        String s = image == null || image.isNull("source") ? "" : image.optString("source");
        return s.isEmpty() ? null : s;
    }

    /** "#321B00" to an opaque ARGB int; 0 when missing or malformed. */
    private static int color(JSONObject hex) {
        String h = hex == null ? "" : hex.optString("hex");
        if (!h.matches("#[0-9A-Fa-f]{6}")) return 0;
        return 0xFF000000 | Integer.parseInt(h.substring(1), 16);
    }

    private static String join(String a, String b) {
        return b.isEmpty() ? a : a + " · " + b;
    }

    void logout() {
        prefs.edit().remove("accessToken").remove("refreshToken").apply();
    }

    private static JSONObject optPath(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o == null) return null;
            o = o.optJSONObject(k);
        }
        return o;
    }

    private static String trim(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s;
    }
}
