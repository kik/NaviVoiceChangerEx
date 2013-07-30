package jp.nekoteki.android.navivoicechanger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.util.Log;

public class RemoteVoiceData {
	protected int id;
	protected String ini_url;
	protected String archive_url;
	protected String preview_url;
	protected float rating;
	protected String title;
	protected String unit;
	protected String author;
	protected String description;
	protected VoiceData voice_data;
	
	public VoiceData getVoiceData() {
		return voice_data;
	}
	public void setVoiceData(VoiceData v) {
		this.voice_data = v;
	}
	public boolean isDownloaded() {
		return this.voice_data != null;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getIni_url() {
		return ini_url;
	}
	public void setIni_url(String ini_url) {
		this.ini_url = ini_url;
	}
	public String getArchive_url() {
		return archive_url;
	}
	public void setArchive_url(String archive_url) {
		this.archive_url = archive_url;
	}
	public String getPreview_url() {
		return preview_url;
	}
	public void setPreview_url(String preview_url) {
		this.preview_url = preview_url;
	}
	public float getRating() {
		return rating;
	}
	public void setRating(float rating) {
		this.rating = rating;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getUnit() {
		return unit;
	}
	public void setUnit(String unit) {
		this.unit = unit;
	}
	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	public void download (Context context) throws IOException {
		File basedir = VoiceData.getBaseDir(context);
		if (basedir == null)
			throw new IOException("Cannot find external strage.");
		File datadir = new File(basedir, Integer.toString(this.id));
		if (!datadir.exists()) datadir.mkdirs();
		this.downloadFile(this.ini_url,     new File(datadir, VoiceData.DATA_INI));
		this.downloadFile(this.archive_url, new File(datadir, VoiceData.ARCHIVE_FILENAME));
		this.downloadFile(this.preview_url, new File(datadir, VoiceData.PREVIEW_FILESNAME));
		this.setVoiceData(new VoiceData(datadir, context));
	}
	
	protected void downloadFile(String url, File file) throws IOException {
		Log.i(this.getClass().toString(), "Start download: "+url+" -> "+file.getAbsolutePath());
		InputStream is = null;
		FileOutputStream os = null;
		AndroidHttpClient client = AndroidHttpClient.newInstance("NaviVoiceChanger");
		try {
			Log.i(this.getClass().toString(), "Loading URL: "+this.ini_url);
			HttpResponse res;
			res = client.execute(new HttpGet(url));
			is = res.getEntity().getContent();
			os = new FileOutputStream(file);
			VoiceData.copyStream(is, os);
		} finally {
			client.close();
			if (os != null)
				os.close();
			if (is != null)
				is.close();
		}
	}
	
	public void delete() {
		if (!this.isDownloaded()) return;
		this.getVoiceData().delete();
		this.setVoiceData(null);
	}
	
	public void playPreview(Context context) {
		if (this.isDownloaded()) {
			this.getVoiceData().playPreview();
		} else {
			MediaPlayer player = MediaPlayer.create(context, Uri.parse(this.getPreview_url()));
			if (player != null)
				player.start();
		}
	}
}
