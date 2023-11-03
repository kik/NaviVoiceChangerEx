package io.github.kik.navivoicechangerex;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
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
        private List<VoiceVoxEngineApi.Player> playerList;

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
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "voicevox api success", Toast.LENGTH_SHORT).show();
                            button.setEnabled(true);
                            updatePlayerList(players);
                            var mapper = new ObjectMapper();
                            try {
                                getPreferenceManager().getSharedPreferences()
                                        .edit()
                                        .putString("voicevox_voices", mapper.writeValueAsString(players))
                                        .apply();
                            } catch (JsonProcessingException ignore) {
                            }
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

            var players = getPreferenceManager().getSharedPreferences().getString("voicevox_voices", "[]");
            var mapper = new ObjectMapper();
            try {
                updatePlayerList(mapper.readValue(players, new TypeReference<List<VoiceVoxEngineApi.Player>>() {}));
            } catch (JsonProcessingException ignore) {
            }
        }

        private void updatePlayerList(final List<VoiceVoxEngineApi.Player> players) {
            this.playerList = players;
            final var prefPlayer = (ListPreference)findPreference("player");
            final var prefStyle = (ListPreference)findPreference("style");
            prefPlayer.setEntries(players.stream().map(p -> p.name).toArray(String[]::new));
            prefPlayer.setEntryValues(players.stream().map(p -> p.uuid).toArray(String[]::new));
            prefPlayer.setOnPreferenceChangeListener((pref, v) -> {
                return updateStyle((String)v);
            });
            updateStyle(prefPlayer.getValue());
        }

        private boolean updateStyle(String playerId) {
            final var prefStyle = (ListPreference)findPreference("style");
            var player = this.playerList.stream().filter(p -> p.uuid.equals(playerId)).findFirst();
            if (player.isPresent()) {
                var styles = player.get().styles;
                prefStyle.setEntries(styles.stream().map(s -> s.name).toArray(String[]::new));
                var values = styles.stream().map(s -> Integer.toString(s.id)).toArray(String[]::new);
                prefStyle.setEntryValues(values);
                prefStyle.setValue(Arrays.stream(values).findFirst().orElse(null));
                return true;
            } else {
                return false;
            }
        }
    }
}
