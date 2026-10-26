package com.carlosgt.atak.icung.plugin;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import gov.tak.api.cot.detail.ICotDetailHandler;
import gov.tak.api.cot.detail.ICotDetailHandler2;
import gov.tak.api.cot.event.CotDetail;
import gov.tak.api.cot.event.CotEvent;
import gov.tak.api.util.AttributeSet;
import gov.tak.api.video.ConnectionEntry;
import gov.tak.api.video.cot.ConnectionEntryDetail;

import com.atakmap.android.maps.MapItem;

/**
 * Adds a {@code <__video>} detail to the self marker's own CoT event while
 * ICU NG is streaming with a GPS fix - never to any other marker.
 */
public class SelfVideoDetailHandler implements ICotDetailHandler2 {

    private static final Set<String> DETAIL_NAMES = Collections.singleton("__video");

    private final String selfUid;
    private final Supplier<Boolean> isStreamingAndFixed;
    private final Supplier<ConnectionEntry> connectionEntrySupplier;

    public SelfVideoDetailHandler(String selfUid, Supplier<Boolean> isStreamingAndFixed,
            Supplier<ConnectionEntry> connectionEntrySupplier) {
        this.selfUid = selfUid;
        this.isStreamingAndFixed = isStreamingAndFixed;
        this.connectionEntrySupplier = connectionEntrySupplier;
    }

    @Override
    public Set<String> getDetailNames() {
        return DETAIL_NAMES;
    }

    @Override
    public boolean isSupported(Object item, AttributeSet attrs, CotEvent event, CotDetail detail) {
        if (!(item instanceof MapItem))
            return false;
        if (!selfUid.equals(((MapItem) item).getUID()))
            return false;
        return Boolean.TRUE.equals(isStreamingAndFixed.get());
    }

    @Override
    public boolean toCotDetail(Object item, AttributeSet attrs, CotEvent event, CotDetail detail) {
        ConnectionEntry entry = connectionEntrySupplier.get();
        if (entry == null)
            return false;

        CotDetail videoDetail = new CotDetail("__video");
        String playbackUrl = buildPlaybackUrl(entry);
        videoDetail.setAttribute("url", playbackUrl);
        if (entry.getUID() != null && !entry.getUID().isEmpty()) {
            videoDetail.setAttribute("uid", entry.getUID());
        }
        if (entry.getAlias() != null && !entry.getAlias().isEmpty()) {
            videoDetail.setAttribute("sensor", entry.getAlias());
        }

        CotDetail ceDetail = ConnectionEntryDetail.toCotDetail(entry);
        ceDetail.setAttribute("rtspReliable", "1");
        videoDetail.addChild(ceDetail);

        detail.addChild(videoDetail);
        return true;
    }

    public static String buildPlaybackUrl(ConnectionEntry entry) {
        if (entry == null)
            return "";
        String scheme = (entry.getProtocol() == ConnectionEntry.Protocol.SRT) ? "srt://" : "rtsp://";
        String host = entry.getAddress() != null ? entry.getAddress() : "";
        int port = entry.getPort();
        String path = entry.getPath() != null ? entry.getPath() : "";
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        if (entry.getProtocol() == ConnectionEntry.Protocol.RTSP && !path.contains("tcp")) {
            path += (path.contains("?") ? "&tcp" : "?tcp");
        }
        return scheme + host + (port > 0 ? ":" + port : "") + path;
    }

    @Override
    public ICotDetailHandler.ImportResult toItemMetadata(Object item, AttributeSet attrs,
            CotEvent event, CotDetail detail) {
        // ICU NG never needs to *receive* a <__video> detail for its own
        // marker - only to emit one.
        return ICotDetailHandler.ImportResult.IGNORE;
    }
}
