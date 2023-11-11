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

    private static class Preferences {
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

    private static Application application;

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
    private static synchronized Preferences getPreferences()
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

        var version = getPackageVersion(param);
        if (version != null) {
            log("version = " + version);
            new GoogleMapsHookBuilder(param).run();
        }
    }

    private Pair<String, Integer> getPackageVersion(@NonNull PackageLoadedParam param) {
        try {
            @SuppressLint("PrivateApi") Class<?> parserCls = param.getClassLoader().loadClass("android.content.pm.PackageParser");
            var parser = parserCls.newInstance();
            File apkPath = new File(param.getApplicationInfo().sourceDir);
            var method = parserCls.getMethod("parsePackage", File.class, int.class);
            var pkg = method.invoke(parser, apkPath, 0);
            var field1 = pkg.getClass().getField("mVersionName");
            var field2 = pkg.getClass().getField("mVersionCode");
            String versionName = (String)field1.get(pkg);
            int versionCode = field2.getInt(pkg);
            return new Pair<>(versionName, versionCode);
        } catch (Throwable e) {
            log("failed to get package version", e);
            return null;
        }
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

    private void onApplicationCapture() {
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

    //
    // MobileMaps/SynthesizeTextの引数
    //
    private static void dumpProto(byte[] obj, String cls, String indent) throws IOException {
        var reader = CodedInputStream.newInstance(obj);
        module.log(indent + "{");
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            int field = WireFormat.getTagFieldNumber(tag);
            int type = WireFormat.getTagWireType(tag);
            switch (type) {
                case WireFormat.WIRETYPE_VARINT: {
                    long value = reader.readRawVarint64();
                    module.log(indent + "  " + field + " VARIANT: " + value);
                    break;
                }
                case WireFormat.WIRETYPE_FIXED64: {
                    long value = reader.readFixed64();
                    module.log(indent + "  " + field + " FIXED64: " + value);
                    break;
                }
                case WireFormat.WIRETYPE_LENGTH_DELIMITED: {
                    if (cls.equals("Item") && field == 1) {
                        module.log(indent + "  " + field + " Str: " + reader.readString());
                        break;
                    }
                    byte[] value = reader.readByteArray();
                    if (cls.equals("Top") && field == 2) {
                        module.log(indent + "  " + field + " Body:");
                        dumpProto(value, "Body", indent + "  ");
                        break;
                    } else if (cls.equals("Body") && field == 2) {
                        module.log(indent + "  " + field + " Element:");
                        dumpProto(value, "Element", indent + "  ");
                        break;
                    } else if (cls.equals("Body") && field == 5) {
                        module.log(indent + "  " + field + " Tail:");
                        dumpProto(value, "Tail", indent + "  ");
                        break;
                    } else if (cls.equals("Element") && field == 1) {
                        module.log(indent + "  " + field + " Item:");
                        dumpProto(value, "Item", indent + "  ");
                        break;
                    }
                    var builder = new StringBuilder();
                    builder.append("[ ");
                    for (byte b : value) {
                        builder.append(String.format("%02X ", b & 0xFF));
                    }
                    builder.append("]");
                    module.log(indent + "  " + field + " LENGTH: " + builder.toString());
                    break;
                }
                case WireFormat.WIRETYPE_FIXED32: {
                    int value = reader.readFixed32();
                    module.log(indent + "  " + field + " FIXED32: " + value);
                    break;
                }
                default:
            }
        }
        module.log(indent + "}");
    }

    private static void dumpTextStructure(Object obj) {
        try {
            Field f = obj.getClass().getField("b");
            Iterable<Byte> structure = (Iterable<Byte>)f.get(obj);
            var it = structure.iterator();
            while (it.hasNext()) {
                var buf1 = new StringBuilder();
                var buf2 = new StringBuilder();
                for (int i = 0; i < 16; i++) {
                    if (it.hasNext()) {
                        byte b = it.next();
                        buf1.append(String.format("%02X ", b & 0xFF));
                        if (i == 7) {
                            buf1.append(' ');
                        }
                        buf2.append(0x20 <= b && b < 0x7F ? (char)b : '.');
                    } else {
                        buf1.append(i == 7 ? "   " : "  ");
                    }
                }
                module.log(buf1.toString() + "    " + buf2.toString());
            }
            var os = new ByteArrayOutputStream();
            for (byte b : structure) {
                os.write(b);
            }
            os.close();
            var array = os.toByteArray();
            dumpProto(array, "Top", "");
        } catch (Exception ignore) {
        }
    }

    @XposedHooker
    static class SynthesizeHook implements XposedInterface.Hooker
    {
        @BeforeInvocation
        public static SynthesizeHook beforeInvocation(BeforeHookCallback callback) {
            module.log("method " + callback.getMember() + " called with " + List.of(callback.getArgs()));
            dumpTextStructure(callback.getArgs()[0]);
            Preferences p = getPreferences();
            if (p.hookNetworkSynthesizer()) {
                if (p.disableNetworkSynthesizer()) {
                    callback.returnAndSkip(false);
                } else {
                    var api = p.getVoiceVoxEngine();
                    var p1 = callback.getArgs()[0];
                    var path = (String)callback.getArgs()[1];
                    boolean ret = false;
                    try {
                        Field f = p1.getClass().getField("a");
                        var text = (String)f.get(p1);
                        text = text.replaceAll(" ", "");
                        text = text.replaceAll("、、", "、");
                        String json = api.audio_query(p.voiceboxStyleId, text);
                        byte[] audio = api.synthesis(p.voiceboxStyleId, json);
                        try (var os = new FileOutputStream(path)) {
                            os.write(audio);
                        }
                        ret = true;
                    } catch (IOException ioe) {
                        module.log("remote TTS failed", ioe);
                    } catch (Exception e) {
                        module.log("hook parameter error", e);
                    }
                    callback.returnAndSkip(ret);
                }
            }
            return new SynthesizeHook();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, SynthesizeHook context) {
            module.log("method " + callback.getMember() + " return with " + callback.getResult());
        }
    }

    //
    // 音声合成キャッシュのIDの生成にボイス名をいれるようにする
    //
    @XposedHooker
    static class SetVoiceNameHook implements XposedInterface.Hooker
    {
        @BeforeInvocation
        public static SetVoiceNameHook beforeInvocation(BeforeHookCallback callback) {
            module.log("method " + callback.getMember() + " called with " + List.of(callback.getArgs()));
            return new SetVoiceNameHook();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, SetVoiceNameHook context) {
            Preferences p = getPreferences();
            if (p.hookNetworkSynthesizer()) {
                // ネットワークTTSを使わないときは、キャッシュにヒットしないようなボイス名にしてなんとかする
                var voiceName = p.disableNetworkSynthesizer() ? "DISABLE-TTS" : "VOICEVOX-" + p.voiceboxStyleId;
                var ret = callback.getResult();
                Arrays.stream(ret.getClass().getDeclaredFields())
                        .filter(f -> f.getType().equals(String.class))
                        .forEach(f -> {
                            try {
                                f.setAccessible(true);
                                f.set(ret, voiceName);
                            } catch (IllegalAccessException e) {
                                module.log("setting ret.voice failed", e);
                            }
                        });
            }
            module.log("method " + callback.getMember() + " return with " + callback.getResult());
        }
    }

    @XposedHooker
    static class ApplicationCaptureHook implements XposedInterface.Hooker
    {
        @BeforeInvocation
        public static ApplicationCaptureHook beforeInvocation(BeforeHookCallback callback) {
            module.log("method " + callback.getMember() + " called with " + List.of(callback.getArgs()));
            return new ApplicationCaptureHook();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, ApplicationCaptureHook context) {
            module.log("method " + callback.getMember() + " return with " + callback.getResult());
            try {
                if (callback.getThisObject() instanceof Application) {
                    synchronized (ModuleMain.class) {
                        if (ModuleMain.application == null) {
                            ModuleMain.application = (Application)callback.getThisObject();
                            ModuleMain.applicationCaptureHookUnhooker.unhook();
                            ModuleMain.module.onApplicationCapture();
                        }
                    }
                }
            } catch (Exception e) {
                module.log("application capture", e);
            }
        }
    }

    @XposedHooker
    static class InspectHook implements XposedInterface.Hooker
    {
        @BeforeInvocation
        public static InspectHook beforeInvocation(BeforeHookCallback callback) {
            module.log("method " + callback.getMember() + " called with " + List.of(callback.getArgs()));
            return new InspectHook();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, InspectHook context) {
            module.log("method " + callback.getMember() + " return with " + callback.getResult());
        }
    }

    @XposedHooker
    private static class InspectCallStackHook implements XposedInterface.Hooker
    {
        @BeforeInvocation
        public static InspectCallStackHook beforeInvocation(BeforeHookCallback callback) {
            module.log("method " + callback.getMember() + " called with " + List.of(callback.getArgs()));
            try {
                throw new Exception();
            } catch (Exception e) {
                module.log("stacktrace", e);
            }
            return new InspectCallStackHook();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, InspectCallStackHook context) {
            module.log("method " + callback.getMember() + " return with " + callback.getResult());
        }
    }
}
