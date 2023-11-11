package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.libxposed.api.XposedModuleInterface;

public abstract class AbstactHookBuilder {
    protected final XposedModuleInterface.PackageLoadedParam moduleLoadedParam;
    protected final int versionCode;
    protected final String versionName;
    protected final Properties cacheStore;

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
        var cacheName = param.getPackageName() + "/" + this.versionName;
        this.cacheStore = new Properties();
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

    protected void loadCache() {
        // Applicationをまだロードしてないので、キャッシュの場所がわからん
        var path = new File(Environment.getExternalStorageDirectory(),
                "Android/data/" + moduleLoadedParam.getPackageName() + "/cache/.nvcex/" + versionName);
        ModuleMain.module.log("loading analysis cache: " + path);
        try (var is = new FileInputStream(path)) {
            this.cacheStore.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            ModuleMain.module.log("loaded");
        } catch (FileNotFoundException ignore) {
        } catch (IOException ioe) {
            ModuleMain.module.log("load cache failed", ioe);
        }
    }

    protected void storeCache() {
        // Applicationをまだロードしてないので、キャッシュの場所がわからん
        var path = new File(Environment.getExternalStorageDirectory(),
                "Android/data/" + moduleLoadedParam.getPackageName() + "/cache/.nvcex/" + versionName);
        ModuleMain.module.log("store analysis cache: " + path);
        path.getParentFile().mkdirs();
        try (var os = new FileOutputStream(path)) {
            this.cacheStore.store(new OutputStreamWriter(os, StandardCharsets.UTF_8), "");
            ModuleMain.module.log("stored");
        } catch (FileNotFoundException ignore) {
        } catch (IOException ioe) {
            ModuleMain.module.log("load cache failed", ioe);
        }
    }

    protected ClassLoader classLoader() {
        return moduleLoadedParam.getClassLoader();
    }

    interface Cached<T> {
        public T get();
    }

    public abstract class AbstractCached<T> implements Cached<T> {
        private final String name;

        public AbstractCached(String name) {
            this.name = name;
        }

        protected String getCache() {
            return cacheStore.getProperty(name);
        }

        protected List<String> getCacheList() {
            var s = getCache();
            if (s == null) {
                return null;
            }
            return List.of(s.split(","));
        }

        protected void putCache(String value) {
            cacheStore.put(name, value);
        }

        protected void putCacheList(List<String> values) {
            putCache(String.join(",", values));
        }
    }

    protected abstract List<Class<?>> loadAllClasses();

    protected Stream<Class<?>> classes() {
        if (lazyAllClasses == null) {
            lazyAllClasses = loadAllClasses();
        }
        return lazyAllClasses.stream();
    }

    protected Cached<String> cacheString(String name, Supplier<String> get) {
        return new AbstractCached<String>(name) {
            @Override
            public String get() {
                var value = getCache();
                if (value == null) {
                    value = get.get();
                    putCache(value);
                }
                return value;
            }
        };
    }

    protected Cached<Class<?>> findClass(String name, Predicate<Class<?>> p) {
        return new AbstractCached<Class<?>>(name) {
            @Override
            public Class<?> get() {
                var className = getCache();
                if (className == null) {
                    return update();
                } else {
                    try {
                        return moduleLoadedParam.getClassLoader().loadClass(className);
                    } catch (ClassNotFoundException ignore) {
                        return update();
                    }
                }
            }

            private Class<?> update() {
                var cls = classes()
                        .filter(p)
                        .findFirst()
                        .orElse(null);
                putCache(cls == null ? "" : cls.getName());
                return cls;
            }
        };
    }

    protected Cached<List<Class<?>>> findClasses(String name, Predicate<Class<?>> p) {
        return new AbstractCached<List<Class<?>>>(name) {
            @Override
            public List<Class<?>> get() {
                var classNames = getCacheList();
                if (classNames == null) {
                    return update();
                } else {
                    List<Class<?>> ret = new ArrayList<>();
                    try {
                        for (var className : classNames) {
                            ret.add(moduleLoadedParam.getClassLoader().loadClass(className));
                        }
                        return ret;
                    } catch (ClassNotFoundException ignore) {
                        return update();
                    }
                }
            }

            private List<Class<?>> update() {
                var classes = classes()
                        .filter(p)
                        .collect(Collectors.toList());
                putCacheList(classes.stream().map(Class::getName).collect(Collectors.toList()));
                return classes;
            }
        };
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

    public static Method getOverrideMethod(@NonNull Class<?> cls, @NonNull Method method) {
        try {
            return cls.getDeclaredMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException ignore) {
            return null;
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
