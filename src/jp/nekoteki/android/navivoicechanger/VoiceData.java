package jp.nekoteki.android.navivoicechanger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BrokenBarrierException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import android.util.Log;
import android.content.Context;
import android.content.res.AssetManager;

class VoiceDataInstallError extends Exception {};
class DataDirNotFound extends VoiceDataInstallError {};
class BrokenArchive extends Exception {
	public BrokenArchive() { super(); }
	public BrokenArchive(String string) {	super(string); }
};

public class VoiceData {
	public static final FileFilter FileFilterDirs = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	};
	
	public static final FileFilter FileFilterFiles = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return pathname.isFile();
		}
	};
	
	public final static String DATA_INI = "voicedata.ini";
	public final static String UNIT_METRIC = "metric";
	public final static String UNIT_IMPERIAL = "imperial";
	public final static String ARCHIVE_FILENAME = "voice_instructions.zip";
	public final static String TTS_ARCHIVE_FILENAME = "cannedtts.zip";

	protected int id;
	protected String title;
	protected float rating;
	protected String description;
	protected String archive_md5;
	protected String path;
	protected String tts_archive_md5;
	protected Context context;
	
	static List<VoiceData> scanVoiceData(Context context) {
		Log.d(VoiceData.class.getClass().getName(), "Start voice data dir scan.");

		ArrayList<VoiceData> list = new ArrayList<VoiceData>();

 		File baseDir = context.getExternalFilesDir(null);
 		if (baseDir == null) {
 			Log.i("VoiceData", "Can't get external storage path.");
 			return list;
 		}
		File vdDir = new File(baseDir, "voicedata");
		if (!vdDir.isDirectory())
			vdDir.mkdirs();
		
		File[] vddlist = vdDir.listFiles();
		
		if (vddlist == null) return list;
		
		for (File vdd: vddlist) {
			VoiceData vd;
			try {
				vd = new VoiceData(vdd, context);
			} catch (Exception e) {
				Log.d("VoiceData", "Invalid voice data dir, skip: "+vdd.getName()+": "+e.getMessage());
				continue;
			}
			list.add(vd);
		}
		
		return list;
	}
	
	static void copyVoiceAssets(Context context) {
		AssetManager assetManager = context.getAssets();

		File baseDir = context.getExternalFilesDir(null);
		if (baseDir == null) {
			Log.i("VoiceData", "Can't get external storage path.");
			return;
		}
		File testdir = new File(baseDir, "voicedata/test");
		if (!testdir.exists() || !testdir.isDirectory())
			testdir.mkdirs();
	
		String[] files = null;
		try {
			files = assetManager.list("voicedata/test");
			if (files == null) return;
			for (String filename: files) {
				Log.d(VoiceData.class.getClass().getName(), "Asset copy: "+filename+ " to "+testdir.getPath());
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
	
	static void purgeVoiceData(Context context) {
		File testdata_dir = getTargetBaseDir(context);
		if (!testdata_dir.exists() || !testdata_dir.isDirectory())
			return;
	
		Log.i("VoiceData", "Purging target voice data dir....");
 		
		for (File target_dir: (new File[] {getTargetVoiceDataDir(context), getTargetTtsDir(context)})) {
			if (!target_dir.exists()) continue;
			try {
				for (File localedir: target_dir.listFiles(FileFilterDirs)) {
					try {
						for (File f: localedir.listFiles(FileFilterFiles)) {
							Log.d("VoiceData", "Deleteing "+f.getPath());
							f.delete();
						}
					} catch (NullPointerException e) {
						// ignore
					}
				}
			} catch (NullPointerException e) {
				// ignore
			}
		}
		Log.i("VoiceData", "Purge has been completed.");
	}
	
	public static File getTargetBaseDir(Context context) {
		return new File(context.getExternalCacheDir().getParentFile().getParentFile(), "com.google.android.apps.maps/testdata");
	}
	
	public static File getTargetVoiceDataDir(Context context) {
		return new File(getTargetBaseDir(context), "voice");
	}
	
	public static File getTargetTtsDir(Context context) {
		return new File(getTargetBaseDir(context), "cannedtts");
	}
	
	protected static void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		int read;
		while ((read = in.read(buf)) != -1) {
			out.write(buf, 0, read);
		}
	}
	
	protected static void copyFile(File from, File to) throws IOException {
		Log.d("VoiceData", "Copy file: "+from.getPath()+" -> "+to.getPath());
		OutputStream os = new FileOutputStream(to);
		InputStream  is = new FileInputStream(from);
		copyStream(is, os);
		is.close();	
		os.close();
	}
	
	public static boolean hasTargetVoiceData(Context context) {
		Log.d("VoiceData", "Cheking target voie data on " + getTargetVoiceDataDir(context));
		File[] files = getTargetVoiceDataDir(context).listFiles(FileFilterDirs);
		if (files == null) return false;
		Log.d("VoiceData", "Locale dir count="+files.length);
		if (files.length < 1) return false;
		return true;
	}
	
	public VoiceData(File file, Context context) {
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
		
		this.context     = context;
		this.id          = Integer.parseInt(prop.getProperty("id"));
		this.archive_md5 = prop.getProperty("archive_md5");
		this.title       = prop.getProperty("title");
		this.description = prop.getProperty("description");
		this.path        = file.getAbsolutePath();
		this.rating      = Float.parseFloat(prop.getProperty("rating"));
		Log.d("VoiceData", "Initalized: "+this.toString());
	}
	
	public String toString() {
		return "<VoiceData id="+this.getId()+" title="+this.getTitle()+" path="+this.getPath()+" rating="+this.getRating()+">";
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

	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	public void validate() throws BrokenArchive {
		this.checkDigest(this.getPath() + "/" + ARCHIVE_FILENAME, this.getArchive_md5());
		// this.checkDigest(this.getPath() + "/" + TTS_ARCHIVE_FILENAME, this.getTts_archive_md5()); // TODO: check tts archive
	}
	
	public boolean isValid() {
		try {
			this.validate();
		} catch (BrokenArchive e) {
			return false;
		}
		return true;
	}
	
	protected void checkDigest(String path, String hexdigest) throws BrokenArchive {
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
			throw new BrokenArchive("I/O Error on archive check.");
		} catch (NoSuchAlgorithmException e) {
			Log.e(VoiceData.class.getClass().getName(), "Archive is not found: " + path);
			throw new BrokenArchive("Archive is not found: "+path);
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
		
		if (cur_digetst.equals(hexdigest)) return;
		
		throw new BrokenArchive("Digetst Mismatch: "+cur_digetst+" (expect: "+hexdigest+")");
	}
	
	public void install() throws BrokenArchive, ZipException, IOException, DataDirNotFound {
		this.validate();
		
		if (!hasTargetVoiceData(this.getContext()))
			throw new DataDirNotFound();

		purgeVoiceData(this.getContext());
		
		Log.i("VoiceData", "Install start for "+this.toString());
		File voice_archive = new File(this.getPath(), ARCHIVE_FILENAME);
		try {
			for (File localedir: getTargetVoiceDataDir(this.getContext()).listFiles()) {
				if (!localedir.isDirectory()) continue;
				copyFile(voice_archive, new File(localedir, ARCHIVE_FILENAME));
			}
		} catch (NullPointerException e) {
			// ignore
		}
		
		
		// TODO: extract TTS archive!
		Log.i("VoiceData", "Install finished!");
	}
	
	protected void extractTtsArchive(File file, File target) {
		// TODO: implement!
		/*
		ZipFile zipfile = new ZipFile(new File(this.getPath(), ARCHIVE_FILENAME));
		Enumeration<? extends ZipEntry> zentries = zipfile.entries();
		while (zentries.hasMoreElements()) {
			ZipEntry entry = zentries.nextElement();
			if (entry.isDirectory())
				continue;
			InputStream is = zipfile.getInputStream(entry);

		}
			*/
	}
	
}
