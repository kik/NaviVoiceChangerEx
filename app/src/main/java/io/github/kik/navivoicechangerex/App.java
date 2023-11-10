package io.github.kik.navivoicechangerex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App {
    public static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final SettableFuture<XposedService> xposed = SettableFuture.create();

    {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                xposed.set(service);
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                Log.w(getClass().getName(), "XposedService died");
            }
        });
    }

    public static XposedService xposed() {
        if (xposed.isDone()) {
            while (true) {
                try {
                    return xposed.get();
                } catch (InterruptedException| ExecutionException ignore) {
                }
            }
        }
        return null;
    }

    public static void waitXposed(Consumer<XposedService> f, Executor executor) {
        xposed.addListener(() -> {
            f.accept(xposed());
        }, executor);
    }

    public static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

}
