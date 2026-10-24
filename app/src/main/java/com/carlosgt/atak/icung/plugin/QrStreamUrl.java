package com.carlosgt.atak.icung.plugin;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the plain-text URL in a server-generated stream QR (the
 * restreamer's /stream-qr page) into ICU NG settings. The QR is just the
 * URL - no JSON, no custom scheme:
 *
 * <pre>
 *   rtsp://[userinfo@]host:8554/path[?name=...]
 *   rtsps://[userinfo@]host:8555/path[?name=...]
 *   srt://host:8890?streamid={read|publish}:path[:userinfo][&amp;(SRT key)=...][&amp;name=...]
 * </pre>
 *
 * What gets normalised, and why:
 * <ul>
 *   <li>{@code name} is a label for people, not a MediaMTX parameter - it
 *   is dropped from the URL (ICU NG labels the stream with the callsign).</li>
 *   <li>SRT {@code streamid}: split on ':' first, percent-decode each part
 *   after, so an encoded ':' in a credential can't split in the wrong place.
 *   RootEncoder sends the streamid verbatim, so it must reach it decoded.
 *   A {@code read:} streamid becomes {@code publish:} - this plugin only
 *   ever publishes, and a "watch" QR is the one people tend to have handy.</li>
 *   <li>The SRT encryption key moves out of the URL into its own setting,
 *   where the pane can show and validate it.</li>
 * </ul>
 * RTSP/RTSPS credentials stay in the URL as they are: RootEncoder decodes
 * the userinfo itself.
 */
public final class QrStreamUrl {

    public static final class Result {
        public final int protocol;
        public final String url;
        /** null when the QR carried no SRT key (keep the current one) */
        public final String srtPassphrase;
        /** the QR's label, null if absent - informational only */
        public final String name;

        Result(int protocol, String url, String srtPassphrase, String name) {
            this.protocol = protocol;
            this.url = url;
            this.srtPassphrase = srtPassphrase;
            this.name = name;
        }
    }

    /** Why a QR was rejected; the plugin turns it into translated text. */
    public enum Reason {
        EMPTY, NOT_A_URL, NO_HOST, RTMP, UNSUPPORTED_PROTOCOL, SRT_NO_STREAMID,
        SRT_PASSPHRASE_LENGTH
    }

    public static final class InvalidQrException extends IllegalArgumentException {
        public final Reason reason;
        /** e.g. the unsupported scheme; null if none */
        public final String detail;

        InvalidQrException(Reason reason, String detail) {
            super(detail == null ? reason.name() : reason.name() + ": " + detail);
            this.reason = reason;
            this.detail = detail;
        }
    }

    private QrStreamUrl() {
    }

    /** @throws InvalidQrException saying why the QR is not usable */
    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new InvalidQrException(Reason.EMPTY, null);
        }
        String text = raw.trim();
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            throw new InvalidQrException(Reason.NOT_A_URL, null);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new InvalidQrException(Reason.NO_HOST, null);
        }

        switch (scheme) {
            case "rtsp":
                return rtsp(uri, StreamSettings.PROTOCOL_RTSP, 8554);
            case "rtsps":
                return rtsp(uri, StreamSettings.PROTOCOL_RTSPS, 8555);
            case "srt":
                return srt(uri);
            case "rtmp":
            case "rtmps":
                throw new InvalidQrException(Reason.RTMP, null);
            default:
                throw new InvalidQrException(Reason.UNSUPPORTED_PROTOCOL, scheme);
        }
    }

    private static Result rtsp(URI uri, int protocol, int defaultPort) {
        List<String[]> query = splitQuery(uri.getRawQuery());
        String name = null;
        List<String[]> kept = new ArrayList<>();
        for (String[] kv : query) {
            if ("name".equals(kv[0])) name = decode(kv[1]);
            else kept.add(kv);
        }
        StringBuilder url = new StringBuilder();
        url.append(uri.getScheme().toLowerCase()).append("://");
        if (uri.getRawUserInfo() != null) url.append(uri.getRawUserInfo()).append('@');
        url.append(hostForUrl(uri)).append(':')
                .append(uri.getPort() >= 0 ? uri.getPort() : defaultPort);
        url.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        appendQuery(url, kept);
        return new Result(protocol, url.toString(), null, name);
    }

    private static Result srt(URI uri) {
        String name = null;
        String passphrase = null;
        String streamId = null;
        List<String[]> kept = new ArrayList<>();
        for (String[] kv : splitQuery(uri.getRawQuery())) {
            switch (kv[0]) {
                case "name":
                    name = decode(kv[1]);
                    break;
                case "passphrase":
                    passphrase = decode(kv[1]);
                    break;
                case "streamid":
                    streamId = kv[1];
                    break;
                default:
                    kept.add(kv);
                    break;
            }
        }
        if (streamId == null || streamId.isEmpty()) {
            throw new InvalidQrException(Reason.SRT_NO_STREAMID, null);
        }
        if (passphrase != null && (passphrase.length() < 10 || passphrase.length() > 79)) {
            throw new InvalidQrException(Reason.SRT_PASSPHRASE_LENGTH, null);
        }

        String[] parts = streamId.split(":", -1);
        StringBuilder sid = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = decode(parts[i]);
            if (i == 0 && "read".equals(part)) part = "publish";
            if (i > 0) sid.append(':');
            sid.append(part);
        }

        StringBuilder url = new StringBuilder("srt://");
        url.append(hostForUrl(uri)).append(':')
                .append(uri.getPort() >= 0 ? uri.getPort() : 8890);
        url.append("?streamid=").append(sid);
        for (String[] kv : kept) {
            url.append('&').append(kv[0]).append('=').append(kv[1]);
        }
        return new Result(StreamSettings.PROTOCOL_SRT, url.toString(), passphrase, name);
    }

    // URI.getHost() keeps the brackets of an IPv6 literal; make sure they're there.
    private static String hostForUrl(URI uri) {
        String host = uri.getHost();
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static List<String[]> splitQuery(String rawQuery) {
        List<String[]> out = new ArrayList<>();
        if (rawQuery == null || rawQuery.isEmpty()) return out;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            out.add(eq >= 0
                    ? new String[] { pair.substring(0, eq), pair.substring(eq + 1) }
                    : new String[] { pair, "" });
        }
        return out;
    }

    private static void appendQuery(StringBuilder url, List<String[]> query) {
        for (int i = 0; i < query.size(); i++) {
            url.append(i == 0 ? '?' : '&').append(query.get(i)[0]);
            if (!query.get(i)[1].isEmpty()) url.append('=').append(query.get(i)[1]);
        }
    }

    private static String decode(String s) {
        try {
            // URLDecoder would turn '+' into a space; in a URL path/userinfo
            // a '+' is a literal plus.
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
