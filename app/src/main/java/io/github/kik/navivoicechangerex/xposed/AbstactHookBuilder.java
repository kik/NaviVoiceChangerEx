package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedModuleInterface;

public abstract class AbstactHookBuilder {
    protected final XposedModuleInterface.PackageLoadedParam moduleLoadedParam;
    protected final int versionCode;
    protected final String versionName;
    protected final SharedPreferences cacheStore;

    private List<Class<?>> lazyAllClasses;

    protected AbstactHookBuilder(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        this.moduleLoadedParam = param;
        var version = getPackageVersion(param);
        if (version == null) {
            this.versionCode = 0;
            this.versionName = "";
        } else {
            this.versionCode = version.second;
            this.versionName = version.first;
        }
        var cacheName = param.getPackageName() + ":" + this.versionName;
        this.cacheStore = ModuleMain.module.getRemotePreferences(cacheName);
    }

    private static Pair<String, Integer> getPackageVersion(@NonNull XposedModuleInterface.PackageLoadedParam param) {
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
            ModuleMain.module.log("failed to get package version", e);
            return null;
        }
    }

    protected ClassLoader classLoader() {
        return moduleLoadedParam.getClassLoader();
    }

    interface Cached<T> {
        public T get(Supplier<T> orElse);
    }

    public class CachedString implements Cached<String> {
        private final String name;
        public CachedString(String name) {
            this.name = name;
        }

        @Override
        public String get(Supplier<String> orElse) {
            var value = cacheStore.getString(name, null);
            if (value == null) {
                value = orElse.get();
                cacheStore.edit().putString(name, value).apply();
            }
            return value;
        }
    }

    protected class CachedClass implements Cached<Class<?>> {
        private final CachedString className;
        public CachedClass(String name) {
            this.className = new CachedString(name);
        }
        @Override
        public Class<?> get(Supplier<Class<?>> orElse) {
            var name = className.get(() -> {
                var cls = orElse.get();
                return cls == null ? null : cls.getName();
            });
            try {
                return name == null ? null : moduleLoadedParam.getClassLoader().loadClass(name);
            } catch (ClassNotFoundException ignore) {
                return null;
            }
        }
    }

    protected abstract List<Class<?>> loadAllClasses();

    protected Stream<Class<?>> classes() {
        if (lazyAllClasses == null) {
            lazyAllClasses = loadAllClasses();
        }
        return lazyAllClasses.stream();
    }

    public static Predicate<Class<?>> implement(Class<?>... interfaces)
    {
        return cls -> Set.of(cls.getInterfaces()).containsAll(Set.of(interfaces));
    }

    public static Predicate<Class<?>> implementExact(Class<?>... interfaces)
    {
        return cls -> Set.of(cls.getInterfaces()).equals(Set.of(interfaces));
    }

    public static Stream<Constructor<?>> getUniqueConstructor(@NonNull Class<?> cls) {
        var ctors = cls.getDeclaredConstructors();
        if (ctors.length == 1) {
            return Stream.of(ctors);
        } else {
            return Stream.of();
        }
    }

    public static Predicate<Executable> matchParam(int index, Class<?> cls) {
        return method -> {
            var params = method.getParameterTypes();
            if (index < params.length) {
                return params[index].equals(cls);
            }
            return false;
        };
    }

    public static Predicate<Executable> matchParam(int index, String className) {
        return method -> {
            var params = method.getParameterTypes();
            if (index < params.length) {
                return params[index].getName().equals(className);
            }
            return false;
        };
    }

    public static Predicate<Executable> matchParam(int index, int otherIndex) {
        return method -> {
            var params = method.getParameterTypes();
            if (index < params.length && otherIndex < params.length) {
                return params[index].equals(params[otherIndex]);
            }
            return false;
        };
    }

}
