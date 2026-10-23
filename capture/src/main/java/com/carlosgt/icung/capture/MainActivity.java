package com.carlosgt.icung.capture;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Launcher entry: there is nothing to configure here - the stream is set
 * up and started from ATAK - so it only explains that and asks for the
 * permissions up front.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText(R.string.main_text);
        text.setTextSize(18);
        text.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        setContentView(text);

        if (savedInstanceState == null) {
            requestPermissions(Build.VERSION.SDK_INT >= 33
                    ? new String[] { Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.POST_NOTIFICATIONS }
                    : new String[] { Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO }, 1);
        }
    }
}
