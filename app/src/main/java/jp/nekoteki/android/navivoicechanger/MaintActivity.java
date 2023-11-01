package jp.nekoteki.android.navivoicechanger;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import io.github.kik.navivoicechangerex.R;

public class MaintActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_maint);

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
}
