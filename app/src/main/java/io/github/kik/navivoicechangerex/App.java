package io.github.kik.navivoicechangerex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App {
    public static CompletableFuture<XposedService> xposed = getXposedService();

    private static CompletableFuture<XposedService> getXposedService()
    {
        var future = new CompletableFuture<XposedService>();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                future.complete(service);
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                Log.w(getClass().getName(), "XposedService died");
            }
        });
        return future;
    }

    public static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

}
