package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.kik.navivoicechangerex.VoiceVoxEngineApi;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

public class ModuleMain extends XposedModule {
    public static ModuleMain module;

    static class Preferences {
        @NonNull
        public final String googleMapNetworkTtsConfig;
        @NonNull
        public final String voicevoxEngineUrl;
        @NonNull
        public final String voicevoxUsername;
        @NonNull
        public final String voiceboxPassword;
        public final int voiceboxStyleId;

        public Preferences()
        {
            googleMapNetworkTtsConfig = "default";
            voicevoxEngineUrl = "";
            voicevoxUsername = "";
            voiceboxPassword = "";
            voiceboxStyleId = 0;
        }

        public Preferences(@NonNull SharedPreferences prefs, @NonNull Preferences old)
        {
            googleMapNetworkTtsConfig = prefs.getString("google_map_network_tts", old.googleMapNetworkTtsConfig);
            voicevoxEngineUrl = prefs.getString("voicevox_engine_url", old.voicevoxEngineUrl);
            voicevoxUsername = prefs.getString("voicevox_engine_username", old.voicevoxUsername);
            voiceboxPassword = prefs.getString("voicevox_engine_password", old.voiceboxPassword);
            voiceboxStyleId = Integer.parseInt(prefs.getString("style", Integer.toString(old.voiceboxStyleId)));
        }

        public boolean hookNetworkSynthesizer()
        {
            return !googleMapNetworkTtsConfig.equals("default");
        }

        public  boolean disableNetworkSynthesizer()
        {
            return googleMapNetworkTtsConfig.equals("disable");
        }

        @NonNull
        public VoiceVoxEngineApi getVoiceVoxEngine()
        {
            return new VoiceVoxEngineApi(voicevoxEngineUrl, voicevoxUsername, voiceboxPassword);
        }
    }

    @NonNull
    private static Preferences preferences = new Preferences();

    static MethodUnhooker<Method> applicationCaptureHookUnhooker;

    public static Application application;

    public ModuleMain(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base, param);
        log("ModuleMain at " + param.getProcessName());
        module = this;
        var prefs = getRemotePreferences("io.github.kik.navivoicechangerex_preferences");
        loadSharedPreferences(prefs);
        prefs.registerOnSharedPreferenceChangeListener((p, s) -> {
            loadSharedPreferences(p);
        });
    }

    private static synchronized void loadSharedPreferences(@NonNull SharedPreferences prefs) {
        preferences = new Preferences(prefs, preferences);
    }

    @NonNull
    static synchronized Preferences getPreferences()
    {
        return preferences;
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        super.onPackageLoaded(param);
        log("onPackageLoaded: " + param.getPackageName());
        log("param classloader is " + param.getClassLoader());
        log("module apk path: " + this.getApplicationInfo().sourceDir);
        log("----------");

        if (!param.isFirstPackage()) return;

        new GoogleMapsHookBuilder(param).run();
    }

    private File getCannedMessageBundle() {
        File base = application.getExternalFilesDir(null);
        if (base.getName().equals("files")) {
            base = base.getParentFile();
        }
        log("base: " + base);
        // testdata/voice/ja_JP6adc1df/voice_instructions_unitless.zip
        File voice = new File(base, "testdata/voice");
        if (!voice.isDirectory()) {
            log("voice directory not found: " + voice);
            return null;
        }
        File[] files = voice.listFiles(pathname -> pathname.getName().startsWith("ja_JP"));
        if (files == null) {
            log("failed to list voice directory: " + voice);
            return null;
        }
        for (var dir : files) {
            File bundle = new File(dir, "voice_instructions_unitless.zip");
            if (bundle.exists()) {
                return bundle;
            }
        }
        log("failed to find list canned message bundle: " + voice);
        return null;
    }

    private boolean isIdenticalFile(FileDescriptor f, File g) throws IOException {
        try (var isf = new FileInputStream(f)) {
            try (var isg = new FileInputStream(g)) {
                var isfb = new BufferedInputStream(isf);
                var isgb = new BufferedInputStream(isg);
                while (true) {
                    int c = isfb.read();
                    int d = isgb.read();
                    if (c != d) {
                        return false;
                    }
                    if (c < 0) {
                        break;
                    }
                }
                return true;
            }
        }
    }

    private void cleanDirectory(File dir) {
        File[] files = dir.listFiles(pathname -> pathname.getName().startsWith("._"));
        if (files != null) {
            for (var file : files) {
                if (!file.delete()) {
                    log("failed to delete file: " + file);
                }
            }
        }
    }

    private static void copyFile(FileDescriptor from, File to) throws IOException {
        try (var is = new FileInputStream(from)) {
            try (var os = new FileOutputStream(to)) {
                byte[] buffer = new byte[4096];
                while (true) {
                    int len = is.read(buffer);
                    if (len < 0) {
                        break;
                    }
                    os.write(buffer, 0, len);
                }
            }
        }
    }

    private boolean needToUpdateCannedMessageBundle(File installed)
    {
        try (var installing = openRemoteFile("voice_instructions_unitless.zip")) {
            if (installed != null && !isIdenticalFile(installing.getFileDescriptor(), installed)) {
                // すでにvoice_instructions_unitless.zipが作られていて、変更がある場合だけ更新する
                return true;
            }
        } catch (FileNotFoundException ignore) {
        } catch (IOException ioe) {
            log("needCannedMessageBundleUpdate", ioe);
        }
        return false;
    }

    void onApplicationCapture() {
        File installed = getCannedMessageBundle();
        if (needToUpdateCannedMessageBundle(installed)) {
            try (var installing = openRemoteFile("voice_instructions_unitless.zip")) {
                log("installing new canned message bundle into: " + installed);
                // testdata/voice/ja_JP6adc1df/._GPS_LOST.mp3 みたいに残ってるファイルを消す
                cleanDirectory(installed.getParentFile());
                copyFile(installing.getFileDescriptor(), installed);
            } catch (FileNotFoundException ignore) {
            } catch (IOException ioe) {
                log("install canned message bundle failed", ioe);
            }
        }
    }


}
