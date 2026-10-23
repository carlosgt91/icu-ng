package com.carlosgt.atak.icung.plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.util.PdfHelper;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * ICU NG's entry under ATAK's Tool Preferences, and the only way to open
 * the user manual: tak.gov compiles docs/user_manual into
 * assets/usermanual.pdf, which nobody can reach from inside the APK.
 *
 * One PDF per language (usermanual_es.pdf, usermanual_de.pdf...); the one
 * for the device language opens, English (usermanual.pdf) otherwise.
 */
public class IcuNgPreferenceFragment extends PluginPreferenceFragment {

    private static final String USER_GUIDE = "usermanual";

    /** Asset name of the manual in the device language, or the English one. */
    private static String userGuideAsset() {
        String lang = Locale.getDefault().getLanguage();
        String localized = USER_GUIDE + "_" + lang + ".pdf";
        try {
            String[] assets = pluginContext.getAssets().list("");
            if (assets != null && Arrays.asList(assets).contains(localized)) return localized;
        } catch (IOException ignored) {
            // fall back to English
        }
        return USER_GUIDE + ".pdf";
    }

    /**
     * Where it is extracted before a viewer is handed it: one file per
     * language, so switching language never shows the previous one.
     */
    private static String userGuidePath(String asset) {
        return FileSystemUtils.getRoot() + File.separator + "tools" + File.separator + "icung"
                + File.separator + "ICU NG " + asset.replace(USER_GUIDE, "manual");
    }

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
        return getSubTitle(atakString("toolPreferences", "Tool Preferences"),
                pluginContext.getString(R.string.app_name));
    }

    // ATAK's own text, so the breadcrumb is in the language ATAK shows
    // (looked up by name: ATAK keeps its resource names).
    private String atakString(String name, String fallback) {
        try {
            android.content.Context atak = getActivity();
            int id = atak.getResources().getIdentifier(name, "string", atak.getPackageName());
            return id != 0 ? atak.getString(id) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
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
                String asset = userGuideAsset();
                PdfHelper.extractAndShow(pluginContext, getActivity(), asset,
                        manualVersion() ^ asset.hashCode(), userGuidePath(asset), true);
                return true;
            }
        });
    }
}
