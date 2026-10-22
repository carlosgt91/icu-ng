package com.carlosgt.atak.icung.plugin;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * REC button artwork, drawn in code and handed to ATAK as base64:// URIs
 * (both Icon and MapDataRef resolve that scheme). Avoids android.resource://
 * URIs, which ATAK would try to resolve against its own package, not the
 * plugin's.
 */
final class RecIcons {

    static final int COLOR_IDLE = Color.rgb(0xD3, 0x2F, 0x2F);
    static final int COLOR_CONNECTING = Color.rgb(0xFF, 0xC1, 0x07);
    static final int COLOR_LIVE = Color.rgb(0xFF, 0x17, 0x44);

    private RecIcons() {
    }

    /** Round REC button: dark disc, white ring, coloured dot (or a stop square while live). */
    static String button(int sizePx, int dotColor, boolean live, int dotAlpha) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = sizePx / 2f;

        p.setColor(Color.argb(170, 0, 0, 0));
        c.drawCircle(r, r, r, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sizePx * 0.06f);
        p.setColor(Color.WHITE);
        c.drawCircle(r, r, r - sizePx * 0.06f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(dotColor);
        p.setAlpha(dotAlpha);
        if (live) {
            float h = sizePx * 0.2f;
            c.drawRoundRect(new RectF(r - h, r - h, r + h, r + h), h * 0.25f, h * 0.25f, p);
        } else {
            c.drawCircle(r, r, sizePx * 0.28f, p);
        }
        return encode(bmp);
    }

    static String encode(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        return "base64://" + Base64.encodeToString(out.toByteArray(),
                Base64.NO_WRAP | Base64.URL_SAFE);
    }
}
