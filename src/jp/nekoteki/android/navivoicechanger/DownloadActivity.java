package jp.nekoteki.android.navivoicechanger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.arnx.jsonic.JSON;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

public class DownloadActivity extends Activity {
	
	public class RemoteVoiceDataAdapter extends BaseAdapter {
		protected boolean eol = false;
		protected Context context;
		protected List<RemoteVoiceData> list;
		protected int cur_page = 1; 
		protected boolean loading = false;

		public RemoteVoiceDataAdapter(Context context) {
			super();
			this.context = context;
			this.reset();
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
		
		public void loadList(AbsListView view) {
			if (this.loading || this.eol) return;
			if (this.loading || this.eol) return;
			this.loading = true;
			String url = Config.get(context, "server_url_base") +"/navi_voices.json?page="+Integer.toString(this.cur_page);
				
			new AsyncTask<Object, Void, RemoteVoiceData[]>() {
				protected RemoteVoiceDataAdapter adapter;
				protected ListView view;

				@Override
				protected RemoteVoiceData[] doInBackground(Object... params) {
					AndroidHttpClient client = AndroidHttpClient.newInstance("NaviVoiceChanger");
					try {
						String url = (String) params[0];
						this.view = (ListView) params[1];
						this.adapter = (RemoteVoiceDataAdapter) params[2];
						Log.i(this.getClass().toString(), "Loading URL: "+url);
						HttpResponse res;
						res = client.execute(new HttpGet(url));
						this.adapter.cur_page += 1;
						InputStream json_stream = res.getEntity().getContent();
						RemoteVoiceData[] vdlist = JSON.decode(json_stream, RemoteVoiceData[].class);
						json_stream.close();
						return vdlist;
					} catch (IOException e) {
						Log.d(this.getClass().toString(), "Failed to load from server.");
						e.printStackTrace();
						return null;
					} catch (Exception e) {
						Log.e(this.getClass().toString(), "Unknwon Exception in AsyncTask!!");
						e.printStackTrace();
						return null;
					} finally {
						try {
							client.close();
						} catch (Exception e) {
							// ignore
						}
					}
 				}

				protected void onPostExecute(RemoteVoiceData[] vdlist) {
					if (vdlist == null || vdlist.length == 0) {
						Log.i(this.getClass().toString(),"EOL detected.");
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

			TextView description = new TextView(context);
			description.setTextSize(12);
			description.setText(rvd.getDescription());
			textlayout.addView(description);
			container.addView(textlayout);

			TextView downloaded = new TextView(context);
			if (rvd.isDownloaded()) {
				Drawable dmark = getResources().getDrawable(android.R.drawable.checkbox_on_background);
				dmark.setBounds(0, 0, 20, 20);
				downloaded.setCompoundDrawables(dmark, null, null, null);
				RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				downloaded.setLayoutParams(lp);
				container.addView(downloaded);
			}

			TextView author = new TextView(context);
			if (rvd.getAuthor() != null && !rvd.getAuthor().equals("")) {
				author.setTextSize(14);
				author.setText("By "+rvd.getAuthor());
				RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				if (rvd.isDownloaded()) {
					lp.setMargins(0, 0, 22, 0);
				}
				author.setLayoutParams(lp);
				container.addView(author);
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
		// Show the Up button in the action bar.
		setupActionBar();
		
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

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> list, View item, int pos, long id) {
				item.performLongClick();
			}
		});
	}
	
	protected void scanVoiceData() {
		this.voice_data_list = VoiceData.scanVoiceData(getApplicationContext());
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.download, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	protected void setDownloadOverlay(boolean flag) {
		View view = findViewById(R.id.download_pregrees);
		if (flag) {
			view.setVisibility(View.VISIBLE);
		} else {
			view.setVisibility(View.GONE);
		}
	}
	
	public void goInstallListFromMenu(MenuItem item) {
		startActivity(new Intent(DownloadActivity.this, InstallListActivity.class));
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
					if (this.rvd.isDownloaded())
						context.rvd_list_adapter.notifyDataSetChanged();
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
			// TODO: implement!
			Toast.makeText(this, "Not implemented yet", Toast.LENGTH_SHORT).show();
			break;
		}
		return true;
	}

}
