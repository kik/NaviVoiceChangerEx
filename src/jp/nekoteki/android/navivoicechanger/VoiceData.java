package jp.nekoteki.android.navivoicechanger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import android.util.Log;
import android.content.Context;
import android.content.res.AssetManager;

class VoiceDataInstallError extends Exception {};
class DataDirNotFound extends VoiceDataInstallError {};
class BrokenArchive extends Exception {};

public class VoiceData {
	public final static String DATA_INI = "voicedata.ini";
	public final static String UNIT_METRIC = "metric";
	public final static String UNIT_IMPERIAL = "imperial";
	
	protected int id;
	protected String title;
	protected float rating;
	protected String description;
	protected String archive_md5;
	protected String path;
	protected String tts_archive_md5;
	
	static List<VoiceData> scanVoiceData(Context context) {
		Log.d(VoiceData.class.getClass().getName(), "Start voice data dir scan.");

		ArrayList<VoiceData> list = new ArrayList<VoiceData>();

		File baseDir = context.getFilesDir();
		File vdDir = new File(baseDir, "voicedata");
		if (!vdDir.isDirectory())
			vdDir.mkdirs();
		
		File[] vddlist = vdDir.listFiles();
		
		for (File vdd: vddlist) {
			VoiceData vd;
			try {
				vd = new VoiceData(vdd);
			} catch (Exception e) {
				Log.d(VoiceData.class.getClass().getName(), "Invalid voice data dir, skip: "+vdd.getName()+": "+e.getMessage());
				continue;
			}
			list.add(vd);
		}
		
		return list;
	}
	
	static void copyVoiceAssets(Context context) {
		AssetManager assetManager = context.getAssets();

		File baseDir = context.getFilesDir();
		File testdir = new File(baseDir, "voicedata/test");
		if (!testdir.isDirectory())
			testdir.mkdirs();
	
		String[] files = null;
		try {
			files = assetManager.list("voicedata/test");
			for (String filename: files) {
				Log.d(VoiceData.class.getClass().getName(), "Check asset copy: "+filename);
				File of = new File(testdir, filename);
				//if (of.exists()) continue;
				OutputStream os = new FileOutputStream(of);
				InputStream  is = assetManager.open("voicedata/test/"+filename);
				copyStream(is, os);
				is.close();	
				os.close();
			}
		} catch (IOException e) {
			Log.e(VoiceData.class.getClass().getName(), "Failed to copy assets by IO Error: " + e.getMessage());
		}

	}
	
	private static void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		int read;
		while ((read = in.read(buf)) != -1) {
			out.write(buf, 0, read);
		}
	}
	
	public VoiceData(File file) {
		if (!file.exists() || !file.isDirectory())
			throw new 	IllegalArgumentException("Invalid data dir");
		File ini = new File(file, DATA_INI);
		if (!ini.exists())
			throw new 	IllegalArgumentException("Invalid data dir");
		
		Properties prop = new Properties();
		try {
			prop.load(new InputStreamReader(new FileInputStream(ini), "UTF-8"));
		} catch (IOException e) {
			throw new 	IllegalArgumentException("Failed to load ini file");
		}
		
		this.id          = Integer.parseInt(prop.getProperty("id"));
		this.archive_md5 = prop.getProperty("archive_md5");
		this.title       = prop.getProperty("title");
		this.description = prop.getProperty("description");
		this.path        = file.getAbsolutePath();
		this.rating      = Float.parseFloat(prop.getProperty("rating"));
		Log.d("VoiceData", "Initalized: "+this.toString());
	}
	
	public String toString() {
		return "<VoiceData id="+this.id+" title="+this.title+" path="+this.path+" rating="+this.rating+">";
	}
	
	public String getArchive_md5() {
		return archive_md5;
	}
	public void setArchive_md5(String archive_md5) {
		this.archive_md5 = archive_md5;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public float getRating() {
		return rating;
	}
	public void setRating(float rating) {
		this.rating = rating;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getTts_archive_md5() {
		return tts_archive_md5;
	}

	public void setTts_archive_md5(String tts_archive_md5) {
		this.tts_archive_md5 = tts_archive_md5;
	}

	public boolean isValid() {
		return this.checkDigest(this.getPath() + "/voice_instructions.zip", this.archive_md5);
	}
	
	protected boolean checkDigest(String path, String hexdigest) {
		DigestInputStream di = null;
		try {
			di = new DigestInputStream(
				new BufferedInputStream(	
					new FileInputStream(path)),
					MessageDigest.getInstance("MD5"));
		
			byte[] buf = new byte[1024];
			while (true) {
				if (di.read(buf) <= 0)
					break;
			}
		} catch (IOException e) {
			Log.e(VoiceData.class.getClass().getName(), "I/O Error on archive check.");
			return false;
		} catch (NoSuchAlgorithmException e) {
			Log.e(VoiceData.class.getClass().getName(), "Archive is not found: " + path);
			return false;
		} finally {
			if (di != null)
				try {
					di.close();
				} catch (IOException e) {
					// ignore
				}
		}
		
		String cur_digetst = "";
		for (byte digest_byte: di.getMessageDigest().digest()) {
			cur_digetst += String.format("%02x", digest_byte);
		}
		
		return cur_digetst == hexdigest;
	}
	
	public void install() throws BrokenArchive {
		if (!this.isValid())
			throw new BrokenArchive();
		
	}
	
}
