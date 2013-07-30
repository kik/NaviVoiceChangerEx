package jp.nekoteki.android.navivoicechanger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Properties;

import android.content.Context;

public class Config {
	final static public String CONF_FILE = "config.ini";
	
	public static String get(Context context, String name) {
		return (String) getProp(context).get(name);
	}
	
	public static void set(Context context, String name, String value) {
		Properties prop = getProp(context);
		prop.setProperty(name, value);
		File conf = getConfFile(context);
		if (conf == null) return;
		try {
			prop.store(new OutputStreamWriter(new FileOutputStream(conf), "UTF-8"), "NaviVoiceChanger Config");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static Properties getProp(Context context) {
		Properties prop = new Properties();
		File conf = getConfFile(context);
		if (conf == null) return prop;
		if (!conf.exists()) return prop;
		try {
			prop.load(new InputStreamReader(new FileInputStream(conf), "UTF-8"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return prop;
	}
	
	public static File getConfFile(Context context) {
		File dir = context.getExternalFilesDir(null);
		if (dir == null) return null;
		if (!dir.exists()) dir.mkdirs();
		return new File(dir, CONF_FILE);
	}
}

