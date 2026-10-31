import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jmisb.api.klv.IKlvKey;
import org.jmisb.api.klv.IMisbMessage;
import org.jmisb.api.klv.KlvParser;
import org.jmisb.api.klv.st0601.PrecisionTimeStamp;
import org.jmisb.api.klv.st0601.UasDatalinkMessage;
import org.jmisb.api.klv.st0601.UasDatalinkTag;

/**
 * Checks a raw KLV dump (every packet back to back, as written by
 * {@code ffmpeg -map 0:d -c copy -f data}) with jMISB: every ST 0601
 * packet must parse with a valid checksum, time stamps must move
 * forward, and it reports the tags seen and the packet rate.
 *
 * Usage: java -cp <jmisb jars> KlvCheck.java klv.bin [--dump]
 * Exit code 0 = no errors, 1 = errors, 2 = no ST 0601 packets at all.
 */
public class KlvCheck {

    private static final byte[] ST0601_KEY = {
            0x06, 0x0E, 0x2B, 0x34, 0x02, 0x0B, 0x01, 0x01,
            0x0E, 0x01, 0x03, 0x01, 0x01, 0x00, 0x00, 0x00 };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: KlvCheck klv.bin [--dump]");
            System.exit(64);
        }
        boolean dump = args.length > 1 && args[1].equals("--dump");
        byte[] data = Files.readAllBytes(Path.of(args[0]));
        if (isTransportStream(data)) {
            data = klvFromTransportStream(data);
            if (data == null) {
                System.out.println("MPEG-TS without a KLV stream in its PMT");
                System.exit(2);
            }
        }

        int packets = 0;
        int errors = 0;
        int skipped = 0;
        long firstTs = -1;
        long lastTs = -1;
        int backwards = 0;
        Map<String, Integer> tagCounts = new TreeMap<>();
        List<String> errorSamples = new ArrayList<>();
        UasDatalinkMessage first = null;

        int pos = 0;
        while (pos + 17 <= data.length) {
            if (!startsWith(data, pos, ST0601_KEY)) {
                // Some other KLV set, or garbage: resync on the next key.
                int next = indexOf(data, ST0601_KEY, pos + 1);
                skipped += (next < 0 ? data.length : next) - pos;
                if (next < 0) break;
                pos = next;
                continue;
            }
            int[] ber = berLength(data, pos + 16);
            if (ber == null || pos + 16 + ber[1] + ber[0] > data.length) {
                errors++;
                addSample(errorSamples, "packet " + packets + ": truncated or bad BER length");
                break;
            }
            int end = pos + 16 + ber[1] + ber[0];
            byte[] packet = Arrays.copyOfRange(data, pos, end);
            pos = end;
            packets++;
            try {
                List<IMisbMessage> msgs = KlvParser.parseBytes(packet);
                if (msgs.isEmpty() || !(msgs.get(0) instanceof UasDatalinkMessage)) {
                    errors++;
                    addSample(errorSamples, "packet " + (packets - 1) + ": not an ST 0601 message");
                    continue;
                }
                UasDatalinkMessage m = (UasDatalinkMessage) msgs.get(0);
                if (first == null) first = m;
                for (IKlvKey k : m.getIdentifiers()) tagCounts.merge(label(k), 1, Integer::sum);
                if (dump) printMessage(packets - 1, m);
                if (m.getField(UasDatalinkTag.PrecisionTimeStamp) instanceof PrecisionTimeStamp) {
                    long ts = ((PrecisionTimeStamp) m.getField(UasDatalinkTag.PrecisionTimeStamp))
                            .getMicroseconds();
                    if (firstTs < 0) firstTs = ts;
                    if (lastTs >= 0 && ts <= lastTs) backwards++;
                    lastTs = ts;
                } else {
                    errors++;
                    addSample(errorSamples, "packet " + (packets - 1) + ": no Precision Time Stamp (tag 2)");
                }
            } catch (Exception e) {
                errors++;
                addSample(errorSamples, "packet " + (packets - 1) + ": " + e.getMessage());
            }
        }

        System.out.println("ST 0601 packets : " + packets);
        System.out.println("parse errors    : " + errors);
        System.out.println("bytes skipped   : " + skipped + " (non-0601 data between packets)");
        if (packets > 1 && lastTs > firstTs) {
            double seconds = (lastTs - firstTs) / 1e6;
            System.out.printf("time span       : %.1f s, %.2f packets/s%n", seconds, (packets - 1) / seconds);
        }
        System.out.println("non-increasing time stamps: " + backwards);
        for (String s : errorSamples) System.out.println("  ! " + s);
        System.out.println("tags seen (packets carrying each):");
        for (Map.Entry<String, Integer> e : tagCounts.entrySet()) {
            System.out.printf("  %-45s %d%n", e.getKey(), e.getValue());
        }
        if (first != null && !dump) {
            System.out.println("first packet:");
            printMessage(0, first);
        }
        if (packets == 0) System.exit(2);
        System.exit(errors > 0 || backwards > 0 ? 1 : 0);
    }

    private static boolean isTransportStream(byte[] d) {
        if (d.length < 188 * 3) return false;
        for (int i = 0; i < 3; i++) if (d[188 * i] != 0x47) return false;
        return true;
    }

    /**
     * The KLV elementary stream of an MPEG-TS: the PID the PMT declares as
     * KLV (stream_type 0x06 with a "KLVA" registration descriptor, or 0x15
     * synchronous metadata, ST 1402), its PES payloads back to back. Not
     * ffmpeg's data muxer: on a public sample it cut the first bytes of
     * every packet's key, so nothing parsed. For stream_type 0x15 the
     * 5-byte metadata AU cell headers stay in; the packet scan skips them.
     */
    private static byte[] klvFromTransportStream(byte[] d) {
        int pmtPid = -1;
        int klvPid = -1;
        for (int at = 0; at + 188 <= d.length && klvPid < 0; at += 188) {
            if (d[at] != 0x47) continue;
            int pid = ((d[at + 1] & 0x1F) << 8) | (d[at + 2] & 0xFF);
            boolean start = (d[at + 1] & 0x40) != 0;
            int p = payloadStart(d, at);
            if (p < 0 || !start) continue;
            p += 1 + (d[p] & 0xFF); // pointer field
            if (pid == 0 && pmtPid < 0 && (d[p] & 0xFF) == 0x00) {
                int sectionEnd = p + 3 + (((d[p + 1] & 0x0F) << 8) | (d[p + 2] & 0xFF)) - 4;
                for (int q = p + 8; q + 4 <= sectionEnd; q += 4) {
                    int program = ((d[q] & 0xFF) << 8) | (d[q + 1] & 0xFF);
                    if (program != 0) {
                        pmtPid = ((d[q + 2] & 0x1F) << 8) | (d[q + 3] & 0xFF);
                        break;
                    }
                }
            } else if (pid == pmtPid && (d[p] & 0xFF) == 0x02) {
                int sectionEnd = Math.min(at + 188,
                        p + 3 + (((d[p + 1] & 0x0F) << 8) | (d[p + 2] & 0xFF)) - 4);
                int programInfo = ((d[p + 10] & 0x0F) << 8) | (d[p + 11] & 0xFF);
                for (int q = p + 12 + programInfo; q + 5 <= sectionEnd; ) {
                    int type = d[q] & 0xFF;
                    int esPid = ((d[q + 1] & 0x1F) << 8) | (d[q + 2] & 0xFF);
                    int infoLen = ((d[q + 3] & 0x0F) << 8) | (d[q + 4] & 0xFF);
                    boolean klva = false;
                    for (int r = q + 5; r + 2 <= q + 5 + infoLen && r + 2 <= d.length; ) {
                        int tag = d[r] & 0xFF;
                        int len = d[r + 1] & 0xFF;
                        if (tag == 0x05 && len >= 4 && d[r + 2] == 'K' && d[r + 3] == 'L'
                                && d[r + 4] == 'V' && d[r + 5] == 'A') klva = true;
                        r += 2 + len;
                    }
                    if (type == 0x15 || (type == 0x06 && klva)) {
                        klvPid = esPid;
                        break;
                    }
                    q += 5 + infoLen;
                }
            }
        }
        if (klvPid < 0) return null;
        System.out.printf("MPEG-TS: KLV on PID 0x%X%n", klvPid);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int at = 0; at + 188 <= d.length; at += 188) {
            if (d[at] != 0x47) continue;
            int pid = ((d[at + 1] & 0x1F) << 8) | (d[at + 2] & 0xFF);
            if (pid != klvPid) continue;
            int p = payloadStart(d, at);
            if (p < 0) continue;
            boolean start = (d[at + 1] & 0x40) != 0;
            if (start && p + 9 <= at + 188 && d[p] == 0 && d[p + 1] == 0 && d[p + 2] == 1) {
                p += 9 + (d[p + 8] & 0xFF); // PES header
            }
            if (p < at + 188) out.write(d, p, at + 188 - p);
        }
        return out.toByteArray();
    }

    // Offset of a TS packet's payload, or -1 if it has none.
    private static int payloadStart(byte[] d, int at) {
        int afc = (d[at + 3] >> 4) & 0x3;
        if (afc == 0 || afc == 2) return -1;
        int p = at + 4;
        if (afc == 3) p += 1 + (d[p] & 0xFF);
        return p < at + 188 ? p : -1;
    }

    private static String label(IKlvKey k) {
        if (k instanceof UasDatalinkTag) {
            UasDatalinkTag t = (UasDatalinkTag) k;
            return String.format("%3d %s", t.getCode(), t.name());
        }
        return k.toString();
    }

    private static void printMessage(int index, UasDatalinkMessage m) {
        System.out.println("  #" + index);
        for (IKlvKey k : m.getIdentifiers()) {
            System.out.printf("    %-45s %s%n", label(k), m.getField(k).getDisplayableValue());
        }
    }

    private static void addSample(List<String> samples, String s) {
        if (samples.size() < 10) samples.add(s);
    }

    // {length, number of length bytes}
    private static int[] berLength(byte[] d, int at) {
        if (at >= d.length) return null;
        int b = d[at] & 0xFF;
        if (b < 0x80) return new int[] { b, 1 };
        int n = b & 0x7F;
        if (n == 0 || n > 4 || at + n >= d.length) return null;
        int len = 0;
        for (int i = 1; i <= n; i++) len = (len << 8) | (d[at + i] & 0xFF);
        return len < 0 ? null : new int[] { len, 1 + n };
    }

    private static boolean startsWith(byte[] d, int at, byte[] key) {
        if (at + key.length > d.length) return false;
        for (int i = 0; i < key.length; i++) if (d[at + i] != key[i]) return false;
        return true;
    }

    private static int indexOf(byte[] d, byte[] key, int from) {
        for (int i = from; i + key.length <= d.length; i++) if (startsWith(d, i, key)) return i;
        return -1;
    }
}
