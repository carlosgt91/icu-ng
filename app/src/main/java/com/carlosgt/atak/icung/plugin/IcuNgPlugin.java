package com.carlosgt.atak.icung.plugin;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.manager.VideoManager;

import java.util.Locale;

public class IcuNgPlugin implements IPlugin, StreamController.Listener {

    IServiceController serviceController;
    Context pluginContext;
    Context atakContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    Pane templatePane;
    SurfaceView previewView;
    EditText urlInput;
    Button streamToggleButton;
    Button qrButton;
    Spinner protocolSpinner;
    Spinner cameraSpinner;
    CheckBox trustAllCertsCheckbox;
    CheckBox klvCheckbox;
    View klvMarkingRow;
    Spinner klvClassificationSpinner;
    EditText klvCountryInput;
    EditText klvMissionInput;
    View srtEncryptionRow;
    EditText srtPassphraseInput;
    Spinner srtEncryptionSpinner;
    View mainPage;
    View qualityPage;
    Button qualityBackButton;
    Spinner codecSpinner;
    Spinner resolutionSpinner;
    Spinner fpsSpinner;
    Spinner bitrateSpinner;
    CheckBox adaptiveBitrateCheckbox;
    CheckBox microphoneCheckbox;
    TextView statusText;
    boolean suppressProtocolListener;
    int lastValidProtocolPosition;
    StreamSettings settings;

    SelfVideoDetailHandler videoDetailHandler;
    SelfSensorFovController sensorFovController;
    StreamController streamController;
    RecButtonWidget recButton;
    SelfMarkerRadialMenu radialMenu;

