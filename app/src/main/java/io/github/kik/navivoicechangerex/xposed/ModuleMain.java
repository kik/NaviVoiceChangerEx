package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private static ModuleMain module;

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
            new GoogleMapsHookBuilder(param, version.first, version.second).run();
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
    private static class SynthesizeHook implements XposedInterface.Hooker
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
    private static class SetVoiceNameHook implements XposedInterface.Hooker
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
                if (!p.disableNetworkSynthesizer()) {
                    var ret = callback.getResult();
                    Arrays.stream(ret.getClass().getDeclaredFields())
                            .filter(f -> f.getType().equals(String.class))
                            .forEach(f -> {
                                try {
                                    f.setAccessible(true);
                                    f.set(ret, "VOICEVOX-" + p.voiceboxStyleId);
                                } catch (IllegalAccessException e) {
                                    module.log("setting ret.voice failed", e);
                                }
                            });
                }
            }
            module.log("method " + callback.getMember() + " return with " + callback.getResult());
        }
    }

    @XposedHooker
    private static class InspectHook implements XposedInterface.Hooker
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

    private class GoogleMapsHookBuilder
    {
        private final PackageLoadedParam packageLoadedParam;
        private final String versionName;
        private final int versionCode;
        private final List<Class<?>> classes;

        GoogleMapsHookBuilder(@NonNull PackageLoadedParam param, String versionName, int versionCode)
        {
            this.packageLoadedParam = param;
            this.versionName = versionName;
            this.versionCode = versionCode;
            classes = loadAllClass(100000);
            log("loaded " + classes.size() + " classes");
        }

        private ClassLoader classLoader()
        {
            return packageLoadedParam.getClassLoader();
        }

        private String className(int index)
        {
            int n = 'z' - 'a' + 1;
            String s = "";
            while (index > 0) {
                int c = index % n;
                index /= n;
                s = (char)('a' + c) + s;
            }
            return s;
        }

        private List<Class<?>> loadAllClass(int classCount)
        {
            var list = new ArrayList<Class<?>>();
            for (int i = 0; i < classCount; i++) {
                try {
                    list.add(classLoader().loadClass(className(i)));
                } catch (ClassNotFoundException ignore) {
                }
            }
            return list;
        }

        private Stream<Class<?>> classes()
        {
            return classes.stream();
        }

        private Predicate<Class<?>> implementsExact(Class<?>... interfaces)
        {
            return cls -> Set.of(cls.getInterfaces()).equals(Set.of(interfaces));
        }

        void run()
        {
            // public final class NetworkTtsQueueRunner implements Runnable
            // {
            //      public NetworkTtsQueueRunner(
            //          PriorityBlockingQueue priorityBlockingQueue, ???, TtsTempManager ttsTempManager,
            //          ApplicationParameters applicationParameters, ???, Executor executor, Executor executor2,
            //          TtsStat ttsStat, TtsSynthesizer synthesizer1, TtsSynthesizer synthesizer2,
            //          ???, Voice voice) {
            // }
            final Constructor<?> ctorNetworkTtsQueueRunner = classes()
                    // implements Runnable
                    .filter(implementsExact(Runnable.class))
                    // unique constructor
                    .map(Class::getDeclaredConstructors)
                    .filter(ctors -> ctors.length == 1)
                    .map(ctors -> ctors[0])
                    .filter(ctor -> ctor.getParameterCount() == 12)
                    // <init>(PriorityBlockingQueue, ..., Executor executor, Executor executor2, ...)
                    .filter(ctor -> {
                        var paramNames = Arrays.stream(ctor.getParameters()).map(param -> param.getType().getName()).collect(Collectors.toList());
                        return paramNames.get(0).equals("java.util.concurrent.PriorityBlockingQueue") &&
                                paramNames.get(5).equals("java.util.concurrent.Executor") &&
                                paramNames.get(6).equals("java.util.concurrent.Executor") &&
                                paramNames.get(8).equals(paramNames.get(9));
                    })
                    .findFirst().orElse(null);
            if (ctorNetworkTtsQueueRunner == null) {
                log("NetworkTtsQueueRunner.<init> not found");
                return;
            }
            log("ctorNetworkTtsQueueRunner = " + ctorNetworkTtsQueueRunner);
            hook(ctorNetworkTtsQueueRunner, InspectHook.class);

            // public interface TtsSynthesizer
            final Class<?> intfTtsSynthesizer = ctorNetworkTtsQueueRunner.getParameters()[8].getType();
            log("intfTtsSynthesizer = " + intfTtsSynthesizer);

            // boolean TtsSynthesizer#synthesizeToFile(VoiceAlert alert, String path)
            final Method methodSynthesizeToFile = Arrays.stream(intfTtsSynthesizer.getMethods())
                    .filter(method -> method.getParameterCount() == 2)
                    .filter(method -> method.getParameters()[1].getType().equals(String.class))
                    .findFirst().orElse(null);
            if (methodSynthesizeToFile == null) {
                log("methodSynthesizeToFile not found");
                return;
            }
            log("methodSynthesizeToFile = " + methodSynthesizeToFile);

            final var methodSynthesizeToFileImpls = classes()
                    .filter(implementsExact(intfTtsSynthesizer))
                    .flatMap(cls -> {
                        try {
                            var m = cls.getMethod(methodSynthesizeToFile.getName(), methodSynthesizeToFile.getParameterTypes());
                            return Stream.of(m);
                        } catch (NoSuchMethodException ignore) {
                            return Stream.empty();
                        }
                    }).collect(Collectors.toList());

            methodSynthesizeToFileImpls.forEach(method -> {
                log("methodSynthesizeToFile impl = " + method);
                hook(method, SynthesizeHook.class);
            });

            // find NetworkTtsQueueManager
            final Class<?>[] ctorNetworkTtsQueueManagerParams = {
                    ctorNetworkTtsQueueRunner.getParameters()[4].getType(),
                    ctorNetworkTtsQueueRunner.getParameters()[3].getType(),
                    ctorNetworkTtsQueueRunner.getParameters()[7].getType(),
                    ctorNetworkTtsQueueRunner.getParameters()[0].getType(),
                    ctorNetworkTtsQueueRunner.getDeclaringClass(),
                    ctorNetworkTtsQueueRunner.getParameters()[11].getType(),
            };
            final Constructor<?> ctorNetworkTtsQueueManager = classes()
                    .map(Class::getDeclaredConstructors)
                    .filter(ctors -> ctors.length == 1)
                    .map(ctors -> ctors[0])
                    .filter(ctor -> Arrays.equals(ctor.getParameterTypes(), ctorNetworkTtsQueueManagerParams))
                    .findFirst().orElse(null);

            if (ctorNetworkTtsQueueManager == null) {
                log("ctorNetworkTtsQueueManager not found");
                return;
            }
            log("ctorNetworkTtsQueueManager = " + ctorNetworkTtsQueueManager);
            final Class<?> clsNetworkTtsQueueManager = ctorNetworkTtsQueueManager.getDeclaringClass();

            final Method methodGetGuidanceText = Arrays.stream(clsNetworkTtsQueueManager.getDeclaredMethods())
                    .filter(m -> Modifier.isStatic(m.getModifiers()))
                    .filter(m -> m.getParameterCount() == 3)
                    .filter(m -> {
                                var types = m.getParameterTypes();
                                if (types[0].equals(ctorNetworkTtsQueueRunner.getParameters()[3].getType()) &&
                                        types[2].equals(ctorNetworkTtsQueueRunner.getParameters()[11].getType())) {
                                    return true;
                                } else {
                                    return false;
                                }
                            })
                    .findFirst().orElse(null);

            if (methodGetGuidanceText == null) {
                log("methodGetGuidanceText not found");
                return;
            }
            log("methodGetGuidanceText = " + methodGetGuidanceText);

            hook(methodGetGuidanceText, SetVoiceNameHook.class);

            runExtra();
        }

        private Method getMethod(String className, String methodName) throws Exception
        {
            var cls = classLoader().loadClass(className);
            var method = Arrays.stream(cls.getDeclaredMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst().orElse(null);
            return method;
        }

        private void runExtra()
        {
            try {
                //hook(getMethod("azuh", "c"), InspectCallStackHook.class);
                //hook(getMethod("azvf", "e"), InspectCallStackHook.class);
                //hook(getMethod("bade", "d"), InspectCallStackHook.class);
            } catch (Exception e) {
                log("runExtra", e);
            }
        }
    }
}
