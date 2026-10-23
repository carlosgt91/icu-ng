// Interface between the ICU NG plugin (inside ATAK) and the ICU NG Capture
// app. The same file lives in both modules - change both together.
package com.carlosgt.icung.capture;

import android.view.Surface;
import com.carlosgt.icung.capture.ICaptureListener;

interface ICaptureService {
    /** The listener gets the current status at once, then every change. */
    void register(ICaptureListener listener);
    void unregister(ICaptureListener listener);
    /**
     * Where to draw the camera while live; null to stop. Synchronous on
     * purpose: when it returns, the app no longer draws into the old
     * surface, so the plugin may let it be destroyed.
     */
    void setPreview(in Surface surface, int width, int height);
    /** Swaps the camera of the running stream (0 back, 1 front, 2 USB). */
    oneway void setCamera(int camera);
    /** Latest MISB ST 0601 packet; the app sends it with the next frame. */
    oneway void klv(in byte[] packet);
}
