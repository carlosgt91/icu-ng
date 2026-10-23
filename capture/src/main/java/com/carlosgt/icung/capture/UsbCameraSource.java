package com.carlosgt.icung.capture;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.view.Surface;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.pedro.encoder.input.sources.OrientationConfig;
import com.pedro.encoder.input.sources.OrientationForced;
import com.pedro.encoder.input.sources.video.VideoSource;

/**
 * RootEncoder video source backed by a USB (UVC) camera through
 * UVCAndroid. The first UVC device attached is opened; if none is attached
 * yet, the source simply waits - plugging one in later starts the picture
 * without restarting the stream.
 *
 * Android asks the user for USB permission the first time a given camera
 * is opened (UVCAndroid's USBMonitor handles that dialog).
 */
public class UsbCameraSource extends VideoSource {

    private static final String TAG = "IcuNgUsbCamera";

    private ICameraHelper cameraHelper;
    private Surface surface;
    private boolean running;

    @Override
    protected boolean create(int width, int height, int fps, int rotation) {
        // The UVC camera negotiates its own format on open; RootEncoder
        // scales whatever it delivers to the encoder size.
        return true;
    }

    @Override
    public void start(SurfaceTexture surfaceTexture) {
        setSurfaceTexture(surfaceTexture);
        if (running) return;
        surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
        surface = new Surface(surfaceTexture);
        Log.i(TAG, "start: waiting for a UVC camera " + getWidth() + "x" + getHeight());
        cameraHelper = new CameraHelper();
        cameraHelper.setStateCallback(stateCallback);
        running = true;
    }

    @Override
    public void stop() {
        if (cameraHelper != null) {
            if (surface != null) cameraHelper.removeSurface(surface);
            cameraHelper.release();
            cameraHelper = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        running = false;
    }

    @Override
    public void release() {
        // nothing beyond stop()
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public OrientationConfig getOrientationConfig() {
        // A UVC camera has no notion of the phone's orientation: its frames
        // are always landscape, whichever way the phone is held.
        return new OrientationConfig(null, null, OrientationForced.LANDSCAPE);
    }

    private final ICameraHelper.StateCallback stateCallback = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.i(TAG, "UVC attached: " + device.getProductName());
            if (cameraHelper != null) cameraHelper.selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            Log.i(TAG, "UVC device open (permission granted), opening camera");
            if (cameraHelper != null) cameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            Log.i(TAG, "UVC camera open, preview " + (cameraHelper == null ? "-"
                    : cameraHelper.getPreviewSize()) + ", surface " + (surface != null));
            if (cameraHelper == null) return;
            cameraHelper.startPreview();
            if (surface != null) cameraHelper.addSurface(surface, false);
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            Log.i(TAG, "UVC camera closed");
            if (cameraHelper != null && surface != null) cameraHelper.removeSurface(surface);
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
        }

        @Override
        public void onDetach(UsbDevice device) {
            Log.i(TAG, "UVC detached: " + device.getProductName());
        }

        @Override
        public void onCancel(UsbDevice device) {
            Log.w(TAG, "USB permission denied for " + device.getProductName());
        }
    };
}
