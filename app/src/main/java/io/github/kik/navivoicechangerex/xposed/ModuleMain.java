package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

public class ModuleMain extends XposedModule {
    private static ModuleMain module;
    private static SharedPreferences prefs;

    public ModuleMain(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base, param);
        log("ModuleMain at " + param.getProcessName());
        module = this;
        prefs = getRemotePreferences("io.github.kik.navivoicechangerex_preferences");
        log("remote preferences");
        for (var v : prefs.getAll().entrySet()) {
            log("  entry = " + v);
        }
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
    private static class SynthesizeHook implements XposedInterface.Hooker
    {
        @BeforeInvocation
        public static SynthesizeHook beforeInvocation(BeforeHookCallback callback) {
            module.log("method " + callback.getMember() + " called with " + List.of(callback.getArgs()));
            callback.returnAndSkip(false);
            return new SynthesizeHook();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, SynthesizeHook context) {
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
                    .filter(ctor -> ctor.getParameterCount() >= 12)
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
                hook(getMethod("bade", "d"), InspectCallStackHook.class);
            } catch (Exception e) {
                log("runExtra", e);
            }
        }
    }
}
