package io.github.kik.navivoicechangerex.module.com.google.android.apps.maps;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

public class Module
{

    public static void run(@NonNull XposedInterface xposed, @NonNull XposedModuleInterface.PackageLoadedParam param)
    {
        final ClassLoader loader = param.getClassLoader();
        final var classes = loadAllClass(loader);
        final var netSynthesizerInterface = findNetSynthesizerInterface(classes);
        final var netSynthesizers = findImplementations(netSynthesizerInterface, classes);

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

    private static List<Class<?>> loadAllClass(ClassLoader loader)
    {
        ArrayList<Class<?>> classList = new ArrayList<>();
        for (int i = 1; i < 40000; i++) {
            try {
                classList.add(loader.loadClass(className(i)));
            } catch (ClassNotFoundException ignored) {
            }
        }
        return classList;
    }

    private static Class<?> findNetSynthesizerInterface(List<Class<?>> classes)
    {
        return classes.stream().filter(clz -> {
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
    }

    private static Stream<Class<?>> findImplementations(final Class<?> interfaceClass, List<Class<?>> classes)
    {
        return classes.stream().filter(clz -> {
            Class<?>[] intfs = clz.getInterfaces();
            if (intfs.length != 1) return false;
            if (!intfs[0].equals(interfaceClass)) return false;
            return true;
        });
    }
}
