package jp.nekoteki.android.navivoicechanger;

import android.os.Bundle;
import android.app.Activity;
import android.app.ProgressDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
//import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.os.Build;

import io.github.kik.navivoicechangerex.R;

public class MaintActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_maint);
		// Show the Up button in the action bar.
		setupActionBar();
		
		findViewById(R.id.btn_purge_installed).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ProgressDialog progress = new ProgressDialog(v.getContext());
				progress.setTitle("Please wait");
				progress.setMessage("Purging files...");
				progress.show();
				VoiceData.purgeVoiceDataFromNavi(v.getContext());
				StaticUtils.killMapsProcess(v.getContext());
				progress.dismiss();
				Toast.makeText(v.getContext(), R.string.msg_installed_removed, Toast.LENGTH_SHORT).show();
			}
		});
		
		findViewById(R.id.btn_purge_downloaded).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ProgressDialog progress = new ProgressDialog(v.getContext());
				progress.setTitle("Please wait");
				progress.setMessage("Purging files...");
				progress.show();
				VoiceData.purgeDownloaded(v.getContext());
				progress.dismiss();
				Toast.makeText(v.getContext(), R.string.msg_downloaded_removed, Toast.LENGTH_SHORT).show();
			}
		});
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}*/
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.maint, menu);
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
			//NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
