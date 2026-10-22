package com.carlosgt.atak.icung.plugin;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Full-screen QR scanner: back camera through Camera2, frames decoded with
 * ZXing's core (no zxing-android-embedded, no extra Activity - a Dialog
 * owned by ATAK's context needs no manifest entry and hands the result
 * back with a plain callback).
 *
 * The caller must release its own hold on the camera first (the pane's
 * preview), or the open here fails on devices that allow only one client.
 */
public class QrScanDialog extends Dialog {

    private static final String TAG = "IcuNgQrScan";
    private static final int FRAME_W = 1280;
    private static final int FRAME_H = 720;

    public interface Callback {
        void onQrDecoded(String text);
    }

    private final Callback callback;
    private final MultiFormatReader reader = new MultiFormatReader();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextureView previewView;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private Surface previewSurface;
    private volatile boolean done;

    public QrScanDialog(Context atakContext, String title, Callback callback) {
        super(atakContext, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.callback = callback;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        FrameLayout root = new FrameLayout(atakContext);
        root.setBackgroundColor(Color.BLACK);
        previewView = new TextureView(atakContext);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(atakContext);
        hint.setText(title);
        hint.setTextColor(Color.WHITE);
        hint.setBackgroundColor(Color.argb(140, 0, 0, 0));
        hint.setPadding(24, 16, 24, 16);
        root.addView(hint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        Button cancel = new Button(atakContext);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(v -> dismiss());
        root.addView(cancel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        setContentView(root);
        setOnDismissListener(d -> closeCamera());

        previewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                openCamera(st);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
    }

    private void openCamera(SurfaceTexture st) {
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = backCameraId(manager);
            if (cameraId == null) {
                fail("No hay cámara trasera disponible");
                return;
            }
            cameraThread = new HandlerThread("IcuNgQrScan");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());

            st.setDefaultBufferSize(FRAME_W, FRAME_H);
            previewSurface = new Surface(st);
            imageReader = ImageReader.newInstance(FRAME_W, FRAME_H, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(this::onFrame, cameraHandler);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    fail("No se pudo abrir la cámara (error " + error + ")");
                }
            }, cameraHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.w(TAG, "openCamera", e);
            fail("No se pudo abrir la cámara");
        }
    }

    @SuppressWarnings("deprecation") // the List<Surface> overload is fine down to API 21
    private void startSession() {
        try {
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession s) {
                            if (cameraDevice == null) return;
                            session = s;
                            try {
                                CaptureRequest.Builder req = cameraDevice
                                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                req.addTarget(previewSurface);
                                req.addTarget(imageReader.getSurface());
                                req.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                s.setRepeatingRequest(req.build(), null, cameraHandler);
                            } catch (CameraAccessException | IllegalStateException e) {
                                Log.w(TAG, "setRepeatingRequest", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession s) {
                            fail("No se pudo configurar la cámara");
                        }
                    }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.w(TAG, "createCaptureSession", e);
            fail("No se pudo configurar la cámara");
        }
    }

    // On the camera thread. Only the Y plane is needed: QR decoding is
    // luminance-only.
    private void onFrame(ImageReader r) {
        Image image = r.acquireLatestImage();
        if (image == null) return;
        try {
            if (done) return;
            Image.Plane y = image.getPlanes()[0];
            int w = image.getWidth();
            int h = image.getHeight();
            int rowStride = y.getRowStride();
            ByteBuffer buf = y.getBuffer();
            byte[] data = new byte[rowStride * h];
            buf.get(data, 0, Math.min(data.length, buf.remaining()));

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, rowStride, h, 0, 0, w, h, false);
            Result result;
            try {
                result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
            } catch (NotFoundException e) {
                return; // no QR in this frame
            } finally {
                reader.reset();
            }
            done = true;
            String text = result.getText();
            mainHandler.post(() -> {
                dismiss();
                callback.onQrDecoded(text);
            });
        } finally {
            image.close();
        }
    }

    private static String backCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        String[] all = manager.getCameraIdList();
        return all.length > 0 ? all[0] : null;
    }

    private void fail(String message) {
        mainHandler.post(() -> {
            android.widget.Toast.makeText(getContext(), message,
                    android.widget.Toast.LENGTH_LONG).show();
            dismiss();
        });
    }

    private void closeCamera() {
        if (session != null) {
            session.close();
            session = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
    }
}
