package jp.nekoteki.android.navivoicechanger;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipException;

import android.os.Bundle;
import android.app.Activity;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;


public class InstallListActivity extends Activity {
	
	private class ListVoiceDataAdapter extends BaseAdapter {
		private Context context;
		private List<VoiceData> list;

		public ListVoiceDataAdapter(Context context) {
			super();
			this.context = context;
			VoiceData.copyVoiceAssets(context);
			this.list = VoiceData.scanVoiceData(context);
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
			return ((VoiceData) this.getItem(position)).getId();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			VoiceData vd = (VoiceData) getItem(position);
			
			LinearLayout container = new LinearLayout(context);
			container.setOrientation(LinearLayout.HORIZONTAL);
			
			LinearLayout layout = new LinearLayout(context);
			layout.setOrientation(LinearLayout.VERTICAL);

			TextView title = new TextView(context);
			title.setText(vd.getTitle());
			title.setTextColor(Color.BLACK);
			layout.addView(title);

			TextView description = new TextView(context);
			description.setText(vd.getDescription());
			layout.addView(description);

			container.addView(layout);
			
			ImageView btn_install = new ImageView(context);
			btn_install.setImageResource(android.R.drawable.ic_menu_add);
			
			class InstallClkHdl implements View.OnClickListener {
				public VoiceData vd;
				
				public InstallClkHdl(VoiceData vd) {
					this.vd = vd;
				}
				
				@Override
				public void onClick(View v) {
					try {
						this.vd.install();
					} catch (ZipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (DataDirNotFound e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (BrokenArchive e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
			}
			InstallClkHdl click_hdl = new InstallClkHdl(vd);
			
			btn_install.setOnClickListener(click_hdl);

			container.addView(btn_install);
			
			convertView = container;
			return convertView;
		}
		
	}
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_install_list);
		//setupActionBar();
		
		ListView lv = (ListView) findViewById(R.id.voice_list);
		lv.setAdapter(new ListVoiceDataAdapter(this.getApplicationContext()));
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
		getMenuInflater().inflate(R.menu.install_list, menu);
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

}
