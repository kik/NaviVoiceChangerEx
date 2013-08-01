package jp.nekoteki.android.navivoicechanger;

import java.io.IOException;
import java.util.zip.ZipException;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;

public class MainMenuActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_menu);
		setTitle(R.string.title_activity_main);
		
		findViewById(R.id.btn_menu_install).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(MainMenuActivity.this, InstallListActivity.class));
			}
		});
		
		findViewById(R.id.btn_menu_maint).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(MainMenuActivity.this, MaintActivity.class));
			}
		});
		
		findViewById(R.id.btn_download).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(MainMenuActivity.this, DownloadActivity.class));

			}
		});
		
		String tos_agree = Config.get(getApplicationContext(), "tos_agree"); 
		if (tos_agree == null || !tos_agree.equals("1")) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					StaticUtils.terminateSelf(getApplicationContext());
				}
			});
			dialog.setPositiveButton(R.string.accept,  new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					Config.set(getApplicationContext(), "tos_agree", "1");
				}
			});
			dialog.setCancelable(false);
			dialog.setTitle(R.string.tos_title);
			dialog.setMessage(R.string.tos);
			dialog.show();
		} 
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}
	
	

}
