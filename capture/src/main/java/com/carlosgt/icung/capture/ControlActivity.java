package com.carlosgt.icung.capture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent, short-lived: receives start/stop from the plugin and hands
 * them to {@link CaptureService}. Only ATAK may drive it - anything else
 * could otherwise point this phone's camera and microphone at any server.
 * getCallingPackage() is set by the system (the plugin launches with
 * startActivityForResult), so it cannot be faked by the caller.
 *
 * Asks for the camera/microphone/notification permissions the first time,
 * over ATAK, before starting.
 */
public class ControlActivity extends Activity {

    private static final String TAG = "IcuNgCapture";
    private static final int REQUEST_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String caller = getCallingPackage();
        if (!CaptureContract.isAtak(caller)) {
            Log.w(TAG, "orden ignorada, no viene de ATAK: " + caller);
            finish();
            return;
        }
        if (savedInstanceState != null) return; // permission dialog in progress

        String action = getIntent().getAction();
        if (CaptureContract.ACTION_STOP.equals(action)) {
            startService(new Intent(this, CaptureService.class)
                    .setAction(CaptureContract.ACTION_STOP));
            finish();
        } else if (CaptureContract.ACTION_START.equals(action)) {
            String[] missing = missingPermissions();
            if (missing.length == 0) {
                startCapture();
            } else {
                requestPermissions(missing, REQUEST_PERMISSIONS);
            }
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCapture();
        } else {
            CaptureService.reportError(getString(R.string.error_permission));
            finish();
        }
    }

    private void startCapture() {
        // Called while this activity is visible: that is what lets the
        // service hold the camera and microphone once the screen goes off.
        Intent service = new Intent(getIntent())
                .setClass(this, CaptureService.class)
                .setAction(CaptureContract.ACTION_START);
        startForegroundService(service);
        finish();
    }

    private String[] missingPermissions() {
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.CAMERA);
        wanted.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        List<String> missing = new ArrayList<>();
        for (String p : wanted) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        return missing.toArray(new String[0]);
    }
}
