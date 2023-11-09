package jp.nekoteki.android.navivoicechanger;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Config {
	final static public String CONF_FILE = "config.ini";
	
	final static Map<String, String> DEFAULTS;
	static {
		Map<String, String> c = new HashMap<String, String>();
		c.put("server_url_base", "http://nvc.nekoteki.jp");
		DEFAULTS = Collections.unmodifiableMap(c);
	}

	public static String get(Context context, String name) {
		Log.d("Config", "Getting config key: "+name);
		String v = (String) getProp(context).get(name);
		if (v != null && !v.equals(""))
			return v;
		Log.d("Config", "Fallback to default for key: "+name);
		return DEFAULTS.get(name);
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
	
	private static Properties getProp(Context context) {
		Properties prop = new Properties();
		File conf = getConfFile(context);
		if (conf == null) return prop;
		Log.d("Config", "Config prop file: "+conf.getAbsolutePath());
		if (!conf.exists()) return prop;
		try {
			prop.load(new InputStreamReader(new FileInputStream(conf), "UTF-8"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return prop;
	}
	
	private static File getConfFile(Context context) {
		File dir = context.getExternalFilesDir(null);
		if (dir == null) return null;
		if (!dir.exists()) dir.mkdirs();
		return new File(dir, CONF_FILE);
	}
}

