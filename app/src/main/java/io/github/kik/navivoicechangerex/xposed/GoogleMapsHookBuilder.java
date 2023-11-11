package io.github.kik.navivoicechangerex.xposed;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedModuleInterface;

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

    private static class Specs {
        Predicate<Class<?>> NetworkTtsQueueRunner() {
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
    }

    public void run() {
        runApplicationCapture();

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
                .filter(implementExact(Runnable.class))
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
            ModuleMain.module.log("NetworkTtsQueueRunner.<init> not found");
            return;
        }
        ModuleMain.module.log("ctorNetworkTtsQueueRunner = " + ctorNetworkTtsQueueRunner);
        ModuleMain.module.hook(ctorNetworkTtsQueueRunner, ModuleMain.InspectHook.class);

        // public interface TtsSynthesizer
        final Class<?> intfTtsSynthesizer = ctorNetworkTtsQueueRunner.getParameters()[8].getType();
        ModuleMain.module.log("intfTtsSynthesizer = " + intfTtsSynthesizer);

        // boolean TtsSynthesizer#synthesizeToFile(VoiceAlert alert, String path)
        final Method methodSynthesizeToFile = Arrays.stream(intfTtsSynthesizer.getMethods())
                .filter(method -> method.getParameterCount() == 2)
                .filter(method -> method.getParameters()[1].getType().equals(String.class))
                .findFirst().orElse(null);
        if (methodSynthesizeToFile == null) {
            ModuleMain.module.log("methodSynthesizeToFile not found");
            return;
        }
        ModuleMain.module.log("methodSynthesizeToFile = " + methodSynthesizeToFile);

        final var methodSynthesizeToFileImpls = classes()
                .filter(implementExact(intfTtsSynthesizer))
                .flatMap(cls -> {
                    try {
                        var m = cls.getMethod(methodSynthesizeToFile.getName(), methodSynthesizeToFile.getParameterTypes());
                        return Stream.of(m);
                    } catch (NoSuchMethodException ignore) {
                        return Stream.empty();
                    }
                }).collect(Collectors.toList());

        methodSynthesizeToFileImpls.forEach(method -> {
            ModuleMain.module.log("methodSynthesizeToFile impl = " + method);
            ModuleMain.module.hook(method, ModuleMain.SynthesizeHook.class);
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
                    if (types[0].equals(ctorNetworkTtsQueueRunner.getParameters()[3].getType()) &&
                            types[2].equals(ctorNetworkTtsQueueRunner.getParameters()[11].getType())) {
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

        try {
            //hook(File.class.getConstructor(File.class, String.class), FileConstructorHook.class);
            //hook(getMethod("baay", "k"), StopCannedMessageBundleUpdateHook.class);
        } catch (Exception e) {
            ModuleMain.module.log("getMethod failed", e);
        }

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
