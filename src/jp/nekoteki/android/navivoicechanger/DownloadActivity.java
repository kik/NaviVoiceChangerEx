package jp.nekoteki.android.navivoicechanger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.arnx.jsonic.JSON;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterViewFlipper;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

public class DownloadActivity extends Activity {
	
	public class RemoteVoiceDataAdapter extends BaseAdapter {
		final static String SERVER_BASE_URL = "http://tempest.private.nemui.org:3000";
		
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
			this.loading = true;
			String url = SERVER_BASE_URL+"/navi_voices.json?page="+Integer.toString(this.cur_page);
				
			new AsyncTask<Object, Void, RemoteVoiceData[]>() {
				protected RemoteVoiceDataAdapter adapter;
				protected ListView view;

				@Override
				protected RemoteVoiceData[] doInBackground(Object... params) {
					try {
						String url = (String) params[0];
						this.view = (ListView) params[1];
						this.adapter = (RemoteVoiceDataAdapter) params[2];
						AndroidHttpClient client = AndroidHttpClient.newInstance("NaviVoiceChanger");
						Log.i(this.getClass().toString(), "Loading URL: "+url);
						HttpResponse res;
						res = client.execute(new HttpGet(url));
						this.adapter.cur_page += 1;
						InputStream json_stream = res.getEntity().getContent();
						RemoteVoiceData[] vdlist = JSON.decode(json_stream, RemoteVoiceData[].class);
						json_stream.close();
						client.close();
						return vdlist;
					} catch (IOException e) {
						Log.d(this.getClass().toString(), "Failed to load from server.");
						e.printStackTrace();
						return null;
					} catch (Exception e) {
						Log.e(this.getClass().toString(), "Unknwon Exception in AsyncTask!!");
						e.printStackTrace();
						return null;
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
							rvd.setDownloaded(true);
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
			title.setText(rvd.getTitle() + (rvd.isDownloaded() ? " [L]" : " [R]")); // TODO: fix design downloaded marker.
			title.setTextColor(Color.BLACK);
			title.setTextSize(16);
			textlayout.addView(title);

			TextView description = new TextView(context);
			description.setTextSize(13);
			description.setText(rvd.getDescription());
			textlayout.addView(description);
			
			container.addView(textlayout);
			
			convertView = container;
			return convertView;
		}
		
	}

	public View list_footer_marker = null;
	public RemoteVoiceDataAdapter vd_list_adapter;
	public List<VoiceData> voice_data_list;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download);
		// Show the Up button in the action bar.
		setupActionBar();
		
		this.vd_list_adapter = new RemoteVoiceDataAdapter(this); 
		this.list_footer_marker = getLayoutInflater().inflate(R.layout.list_progress_footer, null);
		this.voice_data_list = VoiceData.scanVoiceData(this);

		ListView lv = (ListView) findViewById(R.id.download_item_list);
		lv.addFooterView(this.list_footer_marker);
		lv.setAdapter(this.vd_list_adapter);
		lv.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				// nothing to do...
			}
			
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				if (totalItemCount == firstVisibleItem + visibleItemCount)
					((DownloadActivity) view.getContext()).vd_list_adapter.loadList(view);
			}
		});
		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> list, View item, int pos, long id) {
				RemoteVoiceData rvd = (RemoteVoiceData) list.getAdapter().getItem(pos);
				if (rvd.isDownloaded()) return; // TODO: Show dialog to choice to go to install activity or re-download.
				
				((DownloadActivity) list.getContext()).setDownloadOverlay(true);
				
				new AsyncTask<Object, Void, Boolean>() {
					protected DownloadActivity context;
//					protected int pos;
//					protected long id;
					protected RemoteVoiceData rvd;
					
					@Override
					protected Boolean doInBackground(Object... params) {
						this.context = (DownloadActivity) params[0];
						this.rvd     = (RemoteVoiceData)  params[1];
//						this.pos     = ((Integer) params[2]).intValue();
//						this.id      = ((Long) params[3]).longValue();
						try {
							rvd.download(context);
						} catch (Exception e) {
							e.printStackTrace();
							return false;
						}
						return true;
					}
					
					protected void onPostExecute(Boolean flag) {
						if (flag) {
							this.rvd.setDownloaded(true);
							context.vd_list_adapter.notifyDataSetChanged();
						}
						context.setDownloadOverlay(false);
					}
					
				}.execute(list.getContext(), rvd, pos, id);
			}
		});
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
}
