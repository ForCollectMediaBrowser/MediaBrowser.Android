package com.mb.android.ui.main;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.mb.android.R;
import com.mb.android.logging.AppLogger;
import com.mb.android.widget.customswitchpreference.CustomSwitchPreference;

/**
 * Created by Mark on 12/12/13.
 */
public class MainSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Class Constructor
     */
    public MainSettingsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        AppLogger.getLogger().Info("onSharedPreferenceChanged");
        AppLogger.getLogger().Info("key = " + key);
        if (key.equals("pref_enable_external_player")) {
            final CustomSwitchPreference csp = (CustomSwitchPreference) getPreferenceScreen().findPreference(key);

            if (csp.isChecked()) {

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getResources().getString(R.string.warning_string))
                        .setMessage(getResources().getString(R.string.external_player_warning))
                        .setPositiveButton(getResources().getString(R.string.proceed_button), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        })
                        .setNegativeButton(getResources().getString(R.string.cancel_button), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                                csp.setChecked(false);

                            }
                        })
                        .show();

            } else {

            }
        } else if (key.equals("pref_debug_logging_enabled")) {
            final CustomSwitchPreference csp = (CustomSwitchPreference) getPreferenceScreen().findPreference(key);
            if (csp == null) return;
            AppLogger.setDebugLoggingEnabled(csp.isChecked());
        }

    }
}
