package io.github.kik.navivoicechangerex;


import androidx.annotation.NonNull;
import androidx.annotation.OptIn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import dalvik.system.BaseDexClassLoader;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import io.github.libxposed.helper.HookBuilder;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ModuleMain extends XposedModule
{
    private static ModuleMain module;

    public ModuleMain(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base, param);
        log("ModuleMain at " + param.getProcessName());
        module = this;
    }

    private static boolean synthesize(String text, String path) {
        module.log("synthesize() called: " + text);

        HttpUrl baseUrl = HttpUrl.parse("http://192.168.39.5:50021");
        OkHttpClient client = new OkHttpClient();
        String speakerId = "26";

        Request r = new Request.Builder()
                .url(baseUrl.newBuilder()
                        .addPathSegment("audio_query")
                        .addQueryParameter("speaker", speakerId)
                        .addQueryParameter("text", text)
                        .build())
                .post(RequestBody.create("", null))
                .build();
        try (Response response = client.newCall(r).execute()) {
            String audio = response.body().string();

            Request r2 = new Request.Builder()
                    .url(baseUrl.newBuilder()
                            .addPathSegment("synthesis")
                            .addQueryParameter("speaker", speakerId)
                            .build())
                    .post(RequestBody.create(audio, MediaType.parse("application/json")))
                    .build();
            try (Response response2 = client.newCall(r2).execute()) {
                byte[] bs = response2.body().bytes();
                try (FileOutputStream os = new FileOutputStream(path)) {
                    os.write(bs);
                }
            }
        } catch (IOException ioe) {
            module.log("synthesize", ioe);
            return false;
        }
        return true;
    }

    @XposedHooker
    static class MyHooker implements XposedInterface.Hooker
    {
        private Object message;
        private String path;

        public MyHooker(Object message, String path) {
            this.message = message;
            this.path = path;
        }

        @BeforeInvocation
        public static MyHooker beforeInvocation(BeforeHookCallback callback) {
            Object p = callback.getArgs()[0];
            String path = (String)callback.getArgs()[1];
            module.log("beforeInvocation message=" + p + ", path=" + path);
            try {
                Field field = p.getClass().getField("a");
                String message = (String)field.get(p);
                callback.returnAndSkip(synthesize(message, path));
                return null;
            } catch (Exception ex) {
                module.log("afterInvocation", ex);
                callback.returnAndSkip(false);
                return null;
            }
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, MyHooker context) {
            module.log("afterInvocation");
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

        try {
            String httpSynthesizeClassName = "bafb";
            String rpcSynthesizeClassName = "bafa";
            String methodName = "b";
            String paramClassName = "baff";
            ClassLoader loader = param.getClassLoader();
            Class<?> httpSynthesizeClass = loader.loadClass(httpSynthesizeClassName);
            Class<?> rpcSynthesizeClass = loader.loadClass(rpcSynthesizeClassName);
            Class<?> paramClass = loader.loadClass(paramClassName);
            Method httpSynthesize = httpSynthesizeClass.getMethod(methodName, paramClass, String.class);
            Method rpcSynthesize = rpcSynthesizeClass.getMethod(methodName, paramClass, String.class);

            hook(httpSynthesize, MyHooker.class);
            hook(rpcSynthesize, MyHooker.class);

            analyze(param);
        } catch (Exception ex) {
            log("hook failed", ex);
        }
    }

    private static String className(int index)
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

    @OptIn(markerClass = HookBuilder.DexAnalysis.class)
    private void analyze(@NonNull PackageLoadedParam param)
    {
        try {
            ArrayList<Class<?>> classList = new ArrayList<>();
            for (int i = 1; i < 100000; i++) {
                try {
                    classList.add(param.getClassLoader().loadClass(className(i)));
                } catch (ClassNotFoundException ex) {
                }
            }
            log("class count = " + classList.size());

            final Class<?> ttsSynthesizeInterface = classList.stream().filter(clz -> {
                Class<?>[] intfs = clz.getInterfaces();
                if (intfs.length != 1) return false;
                if (!intfs[0].getName().equals("java.lang.Runnable")) return false;
                return true;
            }).map(clz -> {
                Constructor<?>[] ctors = clz.getDeclaredConstructors();
                if (ctors.length != 1) return null;
                Constructor<?> ctor = ctors[0];
                java.lang.reflect.Parameter[] params = ctor.getParameters();
                if (params.length < 1) return null;
                if (!params[0].getType().getName().equals("java.util.concurrent.PriorityBlockingQueue"))
                    return null;
                Class<?> tts = null;
                boolean executor = false;
                for (int i = 0; i < params.length - 1; i++) {
                    if (params[i].getType().equals(params[i + 1].getType())) {
                        if (params[i].getType().getName().equals("java.util.concurrent.Executor")) {
                            executor = true;
                        } else {
                            tts = params[i].getType();
                        }
                    }
                }
                if (!executor) return null;
                return tts;
            }).filter(Objects::nonNull).findFirst().get();
            log("ttsSynthesizeInterface = " + ttsSynthesizeInterface);

            final Stream<Class<?>> ttsSynthesizers = classList.stream().filter(clz -> {
                Class<?>[] intfs = clz.getInterfaces();
                if (intfs.length != 1) return false;
                if (!intfs[0].equals(ttsSynthesizeInterface)) return false;
                return true;
            });

            ttsSynthesizers.flatMap(clz -> {
                log("ttsSynthesizer = " + clz);
                return Arrays.stream(clz.getDeclaredMethods()).filter(m -> {
                    if (m.getParameters().length != 2) return false;
                    if (!m.getParameters()[1].getType().equals(String.class)) return false;

                    try {
                        ttsSynthesizeInterface.getMethod(m.getName(), m.getParameterTypes()[0], m.getParameterTypes()[1]);
                    } catch (NoSuchMethodException ex) {
                        return false;
                    }
                    return true;
                });
            }).forEach(m ->{
                log("hook method: " + m);
            });
        } catch (Exception ex) {
            log("analyze", ex);
        }
    }
}
