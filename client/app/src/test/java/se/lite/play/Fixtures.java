package se.lite.play;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Recorded API responses. One file per request in client/fixtures/, named by {@link #key}.
 * Replay serves them with no network; a missing fixture fails loudly with its file name.
 */
final class Fixtures {
    static final File DIR = new File(System.getProperty("fixtures.dir", "fixtures"));

    /**
     * Stress replay (DESIGN.md, stable geometry): every display string in a response is made about three
     * times longer with å ä ö, channel numbers get three digits. Ids, times and counts stay, so the same
     * key presses reach the same screens and the geometry of both runs must match.
     */
    static volatile boolean stress;

    static String stressed(String json) {
        try {
            Object o = new org.json.JSONTokener(json).nextValue();
            stretch(o);
            return o.toString();
        } catch (Exception e) {
            return json;
        }
    }

    private static String longer(String s) {
        return s.isEmpty() ? s : s + " " + s + " " + s + " ÅÄÖåäö";
    }

    private static void stretch(Object o) throws org.json.JSONException {
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            java.util.Iterator<String> it = j.keys();
            java.util.List<String> keys = new java.util.ArrayList<>();
            while (it.hasNext()) keys.add(it.next());
            for (String k : keys) {
                Object v = j.get(k);
                if (v instanceof String && (k.equals("title") || k.equals("name") || k.equals("readable")
                        || k.equals("description") || k.equals("genre"))) {
                    j.put(k, longer((String) v));
                } else if (v instanceof String && k.equals("contentLabel")) {
                    String l = (String) v;
                    int c = l.lastIndexOf(", ");
                    j.put(k, c > 0 ? longer(l.substring(0, c)) + ", " + longer(l.substring(c + 2)) : longer(l));
                } else if (k.equals("channelNumber")) {
                    j.put(k, 888);
                } else {
                    stretch(v);
                }
            }
        } else if (o instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) o;
            for (int i = 0; i < a.length(); i++) stretch(a.get(i));
        }
    }

    /** Channel ids whose logo fails (HTTP error), to see the fallback in the logo slot. */
    static final java.util.Set<String> failLogos = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** While positive, holds every response, to make loading states visible in screenshots. */
    static volatile int delayMs;

    private Fixtures() {}

    /** A stable file name for a request: the GraphQL operation plus its main variable, or the REST path. */
    static String key(String url, String body) {
        try {
            if (url.contains("graphql")) {
                JSONObject b = new JSONObject(body);
                String op = b.optString("operationName", "graphql");
                JSONObject v = b.optJSONObject("variables");
                String arg = "";
                if (v != null) {
                    for (String k : new String[] {"id", "channelId", "query"}) {
                        if (v.has(k)) {
                            arg = v.getString(k);
                            break;
                        }
                    }
                }
                return safe(arg.isEmpty() ? op : op + "__" + arg);
            }
        } catch (Exception ignored) {
            // fall through to the path-based key
        }
        if (url.contains("/streamingticket/")) return "ticket";
        String path = url.replaceFirst("^https?://[^/]+/", "").replaceFirst("\\?.*$", "");
        return safe("rest__" + path);
    }

    private static String safe(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9åäö_-]+", "_");
    }

    static File file(String key) {
        return new File(DIR, key + ".json");
    }

    /** Installs the replay transport and moves the clock to the recording time. */
    static void install() throws Exception {
        File meta = new File(DIR, "meta.json");
        if (!meta.exists()) throw new IllegalStateException("No fixtures in " + DIR + ". Run the recorder first (README.md).");
        long recordedAt = new JSONObject(read(meta)).optLong("recordedAt");
        Clock.offsetMs = recordedAt - System.currentTimeMillis();
        Http.transport = (method, url, headers, body, contentType) -> {
            // slow:<ms> holds every response until the scenario sets slow:0 (screenshot time is not wall time).
            while (delayMs > 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            File f = file(key(url, body));
            if (!f.exists()) {
                System.err.println("FIXTURE MISSING: " + f.getName() + " for " + method + " " + url);
                return new Http.Response(404, "{\"errors\":[{\"message\":\"no fixture " + f.getName() + "\"}]}");
            }
            String text = read(f);
            if (f.getName().startsWith("rest__logingateway_rest_v1_device_authorization") && logins.incrementAndGet() > 1) {
                // A second login in the test run (after Logga ut) gets a new code, as the server would give (A11).
                text = text.replace("\"userCode\":\"766162\"", "\"userCode\":\"314159\"");
            }
            return new Http.Response(200, stress ? stressed(text) : text);
        };
    }

    /** Device-code requests served in this JVM: the second login of the test run gets a new code (A11). */
    static final java.util.concurrent.atomic.AtomicInteger logins = new java.util.concurrent.atomic.AtomicInteger();

    /** Channel ids whose light-background variant fails (one variant only). */
    static final java.util.Set<String> failLightLogos = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Logos come from client/fixtures/logos/<channelId>.png (dark-background artwork) and
     * client/fixtures/logos-light/<channelId>.png (light-background artwork). A missing file, a failLogos
     * id (both variants) or a failLightLogos id (light only) is an HTTP 404.
     */
    static void installLogos() {
        Logos.source = (id, light, url) -> {
            File f = new File(new File(DIR, light ? "logos-light" : "logos"), id + ".png");
            if (failLogos.contains(id) || (light && failLightLogos.contains(id)) || !f.exists()) {
                throw new IOException("HTTP 404 (fixture) for " + (light ? "light" : "dark") + " logo " + id);
            }
            return Files.readAllBytes(f.toPath());
        };
    }

    /** Image URLs containing one of these fail (HTTP 404), to see the placeholder stay in its slot. */
    static final java.util.Set<String> failImages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** While positive, holds every image response (the data is there, the pictures are not yet). */
    static volatile int imageDelayMs;

    /** Image requests served so far (all URLs, including failures). */
    static final java.util.concurrent.atomic.AtomicInteger imageRequests = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Images come from client/fixtures/images/, by the proxy URL in images/index.json (recorded by
     * images/record.py). A URL that is not recorded, or matches failImages, is an HTTP 404.
     */
    static void installImages() throws IOException {
        File dir = new File(DIR, "images");
        JSONObject index;
        try {
            index = new JSONObject(read(new File(dir, "index.json")));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        java.util.Map<String, String> bySource = new java.util.HashMap<>();
        for (java.util.Iterator<String> it = index.keys(); it.hasNext(); ) {
            String k = it.next();
            bySource.putIfAbsent(k.replaceFirst("resize=\\d+x\\d+", "resize="), index.optString(k, ""));
        }
        Images.source = url -> {
            imageRequests.incrementAndGet();
            while (imageDelayMs > 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            String name = index.optString(url, "");
            if (name.isEmpty()) { // a slot resized since the recording: the same picture at any recorded size
                name = bySource.getOrDefault(url.replaceFirst("resize=\\d+x\\d+", "resize="), "");
            }
            for (String f : failImages) {
                if (url.contains(f) || name.startsWith(f)) throw new IOException("HTTP 404 (fixture) " + f);
            }
            File f = new File(dir, name);
            if (name.isEmpty() || !f.exists()) throw new IOException("HTTP 404 (no image fixture)");
            return Files.readAllBytes(f.toPath());
        };
    }

    static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static void write(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }
}
