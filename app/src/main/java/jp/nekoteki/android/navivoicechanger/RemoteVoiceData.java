package jp.nekoteki.android.navivoicechanger;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteVoiceData {
	private int id;
	private String ini_url;
	private String archive_url;
	private String preview_url;
	private float rating;
	private String title;
	private String unit;
	private String author;
	private String description;
	private VoiceData voice_data;
	private int dlcount;
	
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
		this.downloadFile(this.archive_url, new File(datadir, VoiceData.ARCHIVE_FILENAME));
		this.downloadFile(this.preview_url, new File(datadir, VoiceData.PREVIEW_FILESNAME));
		this.downloadFile(this.ini_url,     new File(datadir, VoiceData.DATA_INI));
		try {
			this.addDownloadCount(context);
		} catch (Exception e) {
			// Ignore ALL errors on count up.... 
		}
		this.setVoiceData(new VoiceData(datadir, context));
	}
	
	protected void downloadFile(String url, File file) throws IOException {
		Log.i(this.getClass().toString(), "Start download: "+url+" -> "+file.getAbsolutePath());

		Request req = new Request.Builder()
				.url(url)
				.get()
				.build();
		try (Response res = new OkHttpClient().newCall(req).execute()) {
			if (res.code() != 200 || res.body() == null) {
				String msg = "Server returns bad status code: "+ Integer.toString(res.code());
				Log.e("VoiceData", msg);
				throw new IOException(msg);
			}
			try (var is = res.body().byteStream()) {
				try (var os = new FileOutputStream(file)) {
					VoiceData.copyStream(is, os);
				}
			}
		}
	}
	
	protected void addDownloadCount(Context context) throws IOException {
		Request req = new Request.Builder()
				.url(Config.get(context, "server_url_base")+ "/navi_voices/" + Integer.toString(this.getId()) + "/dl_log_entries.json")
				.post(new FormBody.Builder()
						.add("dl_log_entry[ident]", Config.get(context, "ident"))
						.build())
				.build();
		try (Response res = new OkHttpClient().newCall(req).execute()) {
			if (res.code() != 201) {
				String msg = "Server returns bad status code: "+ Integer.toString(res.code());
				Log.e(this.getClass().toString(), msg);
				throw new IOException(msg);
			}
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
	public int getDlcount() {
		return dlcount;
	}
	public void setDlcount(int dlcount) {
		this.dlcount = dlcount;
	}
}
