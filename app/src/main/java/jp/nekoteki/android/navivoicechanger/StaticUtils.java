package jp.nekoteki.android.navivoicechanger;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Process;
import android.util.Log;

public class StaticUtils {
	public static void killMapsProcess(Context context) {
		Log.i("Utils", "Trying to kill Maps...");
		ActivityManager am = ((ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE));
		am.killBackgroundProcesses("com.google.android.apps.maps");
		for (RunningAppProcessInfo pi: am.getRunningAppProcesses()) {
			if (pi.processName != "com.google.android.apps.maps")
				continue;
			Log.d("Utils", "Killing pid="+Integer.toString(pi.pid));
			android.os.Process.killProcess(pi.pid);
		}
	}
	
	public static void terminateSelf(Context context) {
		Process.killProcess(Process.myPid());
	}
}
