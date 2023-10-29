package io.github.kik.navivoicechangerex;


import androidx.annotation.NonNull;
import androidx.annotation.OptIn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

    @OptIn(markerClass = HookBuilder.DexAnalysis.class)
    private void analyze(@NonNull PackageLoadedParam param)
    {
        try {
            HookBuilder.buildHooks(this, (BaseDexClassLoader) param.getClassLoader(), param.getApplicationInfo().sourceDir, builder -> {
                builder.setExceptionHandler(ex -> {
                    log("builder ex", ex);
                    return true;
                });
                log("building hooks");
                builder.setForceDexAnalysis(false);
                /*
                log("search method");
                HookBuilder.MethodMatch method = builder.firstMethod(methodMatcher -> {
                    //methodMatcher.setReferredStrings(builder.exact("Couldn't build synthesis URL.").observe());
                    //methodMatcher.setIsFinal(true);
                    methodMatcher.setName(builder.exact("bafb"));
                });
                method.onMatch(m -> {
                    log("method = " + m);
                }).onMiss(() -> {
                    log("not found");
                });
                log("method match = " + method);


                builder.classes(classMatcher -> {
                    //classMatcher.setName(builder.exact("bafb"));
                }).onMatch(classes -> {
                    for (Class<?> c : classes) {
                        log("class found: " + c);
                    }
                }).onMiss(() -> {
                    log("class not found");
                });
*/
            }).get();
        } catch (Exception ex) {
            log("analyze", ex);
        }
    }
}
