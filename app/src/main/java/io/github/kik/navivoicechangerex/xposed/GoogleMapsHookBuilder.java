package io.github.kik.navivoicechangerex.xposed;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedModuleInterface;
import okhttp3.Cache;

public class GoogleMapsHookBuilder extends AbstactHookBuilder {
    public static final int NR_CLASSES = 100000;

    protected GoogleMapsHookBuilder(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super(param);
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

    @Override
    protected List<Class<?>> loadAllClasses() {
        var list = new ArrayList<Class<?>>();
        for (int i = 0; i < NR_CLASSES; i++) {
            try {
                list.add(classLoader().loadClass(className(i)));
            } catch (ClassNotFoundException ignore) {
            }
        }
        return list;
    }

    static class Specs {
        static Predicate<Class<?>> NetworkTtsQueueRunner() {
            // public final class NetworkTtsQueueRunner implements Runnable
            // {
            //      public NetworkTtsQueueRunner(
            //          PriorityBlockingQueue p0,
            //          ??? p1,
            //          TtsTempManager p2,
            //          ApplicationParameters p3,
            //          ??? p4,
            //          Executor p5,
            //          Executor p6,
            //          TtsStat p7,
            //          TtsSynthesizer p8,
            //          TtsSynthesizer p9,
            //          ??? p11,
            //          NazoTriple p12) {
            ///     }
            // }
            return cls -> Stream.of(cls)
                    .filter(implementExact(Runnable.class))
                    .flatMap(GoogleMapsHookBuilder::getUniqueConstructor)
                    .filter(matchParam(0, PriorityBlockingQueue.class))
                    .filter(matchParam(5, Executor.class))
                    .filter(matchParam(6, Executor.class))
                    .anyMatch(matchParam(8, 9));
        }

        static class NetworkTtsQueueRunnerContents {
            public final Constructor<?> constructor;
            public final Class<?> TtsSynthesizer;

            public NetworkTtsQueueRunnerContents(Class<?> cls) {
                ModuleMain.module.log("class NetworkTtsQueueRunner = " + cls.getName());
                this.constructor = cls.getDeclaredConstructors()[0];
                ModuleMain.module.log("NetworkTtsQueueRunner.<init> = " + this.constructor);
                this.TtsSynthesizer = this.constructor.getParameterTypes()[8];
                ModuleMain.module.log("interface TtsSynthesizer = " + this.TtsSynthesizer);
            }
        }

        static class TtsSynthesizerContents {
            public final Method synthesizeToFile;

            public TtsSynthesizerContents(Class<?> cls) {
                // boolean TtsSynthesizer#synthesizeToFile(VoiceAlert alert, String path)
                this.synthesizeToFile = Arrays.stream(cls.getMethods())
                        .filter(method -> method.getParameterCount() == 2)
                        .filter(matchParam(1, String.class))
                        .findFirst().orElse(null);
                if (this.synthesizeToFile == null) {
                    ModuleMain.module.log("method synthesizeToFile not found");
                    return;
                }
                ModuleMain.module.log("method synthesizeToFile = " + this.synthesizeToFile);
            }
        }
    }

    public void run() {
        loadCache();
        runApplicationCapture();

        final Cached<Class<?>> clsNetworkTtsQueueRunner = findClass(
                "NetworkTtsQueueRunner",
                Specs.NetworkTtsQueueRunner());

        if (clsNetworkTtsQueueRunner.get() == null) {
            ModuleMain.module.log("NetworkTtsQueueRunner not found");
            return;
        }
        final var contentsNetworkTtsQueueRunner = new Specs.NetworkTtsQueueRunnerContents(clsNetworkTtsQueueRunner.get());

        ModuleMain.module.hook(contentsNetworkTtsQueueRunner.constructor, ModuleMain.InspectHook.class);

        final var contentsTtsSynthesizer = new Specs.TtsSynthesizerContents(contentsNetworkTtsQueueRunner.TtsSynthesizer);

        final Cached<List<Class<?>>> TtsSynthesizerImpls = findClasses("TtsSynthesizerImpls",
                implementExact(contentsNetworkTtsQueueRunner.TtsSynthesizer));
        for (var impl : TtsSynthesizerImpls.get()) {
            ModuleMain.module.log("class implements TtsSynthesizer = " + impl);
            var method = getOverrideMethod(impl, contentsTtsSynthesizer.synthesizeToFile);
            if (method == null) {
                ModuleMain.module.log("synthesizeToFile not found: " + impl);
                continue;
            }
            ModuleMain.module.log("hook synthesizeToFile: " + method);
            ModuleMain.module.hook(method, ModuleMain.SynthesizeHook.class);
        }

        // find NetworkTtsQueueManager
        final Class<?>[] ctorNetworkTtsQueueManagerParams = {
                contentsNetworkTtsQueueRunner.constructor.getParameters()[4].getType(),
                contentsNetworkTtsQueueRunner.constructor.getParameters()[3].getType(),
                contentsNetworkTtsQueueRunner.constructor.getParameters()[7].getType(),
                contentsNetworkTtsQueueRunner.constructor.getParameters()[0].getType(),
                contentsNetworkTtsQueueRunner.constructor.getDeclaringClass(),
                contentsNetworkTtsQueueRunner.constructor.getParameters()[11].getType(),
        };
        final Constructor<?> ctorNetworkTtsQueueManager = classes()
                .map(Class::getDeclaredConstructors)
                .filter(ctors -> ctors.length == 1)
                .map(ctors -> ctors[0])
                .filter(ctor -> Arrays.equals(ctor.getParameterTypes(), ctorNetworkTtsQueueManagerParams))
                .findFirst().orElse(null);

        if (ctorNetworkTtsQueueManager == null) {
            ModuleMain.module.log("ctorNetworkTtsQueueManager not found");
            return;
        }
        ModuleMain.module.log("ctorNetworkTtsQueueManager = " + ctorNetworkTtsQueueManager);
        final Class<?> clsNetworkTtsQueueManager = ctorNetworkTtsQueueManager.getDeclaringClass();

        final Method methodGetGuidanceText = Arrays.stream(clsNetworkTtsQueueManager.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 3)
                .filter(m -> {
                    var types = m.getParameterTypes();
                    if (types[0].equals(contentsNetworkTtsQueueRunner.constructor.getParameters()[3].getType()) &&
                            types[2].equals(contentsNetworkTtsQueueRunner.constructor.getParameters()[11].getType())) {
                        return true;
                    } else {
                        return false;
                    }
                })
                .findFirst().orElse(null);

        if (methodGetGuidanceText == null) {
            ModuleMain.module.log("methodGetGuidanceText not found");
            return;
        }
        ModuleMain.module.log("methodGetGuidanceText = " + methodGetGuidanceText);

        ModuleMain.module.hook(methodGetGuidanceText, ModuleMain.SetVoiceNameHook.class);

        storeCache();
    }

    private void runApplicationCapture()
    {
        try {
            ModuleMain.applicationCaptureHookUnhooker = ModuleMain.module.hook(getMethod("android.content.ContextWrapper", "attachBaseContext"), ModuleMain.ApplicationCaptureHook.class);
        } catch (Exception e) {
            ModuleMain.module.log("runApplicationCapture", e);
        }
    }

    private Method getMethod(String className, String methodName) throws Exception {
        var cls = classLoader().loadClass(className);
        var method = Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElse(null);
        return method;
    }

}
