package jp.nekoteki.android.navivoicechanger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import net.arnx.jsonic.JSON;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import io.github.kik.navivoicechangerex.R;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadActivity extends Activity {

	public class RemoteVoiceDataAdapter extends BaseAdapter {
		protected boolean eol = false;
		protected Context context;
		protected List<RemoteVoiceData> list;
		protected int cur_page = 1;
		protected boolean loading = false;
		protected String q;
		protected String order = "time";

		public RemoteVoiceDataAdapter(Context context) {
			super();
			this.context = context;
			this.reset();
 		}

		public String getOrder() {
			return this.order;
		}

		@Override
		public int getCount() {
			return this.list.size();
		}

		@Override
		public Object getItem(int position) {
			return this.list.get(position);
		}

		@Override
		public long getItemId(int position) {
			return ((RemoteVoiceData) this.getItem(position)).getId();
		}

		public RemoteVoiceData getItemById(int id) {
			for (RemoteVoiceData rvd: this.list) {
				if (rvd.getId() == id) return rvd;
			}
			return null;
		}

		public void reset() {
			this.cur_page = 1;
			this.list = new ArrayList<RemoteVoiceData>();
			this.notifyDataSetChanged();
		}

		public void add(RemoteVoiceData vd) {
			this.list.add(vd);
		}

		@SuppressLint("StaticFieldLeak")
		public void loadList(AbsListView view) {
			if (this.loading || this.eol) return;
			this.loading = true;
			String url = Config.get(context, "server_url_base") +"/navi_voices.json?page="+Integer.toString(this.cur_page)+"&order="+this.order;
			if (this.q != null && !this.q.equals("")) {
				try {
					url += "&q="+URLEncoder.encode(q, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// ignore
					e.printStackTrace();
				}
			}

			new AsyncTask<Object, Void, RemoteVoiceData[]>() {
				protected RemoteVoiceDataAdapter adapter;
				protected ListView view;

				@Override
				protected RemoteVoiceData[] doInBackground(Object... params) {
					String url = (String) params[0];
					this.view = (ListView) params[1];
					this.adapter = (RemoteVoiceDataAdapter) params[2];
					Log.i(this.getClass().toString(), "Loading URL: "+url);

					Request req = new Request.Builder()
							.url(url)
							.get()
							.build();
					try (Response res = new OkHttpClient().newCall(req).execute()) {
						this.adapter.cur_page += 1;
						if (res.body() == null) {
							return null;
						}
						try (var json_stream = res.body().byteStream()) {
							RemoteVoiceData[] vdlist = JSON.decode(json_stream, RemoteVoiceData[].class);
							return vdlist;
						}
					} catch (IOException e) {
						Log.e(this.getClass().toString(), "Failed to load from server.");
						e.printStackTrace();
						return null;
					}
 				}

				protected void onPostExecute(RemoteVoiceData[] vdlist) {
					if (vdlist == null || vdlist.length == 0) {
						Log.d(this.getClass().toString(),"EOL detected.");
						this.adapter.eol = true;
						this.view.removeFooterView(((DownloadActivity) this.view.getContext()).list_footer_marker);
						this.adapter.loading = false;
						this.adapter.notifyDataSetChanged();
						this.view.invalidateViews();
						return;
					}

					List<VoiceData> locals = ((DownloadActivity) this.adapter.context).voice_data_list;
					Log.d(this.getClass().toString(), "Checking items...");
					for (RemoteVoiceData rvd: vdlist) {
						Log.d(this.getClass().toString(), "Item #" + Integer.toString(rvd.getId())+": "+rvd.getTitle());
						for (VoiceData vd: locals) {
							if (vd.getId() != rvd.getId())
								continue;
							rvd.setVoiceData(vd);
							break;
						}
						this.adapter.list.add(rvd);
					}
					Log.d(this.getClass().toString(), "Item load done.");
					this.adapter.notifyDataSetChanged();
					this.view.invalidateViews();
					this.adapter.loading = false;
				}
			}.execute(url, view, this);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			RemoteVoiceData rvd = (RemoteVoiceData) getItem(position);

			RelativeLayout container = new RelativeLayout(context);

			LinearLayout textlayout = new LinearLayout(context);
			textlayout.setOrientation(LinearLayout.VERTICAL);
			textlayout.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
			textlayout.setFocusable(false);
			textlayout.setFocusableInTouchMode(false);

			TextView title = new TextView(context);
			title.setText(rvd.getTitle());
			title.setTextColor(Color.BLACK);
			title.setTextSize(16);
			textlayout.addView(title);

			if (rvd.getAuthor() != null && !rvd.getAuthor().equals("")) {
				TextView author = new TextView(context);
				author.setTextSize(14);
				author.setText(getResources().getString(R.string.author, rvd.getAuthor()));
				textlayout.addView(author);
			}

			LinearLayout infoline = new LinearLayout(context);
			infoline.setOrientation(LinearLayout.HORIZONTAL);
			if (rvd.getRating() > 0) {
				RatingBar rating = new RatingBar(context, null, android.R.attr.ratingBarStyleSmall);
				rating.setMax(5);
				rating.setNumStars(5);
				rating.setStepSize(0.5f);
				rating.setRating(rvd.getRating());
				infoline.addView(rating);
			}

			if (rvd.getDlcount() > 0) {
				TextView downloads = new TextView(context);
				downloads.setTextSize(12);
				downloads.setText(getResources().getString(R.string.downloads, rvd.getDlcount()));
				downloads.setPadding(10, 0, 0, 0);
				infoline.addView(downloads);
			}

			textlayout.addView(infoline);

			if (rvd.getDescription() != null && !rvd.getDescription().equals("")) {
				TextView description = new TextView(context);
				description.setTextSize(12);
				description.setText(rvd.getDescription());
				textlayout.addView(description);
			}

			container.addView(textlayout);

			TextView downloaded = new TextView(context);
			if (rvd.isDownloaded()) {
				Drawable dmark = ResourcesCompat.getDrawable(getResources(), android.R.drawable.stat_sys_download_done, null);
				dmark.setBounds(0, 0, 20, 20);
				downloaded.setCompoundDrawables(dmark, null, null, null);
				RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				downloaded.setLayoutParams(lp);
				downloaded.setBackgroundColor(Color.GRAY);
				downloaded.setTextSize(1);
				container.addView(downloaded);
			}

			container.setPadding(0, 5, 0, 5);
			return container;
		}

	}

	private static final int C_MENU_PREVIEW = 0;
	private static final int C_MENU_DOWNLOAD = 1;
	private static final int C_MENU_INSTALL = 2;
	private static final int C_MENU_RATE = 3;
	private static final int C_MENU_DELETE = 4;

	public View list_footer_marker = null;
	public RemoteVoiceDataAdapter rvd_list_adapter;
	public List<VoiceData> voice_data_list;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download);

		this.rvd_list_adapter = new RemoteVoiceDataAdapter(this);
		this.list_footer_marker = getLayoutInflater().inflate(R.layout.list_progress_footer, null);
		this.scanVoiceData();

		ListView lv = (ListView) findViewById(R.id.download_item_list);
		lv.addFooterView(this.list_footer_marker);
		lv.setAdapter(this.rvd_list_adapter);
		lv.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				// nothing to do...
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				if (totalItemCount == firstVisibleItem + visibleItemCount)
					((DownloadActivity) view.getContext()).rvd_list_adapter.loadList(view);
			}
		});

		registerForContextMenu(lv);

		lv.setOnItemClickListener((list, item, pos, id) -> item.performLongClick());

		findViewById(R.id.btn_dl_opts).setOnClickListener(v -> {
			AlertDialog.Builder dialog = new AlertDialog.Builder(DownloadActivity.this);
			class DlOptsApplyClkListener implements DialogInterface.OnClickListener {
				public View opt_view;
				@Override
				public void onClick(DialogInterface dialog, int which) {
					RemoteVoiceDataAdapter rvd_adpt = DownloadActivity.this.rvd_list_adapter;
					if (((RadioButton) opt_view.findViewById(R.id.dlopt_order_dlcount)).isChecked()) {
						rvd_adpt.order = "dlcount";
					} else if (((RadioButton) opt_view.findViewById(R.id.dlopt_order_rating)).isChecked()) {
						rvd_adpt.order = "rating";
					} else {
						rvd_adpt.order = "time";
					}
					rvd_adpt.q = ((EditText) opt_view.findViewById(R.id.dlopt_filter_q)).getText().toString();
					rvd_adpt.reset();
				}
			}
			DlOptsApplyClkListener apply_hdl = new DlOptsApplyClkListener();
			apply_hdl.opt_view = View.inflate(DownloadActivity.this, R.layout.dl_filter, null);
			RemoteVoiceDataAdapter rvd_adpt = DownloadActivity.this.rvd_list_adapter;
			if (rvd_adpt.order == "dlcount") {
				((RadioButton) apply_hdl.opt_view.findViewById(R.id.dlopt_order_dlcount)).setChecked(true);
			} else if (rvd_adpt.order == "rating") {
				((RadioButton) apply_hdl.opt_view.findViewById(R.id.dlopt_order_rating)).setChecked(true);
			} else {
				((RadioButton) apply_hdl.opt_view.findViewById(R.id.dlopt_order_time)).setChecked(true);
			}
			((EditText) apply_hdl.opt_view.findViewById(R.id.dlopt_filter_q)).setText(rvd_adpt.q);

			dialog.setPositiveButton(R.string.apply, apply_hdl);
			dialog.setNegativeButton(android.R.string.cancel, (dialog1, which) -> { });
			dialog.setTitle(R.string.dllist_opts_title);
			dialog.setView(apply_hdl.opt_view);
			dialog.show();
		});
	}

	protected void scanVoiceData() {
		this.voice_data_list = VoiceData.scanVoiceData(getApplicationContext());
	}

	protected void setDownloadOverlay(boolean flag) {
		View view = findViewById(R.id.download_pregrees);
		if (flag) {
			view.setVisibility(View.VISIBLE);
		} else {
			view.setVisibility(View.GONE);
		}
	}

	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
		super.onCreateContextMenu(menu, view, info);
		AdapterContextMenuInfo ainfo = (AdapterContextMenuInfo) info;
		ListView listView = (ListView)view;

		RemoteVoiceData rvd = (RemoteVoiceData) listView.getItemAtPosition(ainfo.position);
		menu.setHeaderTitle(rvd.getTitle());
		menu.add(rvd.getId(), C_MENU_PREVIEW,  0, R.string.c_menu_preview);
		menu.add(rvd.getId(), C_MENU_DOWNLOAD, 0, R.string.c_menu_download);
		menu.add(rvd.getId(), C_MENU_INSTALL,  0, R.string.c_menu_install);
		menu.add(rvd.getId(), C_MENU_RATE,     0, R.string.c_menu_rate);
		menu.add(rvd.getId(), C_MENU_DELETE,   0, R.string.c_menu_delete);

		if (rvd.isDownloaded()) {
			menu.getItem(C_MENU_DOWNLOAD).setEnabled(false);
		} else {
			menu.getItem(C_MENU_INSTALL).setEnabled(false);
			menu.getItem(C_MENU_DELETE).setEnabled(false);
			menu.getItem(C_MENU_RATE).setEnabled(false);
		}
	}

	@SuppressLint("StaticFieldLeak")
	public boolean onContextItemSelected(MenuItem item) {
		RemoteVoiceData rvd = this.rvd_list_adapter.getItemById(item.getGroupId());
		if (rvd == null) return true;
		switch (item.getItemId()) {
		case C_MENU_PREVIEW:
			rvd.playPreview(this.getApplicationContext());
			break;
		case C_MENU_DOWNLOAD:
			if (rvd.isDownloaded()) return true;
			this.setDownloadOverlay(true);

			new AsyncTask<Object, Void, Boolean>() {
				protected DownloadActivity context;
				protected RemoteVoiceData rvd;

				@Override
				protected Boolean doInBackground(Object... params) {
					this.context = (DownloadActivity) params[0];
					this.rvd     = (RemoteVoiceData)  params[1];
					try {
						rvd.download(context);
					} catch (Exception e) {
						e.printStackTrace();
						return false;
					}
					return true;
				}

				protected void onPostExecute(Boolean flag) {
					if (flag && this.rvd.isDownloaded()) {
						context.rvd_list_adapter.notifyDataSetChanged();
						Toast.makeText(context, R.string.download_success, Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show();
					}
					context.setDownloadOverlay(false);
				}
			}.execute(this, rvd);
			break;
		case C_MENU_INSTALL:
			if (rvd.isDownloaded())
				rvd.getVoiceData().installAndShowResults(this);
			break;
		case C_MENU_DELETE:
			rvd.delete();
			this.rvd_list_adapter.notifyDataSetChanged();
			Toast.makeText(this, R.string.voice_deleted, Toast.LENGTH_SHORT).show();
			break;
		case C_MENU_RATE:
			if (!rvd.isDownloaded()) return true;
			rvd.getVoiceData().promptToRate(this);
			break;
		}
		return true;
	}

}
