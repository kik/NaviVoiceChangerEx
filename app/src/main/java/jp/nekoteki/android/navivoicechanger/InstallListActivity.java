package jp.nekoteki.android.navivoicechanger;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import io.github.kik.navivoicechangerex.R;


public class InstallListActivity extends AppCompatActivity {
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
			this.notifyDataSetChanged();
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
			
			LinearLayout textlayout = new LinearLayout(context);
			textlayout.setOrientation(LinearLayout.VERTICAL);
			textlayout.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
			textlayout.setFocusable(false);
			textlayout.setFocusableInTouchMode(false);

			TextView title = new TextView(context);
			title.setText(vd.getTitle());
			title.setTextColor(Color.BLACK);
			title.setTextSize(16);
			textlayout.addView(title);

			TextView description = new TextView(context);
			description.setTextSize(12);
			description.setText(vd.getDescription());
			textlayout.addView(description);
			
			container.addView(textlayout);
			
			TextView author = new TextView(context);
			if (vd.getAuthor() != null && !vd.getAuthor().equals("")) {
				author.setTextSize(14);
				author.setText("By "+vd.getAuthor());
				RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				author.setLayoutParams(lp);
				container.addView(author);
			}

			container.setPadding(0, 5, 0, 5);
			return container;
		}
		
	}
	
	protected ListView list_view;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_install_list);
		setTitle(R.string.title_activity_install_list);
		
		VoiceData.copyVoiceAssets(this);
		 
		ListView lv = (ListView) findViewById(R.id.voice_list);
		this.list_view = lv;
		lv.setAdapter(new ListVoiceDataAdapter(this));
		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> list, View item, int pos, long id) {
				item.performLongClick();
			}
		});
		registerForContextMenu(lv);
		
		findViewById(R.id.btn_download).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivity(new Intent(InstallListActivity.this, DownloadActivity.class));
			}
		});
	}

	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
		super.onCreateContextMenu(menu, view, info);
		AdapterContextMenuInfo ainfo = (AdapterContextMenuInfo) info;
		ListView listView = (ListView)view;
		
		VoiceData vd = (VoiceData) listView.getItemAtPosition(ainfo.position);
		menu.setHeaderTitle(vd.getTitle());
		menu.add(vd.getId(), C_MENU_PREVIEW, 0, R.string.c_menu_preview);
		menu.add(vd.getId(), C_MENU_INSTALL, 0, R.string.c_menu_install);
		menu.add(vd.getId(), C_MENU_RATE,    0, R.string.c_menu_rate);
		menu.add(vd.getId(), C_MENU_DELETE,  0, R.string.c_menu_delete);

		if (vd.getId() < 1) {
			menu.getItem(C_MENU_RATE).setEnabled(false);
			menu.getItem(C_MENU_DELETE).setEnabled(false);
		}
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		VoiceData vd = VoiceData.getById(this.getApplicationContext(), item.getGroupId());
		if (vd == null) return true;
		switch (item.getItemId()) {
		case C_MENU_PREVIEW:
			vd.playPreview();
			break;
		case C_MENU_INSTALL:
			vd.installAndShowResults(this);
			break;
		case C_MENU_DELETE:
			vd.delete();
			((ListVoiceDataAdapter) this.list_view.getAdapter()).rescan(); // TODO: slow...
			Toast.makeText(this, R.string.voice_deleted, Toast.LENGTH_SHORT).show();
			break;
		case C_MENU_RATE:
			vd.promptToRate(this);
			break;
		}
		return true;
	}
}
