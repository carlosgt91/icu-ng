package com.carlosgt.atak.icung.plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.util.PdfHelper;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;

/**
 * ICU NG's entry under ATAK's Tool Preferences, and the only way to open
 * the user manual: tak.gov compiles docs/user_manual into
 * assets/usermanual.pdf, which nobody can reach from inside the APK.
 */
public class IcuNgPreferenceFragment extends PluginPreferenceFragment {

    private static final String USER_GUIDE = "usermanual.pdf";

    /** What the operator sees in a file picker, so named for what it is. */
    private static final String USER_GUIDE_PATH = FileSystemUtils.getRoot()
            + File.separator + "tools" + File.separator + "icung"
            + File.separator + "Manual ICU NG.pdf";

    private static Context pluginContext;

    /**
     * Cache key for PdfHelper, which re-extracts the PDF only when it
     * changes. From versionName, so a rebuild of the same version for
     * another ATAK target refreshes it too.
     */
    private static long manualVersion() {
        try {
            return pluginContext.getString(R.string.versionName).hashCode() & 0xFFFFFFFFL;
        } catch (RuntimeException noResource) {
            // never fail to open the manual over its own cache key
            return System.currentTimeMillis();
        }
    }

    public IcuNgPreferenceFragment() {
        super(pluginContext, R.xml.preferences);
    }

    @SuppressLint("ValidFragment")
    public IcuNgPreferenceFragment(Context context) {
        super(context, R.xml.preferences);
        pluginContext = context;
    }

    @Override
    public String getSubTitle() {
        return getSubTitle("Tool Preferences", pluginContext.getString(R.string.app_name));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Preference manual = findPreference("manual");
        if (manual == null) return;
        // An anonymous class, not a lambda: lambdas break under release proguard.
        manual.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PdfHelper.extractAndShow(pluginContext, getActivity(), USER_GUIDE,
                        manualVersion(), USER_GUIDE_PATH, true);
                return true;
            }
        });
    }
}
