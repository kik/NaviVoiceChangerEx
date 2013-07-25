package jp.nekoteki.android.navivoicechanger;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipException;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;


public class InstallListActivity extends Activity {
	final static int C_MENU_PREVIEW = 0;
	final static int C_MENU_INSTALL = 1;
	final static int C_MENU_RATE    = 2;
	final static int C_MENU_DELETE  = 3;
	
	private class ListVoiceDataAdapter extends BaseAdapter {
		private Context context;
		private List<VoiceData> list;

		public ListVoiceDataAdapter(Context context) {
			super();
			this.context = context;
			this.rescan();
 		}
		
		public void rescan() {
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
			
			RelativeLayout container = new RelativeLayout(context);
			
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
			
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(this.context);
			alertDialog.setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
									
				}
				
			});
			class InstallClkHdl implements View.OnClickListener {
				public VoiceData vd;
				public InstallListActivity activity;
				
				public InstallClkHdl(VoiceData vd, InstallListActivity activity) {
					this.vd = vd;
					this.activity = activity;
				}
				
				@Override
				public void onClick(View v) {
					try {
						this.vd.install();
					} catch (Exception e) {
						this.activity.showInstallResult(e);
					}
				}
				
			}
			InstallClkHdl click_hdl = new InstallClkHdl(vd, (InstallListActivity) this.context);
			
			btn_install.setOnClickListener(click_hdl);

			android.widget.RelativeLayout.LayoutParams lparam = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			lparam.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			container.addView(btn_install, lparam);
			
			convertView = container;
			return convertView;
		}
		
	}
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_install_list);
		//setupActionBar();
		setTitle(R.string.title_activity_install_list);
		
		VoiceData.copyVoiceAssets(this);
		 
		ListView lv = (ListView) findViewById(R.id.voice_list);
		lv.setAdapter(new ListVoiceDataAdapter(this));
		registerForContextMenu(lv);
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
	
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
		super.onCreateContextMenu(menu, view, info);
		AdapterContextMenuInfo ainfo = (AdapterContextMenuInfo) info;
		ListView listView = (ListView)view;
		
		VoiceData vd = (VoiceData) listView.getItemAtPosition(ainfo.position);
		menu.setHeaderTitle(vd.getTitle());
		menu.add(vd.getId(), C_MENU_PREVIEW, 0, R.string.c_menu_preview);
		menu.add(vd.getId(), C_MENU_INSTALL, 0, R.string.c_menu_install);
		menu.add(vd.getId(), C_MENU_RATE, 0, R.string.c_menu_rate);
		menu.add(vd.getId(), C_MENU_DELETE, 0, R.string.c_menu_delete);

		if (vd.getId() < 1) {
			menu.getItem(2).setEnabled(false);
			menu.getItem(3).setEnabled(false);
		}
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		VoiceData vd = VoiceData.getById(this.getApplicationContext(), item.getGroupId());
		if (vd == null) return true;
		switch (item.getItemId()) {
		case C_MENU_PREVIEW:
			
			break;
		case C_MENU_INSTALL:
			try {
				vd.install();
				this.showInstallResult(null);
			} catch (Exception e) {
				this.showInstallResult(e);
			}
			break;
		case C_MENU_DELETE:
			vd.delete();
			// TODO: remove item from list.
			Toast.makeText(this, R.string.voice_deleted, Toast.LENGTH_SHORT).show();
			break;
		case C_MENU_RATE:
			// TODO: implement!
			Toast.makeText(this, "Not implemented yet", Toast.LENGTH_SHORT).show();
			break;
		}
		return true;
	}

	public void showInstallResult(Exception e) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) { }							
		});
		dialog.setTitle(R.string.install_error);
		if (e == null) {
			dialog.setTitle(R.string.install_success);
			dialog.setMessage(R.string.install_success_message);
		} else if (e instanceof BrokenArchive || e instanceof ZipException) {
			dialog.setMessage(R.string.err_broken_archive);
		} else if (e instanceof DataDirNotFound) {
			dialog.setMessage(R.string.err_no_target);
		} else if (e instanceof IOException) {
			dialog.setMessage(R.string.err_fileio);
		} else {
			dialog.setMessage(R.string.err_unknown);
			Log.e("Inatall Activity", "Unknown Erorr!! ");
			e.printStackTrace();
		}
		dialog.show();
	}
}
