package se.lite.play;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Minimal blocking HTTP client on top of HttpURLConnection. Call it off the main thread. */
final class Http {
    static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    /** The network seam. Tests replace it to record real responses or to replay them offline. */
    interface Transport {
        Response request(String method, String url, Map<String, String> headers, String body, String contentType)
                throws IOException;
    }

    static volatile Transport transport = Http::network;

    /**
     * The hard limit of one data request, from the call to the answer (E9, 2026-10-23: Tablå sat on "Laddar …" after a
     * long idle). The connection's own timeouts do not bound a request: the connect timeout leaves out the DNS lookup,
     * and the read timeout counts per read, so a dead pooled connection after a TV standby or a server that trickles
     * can hold the caller far longer. A test lowers it.
     */
    static volatile long deadlineMs = 12_000;

    /** The threads a bounded request runs on; one that outlives its deadline is left to end on its own timeouts. */
    private static final ExecutorService calls = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "http");
        t.setDaemon(true);
        return t;
    });

    private Http() {}

    /**
     * A request with no limit beyond the connection's own timeouts: the streaming ticket, whose path (the zap) is left
     * exactly as it was measured (`zap-bench.sh`).
     */
    static Response request(String method, String url, Map<String, String> headers, String body, String contentType)
            throws IOException {
        return transport.request(method, url, headers, body, contentType);
    }

    /**
     * A request that answers or fails within {@link #deadlineMs}: every GraphQL query, the token refresh and the
     * playback reports. A request that has not answered by then fails with an IOException, so the caller can show
     * what it has, retry or say so; it never waits for ever.
     */
    static Response bounded(String method, String url, Map<String, String> headers, String body, String contentType)
            throws IOException {
        Transport t = transport;
        Future<Response> f = calls.submit(() -> t.request(method, url, headers, body, contentType));
        long limit = deadlineMs;
        try {
            return f.get(limit, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new IOException("no answer in " + limit + " ms: " + host(url));
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException) throw (IOException) c;
            throw new IOException(c);
        } catch (InterruptedException e) {
            f.cancel(true);
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted: " + host(url));
        }
    }

    private static String host(String url) {
        int s = url.indexOf("://"), e = s < 0 ? -1 : url.indexOf('/', s + 3);
        return s < 0 ? url : e < 0 ? url.substring(s + 3) : url.substring(s + 3, e);
    }

    static Response network(String method, String url, Map<String, String> headers, String body, String contentType)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod(method);
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        }
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : readAll(in);
        c.disconnect();
        return new Response(code, text);
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
