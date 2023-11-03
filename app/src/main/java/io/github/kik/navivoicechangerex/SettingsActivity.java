package io.github.kik.navivoicechangerex;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        Log.i(this.getClass().toString(), "getSharedPreferences called: " + name);
        try {
            if (App.xposed.isDone()) {
                return App.xposed.get().getRemotePreferences(name);
            }
        } catch (InterruptedException | ExecutionException ignore) {
        }
        return super.getSharedPreferences(name, mode);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            for (var key : List.of("voicevox_engine_url", "voicevox_engine_username", "voicevox_engine_password")) {
                var p = (EditTextPreference)findPreference(key);
                if (p != null) {
                    p.setOnBindEditTextListener(TextView::setSingleLine);
                }
            }

            final var button = findPreference("get_voicevox_voices");
            button.setOnPreferenceClickListener(p -> {
                button.setEnabled(false);
                App.executor.execute(() -> {
                    var prefs = getPreferenceManager().getSharedPreferences();
                    var api = new VoiceVoxEngineApi(
                            prefs.getString("voicevox_engine_url", null),
                            prefs.getString("voicevox_engine_username", null),
                            prefs.getString("voicevox_engine_password", null));
                    try {
                        var players = api.players();
                        for (var player: players) {
                            Log.i(getClass().getName(), "palyer " + player.name);
                            for (var style: player.styles) {
                                Log.i(getClass().getName(), "style " + style.name + " id = " + style.id);
                            }
                        }
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "voicevox api success", Toast.LENGTH_SHORT).show();
                            button.setEnabled(true);
                        });
                    } catch (IOException ioe) {
                        Log.w(getClass().getName(), "players() failed", ioe);
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "voicevox api failed " + ioe.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
                return true;
            });
        }
    }
}