    public IcuNgPlugin(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        // obtain the UI service
        uiService = serviceController.getService(IHostUIService.class);

        // initialize the toolbar button for the plugin

        // create the button and set the identifier to be well known
        // if you fail to do this, the toolbar configuration will never
        // be able to find it again after the user moves the icon.
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_toolbar),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                }).setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        // the plugin is starting, add the button to the toolbar
        if (uiService == null)
            return;

        uiService.addToolbarItem(toolbarItem);

        MapView mapView = MapView.getMapView();
        atakContext = mapView.getContext();

        sensorFovController = new SelfSensorFovController();
        sensorFovController.start();

        streamController = new StreamController(atakContext, pluginContext,
                VideoManager.getInstance(), sensorFovController);

        String selfUid = mapView.getSelfMarker().getUID();
        videoDetailHandler = new SelfVideoDetailHandler(
                selfUid,
                this::isStreamingWithFix,
                () -> streamController.getConnectionEntry());
        CotDetailManager.getInstance().registerHandler(videoDetailHandler);

        recButton = new RecButtonWidget(mapView, streamController, this::showPane);
        recButton.show();

        radialMenu = new SelfMarkerRadialMenu(mapView, streamController);
        radialMenu.register();

        registerPreferences();
    }

    private static final String PREFS_KEY = "icung_preferences";

    // Guarded: an ATAK build without ToolsPreferenceFragment costs the
    // manual, not the whole plugin.
    private void registerPreferences() {
        try {
            com.atakmap.app.preferences.ToolsPreferenceFragment.register(
                    new com.atakmap.app.preferences.ToolsPreferenceFragment.ToolPreference(
                            pluginContext.getString(R.string.app_name),
                            pluginContext.getString(R.string.prefs_summary),
                            PREFS_KEY,
                            // ic_toolbar: this row sits on ATAK's dark UI
                            pluginContext.getResources().getDrawable(R.drawable.ic_toolbar),
                            new IcuNgPreferenceFragment(pluginContext)));
        } catch (LinkageError | RuntimeException notThisBuild) {
            android.util.Log.w("IcuNgPlugin", "no se pudieron registrar las preferencias", notThisBuild);
        }
    }

    private void unregisterPreferences() {
        try {
            com.atakmap.app.preferences.ToolsPreferenceFragment.unregister(PREFS_KEY);
        } catch (LinkageError | RuntimeException notThisBuild) {
            android.util.Log.w("IcuNgPlugin", "no se pudieron quitar las preferencias", notThisBuild);
        }
    }

    private boolean isStreamingWithFix() {
        return streamController != null && streamController.isStreaming()
                && MapView.getMapView().getSelfMarker().getPoint().isValid();
    }

    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);
        unregisterPreferences();

        if (templatePane != null && uiService.isPaneVisible(templatePane)) {
            uiService.closePane(templatePane);
        }
        if (radialMenu != null) {
            radialMenu.dispose();
        }
        if (recButton != null) {
            recButton.dispose();
        }
        if (streamController != null) {
            streamController.dispose();
        }
        if (videoDetailHandler != null) {
            CotDetailManager.getInstance().unregisterHandler(videoDetailHandler);
        }
        try {
            com.atakmap.android.cot.CotMapComponent cmc = com.atakmap.android.cot.CotMapComponent.getInstance();
            if (cmc != null) {
                cmc.removeAdditionalDetail("__video");
            }
        } catch (Exception | LinkageError ignored) {}
        if (sensorFovController != null) {
            sensorFovController.stop();
        }
    }

    private void setStatus(String text, int colorRes) {
        if (statusText == null) return;
        statusText.setText(text);
        statusText.setTextColor(ContextCompat.getColor(pluginContext, colorRes));
    }

    @Override
    public void onStreamStateChanged(StreamController.State state, String message) {
        switch (state) {
            case CONNECTING:
                setStatus(pluginContext.getString(R.string.status_connecting),
                        R.color.status_connecting);
                break;
            case STREAMING:
                setStatus(pluginContext.getString(R.string.status_streaming),
                        R.color.status_streaming);
                break;
            case ERROR:
                setStatus(message, R.color.status_error);
                break;
            default:
                setStatus(pluginContext.getString(R.string.status_idle),
                        R.color.status_neutral);
                break;
        }
        boolean live = state == StreamController.State.STREAMING
                || state == StreamController.State.CONNECTING;
        if (streamToggleButton != null) {
            streamToggleButton.setText(live ? R.string.stop_stream : R.string.start_stream);
            streamToggleButton.setBackground(pluginContext.getDrawable(
                    live ? R.drawable.btn_stop : R.drawable.btn_primary));
        }
        // While a stream runs, its settings are fixed - show those, not
        // whatever was left half-typed, and don't let them be edited.
        setInputsEnabled(!live);
        StreamSettings active = streamController.getActiveSettings();
        if (active != null) {
            fillInputs(active);
        }
    }

    private void setInputsEnabled(boolean enabled) {
        if (urlInput == null) return;
        urlInput.setEnabled(enabled);
        protocolSpinner.setEnabled(enabled);
        qrButton.setEnabled(enabled);
        trustAllCertsCheckbox.setEnabled(enabled);
        klvCheckbox.setEnabled(enabled);
        klvClassificationSpinner.setEnabled(enabled);
        klvCountryInput.setEnabled(enabled);
        klvMissionInput.setEnabled(enabled);
        srtPassphraseInput.setEnabled(enabled);
        srtEncryptionSpinner.setEnabled(enabled);
        // The encoder is built with these; bitrate and microphone stay live.
        if (codecSpinner != null) codecSpinner.setEnabled(enabled);
        resolutionSpinner.setEnabled(enabled);
        fpsSpinner.setEnabled(enabled);
    }

    private void fillInputs(StreamSettings s) {
        suppressProtocolListener = true;
        protocolSpinner.setSelection(s.protocol);
        suppressProtocolListener = false;
        lastValidProtocolPosition = s.protocol;
        urlInput.setText(s.url);
        trustAllCertsCheckbox.setChecked(s.trustAllCerts);
        klvCheckbox.setChecked(s.klv);
        klvClassificationSpinner.setSelection(s.classificationIndex);
        klvCountryInput.setText(s.country);
        klvMissionInput.setText(s.missionId);
        srtPassphraseInput.setText(s.srtPassphrase);
        srtEncryptionSpinner.setSelection(s.srtEncryptionIndex);
        cameraSpinner.setSelection(s.camera);
        if (codecSpinner != null) codecSpinner.setSelection(s.codec);
        resolutionSpinner.setSelection(s.resolutionIndex);
        fpsSpinner.setSelection(s.fpsIndex);
        bitrateSpinner.setSelection(s.bitrateIndex);
        adaptiveBitrateCheckbox.setChecked(s.adaptiveBitrate);
        microphoneCheckbox.setChecked(s.microphone);
        updateProtocolRows(s.protocol);
    }

    private void updateProtocolRows(int protocol) {
        trustAllCertsCheckbox.setVisibility(
                protocol == StreamSettings.PROTOCOL_RTSPS ? View.VISIBLE : View.GONE);
        srtEncryptionRow.setVisibility(
                protocol == StreamSettings.PROTOCOL_SRT ? View.VISIBLE : View.GONE);
        // KLV is only implemented on the RTSP client
        klvCheckbox.setVisibility(
                protocol == StreamSettings.PROTOCOL_SRT ? View.GONE : View.VISIBLE);
        updateKlvMarkingRow();
    }

    private void updateKlvMarkingRow() {
        klvMarkingRow.setVisibility(klvCheckbox.getVisibility() == View.VISIBLE
                && klvCheckbox.isChecked() ? View.VISIBLE : View.GONE);
    }

    // Every edit in the pane is written straight to the persisted settings,
    // so the REC map button always streams with what the pane shows.
    private void saveInputs() {
        if (settings == null || urlInput == null || streamController.isStreaming()) return;
        settings.protocol = protocolSpinner.getSelectedItemPosition();
        settings.url = urlInput.getText().toString().trim();
        settings.trustAllCerts = trustAllCertsCheckbox.isChecked();
        settings.klv = klvCheckbox.isChecked();
        settings.classificationIndex = klvClassificationSpinner.getSelectedItemPosition();
        settings.country = klvCountryInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        settings.missionId = klvMissionInput.getText().toString().trim();
        settings.srtPassphrase = srtPassphraseInput.getText().toString();
        settings.srtEncryptionIndex = srtEncryptionSpinner.getSelectedItemPosition();
        settings.camera = cameraSpinner.getSelectedItemPosition();
        if (codecSpinner != null) settings.codec = codecSpinner.getSelectedItemPosition();
        settings.resolutionIndex = resolutionSpinner.getSelectedItemPosition();
        settings.fpsIndex = fpsSpinner.getSelectedItemPosition();
        settings.save(atakContext);
    }

    // Bitrate, adaptive bitrate and microphone: saved and applied at once,
    // also mid-stream.
    private void onLiveOptionChanged() {
        if (settings == null || bitrateSpinner == null) return;
        int bitrate = bitrateSpinner.getSelectedItemPosition();
        boolean adaptive = adaptiveBitrateCheckbox.isChecked();
        boolean mic = microphoneCheckbox.isChecked();
        if (bitrate == settings.bitrateIndex && adaptive == settings.adaptiveBitrate
                && mic == settings.microphone) return;
        settings.bitrateIndex = bitrate;
        settings.adaptiveBitrate = adaptive;
        settings.microphone = mic;
        settings.save(atakContext);
        streamController.applyLiveOptions(settings);
    }

    private void onProtocolSelected(int position) {
        if (!streamController.onProtocolChanged(position)) {
            suppressProtocolListener = true;
            protocolSpinner.setSelection(lastValidProtocolPosition);
            suppressProtocolListener = false;
            setStatus(pluginContext.getString(
                    R.string.status_error_change_protocol_while_streaming),
                    R.color.status_error);
            return;
        }
        lastValidProtocolPosition = position;
        updateProtocolRows(position);
        urlInput.setText(StreamSettings.rewriteUrlScheme(urlInput.getText().toString(), position));
        saveInputs();
    }

    // The plugin theme gives spinner drop-down rows white text on a white
    // popup; style the rows explicitly so they are readable.
    private ArrayAdapter<CharSequence> spinnerAdapter(int arrayRes) {
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(pluginContext,
                android.R.layout.simple_spinner_item,
                pluginContext.getResources().getTextArray(arrayRes)) {
            @Override
            public View getDropDownView(int position, View convertView,
                    android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(0xFF000000);
                v.setBackgroundColor(0xFFEEEEEE);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void scanQr() {
        if (streamController.isStreaming()) {
            setStatus(pluginContext.getString(R.string.qr_error_streaming), R.color.status_error);
            return;
        }
        // The scanner needs the camera the pane preview is holding.
        final SurfaceView preview = previewView;
        streamController.detachPreview(preview);
        QrScanDialog dialog = new QrScanDialog(atakContext, pluginContext,
                pluginContext.getString(R.string.qr_scan_title), this::applyQr);
        dialog.setOnDismissListener(d -> {
            if (preview != null && preview == previewView
                    && preview.getHolder().getSurface().isValid()) {
                streamController.attachPreview(preview);
            }
        });
        dialog.show();
    }

    private String qrErrorText(QrStreamUrl.InvalidQrException e) {
        switch (e.reason) {
            case EMPTY:
                return pluginContext.getString(R.string.qr_error_empty);
            case NO_HOST:
                return pluginContext.getString(R.string.qr_error_no_host);
            case RTMP:
                return pluginContext.getString(R.string.qr_error_rtmp);
            case UNSUPPORTED_PROTOCOL:
                return pluginContext.getString(R.string.qr_error_protocol, e.detail);
            case SRT_NO_STREAMID:
                return pluginContext.getString(R.string.qr_error_srt_streamid);
            case SRT_PASSPHRASE_LENGTH:
                return pluginContext.getString(R.string.qr_error_srt_passphrase);
            case NOT_A_URL:
            default:
                return pluginContext.getString(R.string.qr_error_not_url);
        }
    }

    private void applyQr(String text) {
        QrStreamUrl.Result qr;
        try {
            qr = QrStreamUrl.parse(text);
        } catch (QrStreamUrl.InvalidQrException e) {
            setStatus(qrErrorText(e), R.color.status_error);
            return;
        }
        if (settings == null || urlInput == null) return; // pane closed meanwhile
        settings.protocol = qr.protocol;
        settings.url = qr.url;
        if (qr.srtPassphrase != null) settings.srtPassphrase = qr.srtPassphrase;
        settings.save(atakContext);
        fillInputs(settings);
        streamController.onProtocolChanged(qr.protocol);
        setStatus(qr.name == null
                ? pluginContext.getString(R.string.qr_loaded)
                : pluginContext.getString(R.string.qr_loaded_named, qr.name),
                R.color.status_streaming);
    }

    private void tearDownPane() {
        if (templatePane == null) return;
        templatePane = null;
        streamController.removeListener(this);
        streamController.detachPreview(previewView);
        previewView = null;
        urlInput = null;
        statusText = null;
        streamToggleButton = null;
        mainPage = null;
        qualityPage = null;
        qualityBackButton = null;
        codecSpinner = null;
    }

    private void showPane() {
        // instantiate the plugin view if necessary
        if (templatePane == null) {
            // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
            View paneView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.main_layout, null);

            previewView = paneView.findViewById(R.id.preview_view);
            urlInput = paneView.findViewById(R.id.rtsp_url_input);
            streamToggleButton = paneView.findViewById(R.id.stream_toggle_button);
            qrButton = paneView.findViewById(R.id.qr_button);
            protocolSpinner = paneView.findViewById(R.id.protocol_spinner);
            cameraSpinner = paneView.findViewById(R.id.camera_spinner);
            trustAllCertsCheckbox = paneView.findViewById(R.id.trust_all_certs_checkbox);
            klvCheckbox = paneView.findViewById(R.id.klv_checkbox);
            klvMarkingRow = paneView.findViewById(R.id.klv_marking_row);
            klvClassificationSpinner = paneView.findViewById(R.id.klv_classification_spinner);
            klvCountryInput = paneView.findViewById(R.id.klv_country_input);
            klvMissionInput = paneView.findViewById(R.id.klv_mission_input);
            srtEncryptionRow = paneView.findViewById(R.id.srt_encryption_row);
            srtPassphraseInput = paneView.findViewById(R.id.srt_passphrase_input);
            srtEncryptionSpinner = paneView.findViewById(R.id.srt_encryption_spinner);
            mainPage = paneView.findViewById(R.id.main_page);
            qualityPage = paneView.findViewById(R.id.quality_page);
            qualityBackButton = paneView.findViewById(R.id.quality_back_button);

            paneView.findViewById(R.id.quality_button).setOnClickListener(v -> {
                mainPage.setVisibility(View.GONE);
                qualityPage.setVisibility(View.VISIBLE);
            });
            qualityBackButton.setOnClickListener(v -> {
                qualityPage.setVisibility(View.GONE);
                mainPage.setVisibility(View.VISIBLE);
            });

            codecSpinner = paneView.findViewById(R.id.codec_spinner);
            resolutionSpinner = paneView.findViewById(R.id.resolution_spinner);
            fpsSpinner = paneView.findViewById(R.id.fps_spinner);
            bitrateSpinner = paneView.findViewById(R.id.bitrate_spinner);
            adaptiveBitrateCheckbox = paneView.findViewById(R.id.adaptive_bitrate_checkbox);
            microphoneCheckbox = paneView.findViewById(R.id.microphone_checkbox);
            statusText = paneView.findViewById(R.id.status_text);
            ((TextView) paneView.findViewById(R.id.mode_text)).setText(
                    streamController.usesCaptureApp()
                            ? R.string.mode_capture_app : R.string.mode_plugin);

            ArrayAdapter<CharSequence> protocolAdapter = spinnerAdapter(R.array.protocol_entries);
            protocolSpinner.setAdapter(protocolAdapter);

            ArrayAdapter<CharSequence> encryptionAdapter = spinnerAdapter(R.array.srt_encryption_entries);
            srtEncryptionSpinner.setAdapter(encryptionAdapter);
            klvClassificationSpinner.setAdapter(spinnerAdapter(R.array.classification_entries));

            ArrayAdapter<CharSequence> cameraAdapter = spinnerAdapter(R.array.camera_entries);
            cameraSpinner.setAdapter(cameraAdapter);
            codecSpinner.setAdapter(spinnerAdapter(R.array.codec_entries));
            resolutionSpinner.setAdapter(spinnerAdapter(R.array.resolution_entries));
            fpsSpinner.setAdapter(spinnerAdapter(R.array.fps_entries));
            bitrateSpinner.setAdapter(spinnerAdapter(R.array.bitrate_entries));

            settings = StreamSettings.load(atakContext);
            fillInputs(settings);

            protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (suppressProtocolListener || position == lastValidProtocolPosition) return;
                    onProtocolSelected(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            });
            // The camera is the one setting that can change mid-stream.
            cameraSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == settings.camera) return;
                    settings.camera = position;
                    settings.save(atakContext);
                    streamController.setCamera(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            });
            AdapterView.OnItemSelectedListener saveOnSelect = new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    saveInputs();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            };
            srtEncryptionSpinner.setOnItemSelectedListener(saveOnSelect);
            klvClassificationSpinner.setOnItemSelectedListener(saveOnSelect);
            codecSpinner.setOnItemSelectedListener(saveOnSelect);
            resolutionSpinner.setOnItemSelectedListener(saveOnSelect);
            fpsSpinner.setOnItemSelectedListener(saveOnSelect);
            bitrateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    onLiveOptionChanged();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            });
            adaptiveBitrateCheckbox.setOnCheckedChangeListener((b, checked) -> onLiveOptionChanged());
            microphoneCheckbox.setOnCheckedChangeListener((b, checked) -> onLiveOptionChanged());
            TextWatcher saveOnEdit = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    saveInputs();
                }
            };
            urlInput.addTextChangedListener(saveOnEdit);
            srtPassphraseInput.addTextChangedListener(saveOnEdit);
            klvCountryInput.addTextChangedListener(saveOnEdit);
            klvMissionInput.addTextChangedListener(saveOnEdit);
            trustAllCertsCheckbox.setOnCheckedChangeListener((b, checked) -> saveInputs());
            klvCheckbox.setOnCheckedChangeListener((b, checked) -> {
                updateKlvMarkingRow();
                saveInputs();
            });

            qrButton.setOnClickListener(v -> scanQr());

            streamToggleButton.setOnClickListener(v -> {
                saveInputs();
                streamController.toggle();
            });

            // Registered after fillInputs(): reflects the current stream
            // state immediately, including a stream started from the map.
            streamController.addListener(this);

            // RootEncoder needs the SurfaceView's real Surface to exist before
            // startPreview() can touch it - Android creates it asynchronously
            // on a later layout pass, not when the view is inflated.
            final SurfaceView surface = previewView;
            previewView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    streamController.attachPreview(surface);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    // no-op
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    streamController.detachPreview(surface);
                }
            });

            templatePane = new PaneBuilder(paneView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        // if the plugin pane is not visible, show it!
        if (!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, new IHostUIService.IPaneLifecycleListener() {
                @Override
                public void onPaneVisible(boolean visible) {
                    // ATAK reports the pane invisible for a moment while it
                    // re-lays it out on rotation, then shows the same view
                    // again without calling showPane(): tearing down here
                    // left that view wired to nulled fields. The preview
                    // follows its Surface on its own; only a real close
                    // tears the pane down.
                }

                @Override
                public void onPaneClose() {
                    tearDownPane();
                }
            });
        }
    }
}
