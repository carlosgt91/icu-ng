package com.carlosgt.icung.capture;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Local MP4 copies of the stream. RootEncoder writes each one to a private
 * temporary file while live (MediaMuxer needs a seekable file); once it is
 * closed it is moved to the phone's shared Movies/ICU NG folder, where the
 * gallery, file managers and USB see it.
 *
 * The ICU NG plugin keeps a copy of fileName() only (it cannot record
 * inside ATAK); keep the two identical.
 */
final class Recordings {

    private static final String TAG = "IcuNgRec";
    static final String FOLDER = "ICU NG";

    private Recordings() {
    }

    /** ICUNG_callsign_20261111_153000Z.mp4, safe as a file name. */
    static String fileName(String callsign, long timeMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String who = callsign == null ? "" : callsign.replaceAll("[^A-Za-z0-9_-]+", "-");
        who = who.replaceAll("^-+|-+$", "");
        return "ICUNG_" + (who.isEmpty() ? "" : who + "_") + f.format(new Date(timeMs)) + "Z.mp4";
    }

    static File tempFile(Context context, String name) {
        File dir = new File(context.getCacheDir(), "icung-recordings");
        if (!dir.isDirectory() && !dir.mkdirs()) Log.w(TAG, "no se pudo crear " + dir);
        return new File(dir, name);
    }

    /**
     * Moves a finished recording to Movies/ICU NG, off the main thread.
     * Empty files (stopped before the first frame) are just deleted.
     */
    static void publish(Context context, File temp, Runnable onDone) {
        new Thread(() -> {
            try {
                if (temp.length() > 0) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        publishMediaStore(context.getContentResolver(), temp);
                    } else {
                        publishFile(context, temp);
                    }
                    Log.i(TAG, "grabacion guardada: " + temp.getName());
                    if (onDone != null) onDone.run();
                }
                if (!temp.delete()) Log.w(TAG, "no se pudo borrar " + temp);
            } catch (IOException | RuntimeException e) {
                // Left in the cache: better than losing it.
                Log.e(TAG, "no se pudo mover la grabacion " + temp, e);
            }
        }, "icung-rec-publish").start();
    }

    // Android 8-9: the shared Movies folder needs the storage permission,
    // which ATAK has and ICU NG Capture does not ask for; without it the
    // file goes to the app's own Movies folder (Android/data/<app>/files).
    private static void publishFile(Context context, File temp) throws IOException {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), FOLDER);
        if (!(dir.isDirectory() || dir.mkdirs()) || !dir.canWrite()) {
            File own = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (own == null) throw new IOException("sin almacenamiento externo");
            dir = new File(own, FOLDER);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdirs " + dir);
        }
        File dest = new File(dir, temp.getName());
        try (InputStream in = new FileInputStream(temp);
             OutputStream out = new FileOutputStream(dest)) {
            copy(in, out);
        } catch (IOException | RuntimeException e) {
            if (!dest.delete()) Log.w(TAG, "no se pudo borrar " + dest);
            throw e;
        }
        MediaScannerConnection.scanFile(context, new String[] { dest.getAbsolutePath() },
                new String[] { "video/mp4" }, null);
    }

    private static void publishMediaStore(ContentResolver resolver, File temp) throws IOException {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Video.Media.DISPLAY_NAME, temp.getName());
        v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER + "/");
        v.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("MediaStore insert");
        try (InputStream in = new FileInputStream(temp);
             OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("MediaStore open");
            copy(in, out);
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        v.clear();
        v.put(MediaStore.Video.Media.IS_PENDING, 0);
        if (resolver.update(uri, v, null, null) != 1) {
            // Left pending it would stay invisible: drop it, the temp file remains.
            resolver.delete(uri, null, null);
            throw new IOException("MediaStore publish");
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
