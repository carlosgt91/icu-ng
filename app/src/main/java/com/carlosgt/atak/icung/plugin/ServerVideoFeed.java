package com.carlosgt.atak.icung.plugin;

import android.util.Log;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.video.manager.VideoXMLHandler;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.comms.TAKServer;
import com.atakmap.net.CertificateManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

import gov.tak.api.video.ConnectionEntry;

/**
 * Lists the stream in the Video Feed Manager of every connected TAK Server
 * (POST /Marti/vcm, the same call as ATAK's own "send video alias to
 * server"), so the command post finds every live feed in one place instead
 * of marker by marker. Authenticated with the client certificate of that
 * server connection.
 *
 * One feed per phone (its UID derives from the self marker), marked active
 * while live and inactive when stopped: the server updates it by UID, and a
 * field user's certificate may not delete it. A stop with no network leaves
 * it active until the next stream from this phone.
 */
final class ServerVideoFeed {

    private static final String TAG = "IcuNgFeed";
    private static final int TIMEOUT_MS = 10000;

    // One at a time and in order, so "inactive" never overtakes "active".
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "icung-server-feed");
        t.setDaemon(true);
        return t;
    });

    static String feedUid(String selfUid) {
        return "icung-" + selfUid;
    }

    /**
     * The feed XML: ATAK's own serialization, with this phone's fixed UID
     * and the active flag the server keeps.
     */
    static String feedXml(ConnectionEntry entry, String uid, boolean active) {
        String xml = VideoXMLHandler.serialize(Collections.singletonList(entry));
        xml = xml.replaceFirst("<uid>[^<]*</uid>|<uid/>",
                "<uid>" + escape(uid) + "</uid>");
        return xml.replaceFirst("<feed>", "<feed>\n<active>" + active + "</active>");
    }

    void publish(ConnectionEntry entry, String uid, boolean active) {
        if (entry == null) return;
        final String xml = feedXml(entry, uid, active);
        worker.execute(() -> {
            CotMapComponent cmc = CotMapComponent.getInstance();
            TAKServer[] servers = cmc == null ? null : cmc.getServers();
            if (servers == null) return;
            for (TAKServer s : servers) {
                if (s == null || !s.isEnabled() || !s.isConnected()) continue;
                try {
                    int code = post(s, xml);
                    Log.i(TAG, "feed " + (active ? "activo" : "inactivo") + " en "
                            + s.getDescription() + ": HTTP " + code);
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, "no se pudo publicar el feed en " + s.getDescription(), e);
                }
            }
        });
    }

    void dispose() {
        worker.shutdown();
    }

    private static int post(TAKServer server, String xml) throws IOException {
        String base = server.getURL(false);
        if (base == null || !base.toLowerCase(java.util.Locale.ROOT).startsWith("https")) {
            // Plain TCP connections have no certificate to sign in with.
            throw new IOException("servidor sin TLS: " + base);
        }
        URL url = new URL(base + SslNetCotPort.getServerApiPath(SslNetCotPort.Type.SECURE) + "/vcm");
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        try {
            c.setSSLSocketFactory(CertificateManager.getSockFactory2(true,
                    server.getConnectString()));
            // As ATAK's TakHttpClient: the server certificate is checked
            // against this connection's trust store, not by host name.
            c.setHostnameVerifier(CertificateManager.ALL_TRUSTING_HOSTNAME_VERIFIER);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/xml; charset=UTF-8");
            byte[] body = xml.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code);
            return code;
        } finally {
            c.disconnect();
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
