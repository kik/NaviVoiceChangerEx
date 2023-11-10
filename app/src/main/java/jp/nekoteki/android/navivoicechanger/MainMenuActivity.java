package jp.nekoteki.android.navivoicechanger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;

import java.util.UUID;

import io.github.kik.navivoicechangerex.App;
import io.github.kik.navivoicechangerex.R;
import io.github.kik.navivoicechangerex.SettingsActivity;

public class MainMenuActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_menu);
		setTitle(R.string.title_activity_main);
		
		findViewById(R.id.btn_menu_install).setOnClickListener(v -> startActivity(new Intent(MainMenuActivity.this, InstallListActivity.class)));
		
		findViewById(R.id.btn_menu_maint).setOnClickListener(v -> startActivity(new Intent(MainMenuActivity.this, MaintActivity.class)));
		
		findViewById(R.id.btn_download).setOnClickListener(v -> startActivity(new Intent(MainMenuActivity.this, DownloadActivity.class)));

		findViewById(R.id.btn_menu_settings).setOnClickListener(v -> startActivity(new Intent(MainMenuActivity.this, SettingsActivity.class)));
		// XPosedがインストールされているときだけ設定画面を有効にする
		// でもデバッグビルドのときも設定画面を出したい
		findViewById(R.id.btn_menu_settings).setEnabled(false);
		App.waitXposed(xposed -> {
			findViewById(R.id.btn_menu_settings).setEnabled(true);
		}, getMainExecutor());
		if (App.isDebuggable(this)) {
			new Handler(getMainLooper()).postDelayed(() -> {
				findViewById(R.id.btn_menu_settings).setEnabled(true);
			}, 5000);
		}

		String tos_agree = Config.get(getApplicationContext(), "tos_agree"); 
		if (tos_agree == null || !tos_agree.equals("1")) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setNegativeButton(R.string.decline, (dialog1, which) -> StaticUtils.terminateSelf(getApplicationContext()));
			dialog.setPositiveButton(R.string.accept, (arg0, arg1) -> Config.set(getApplicationContext(), "tos_agree", "1"));
			dialog.setCancelable(false);
			dialog.setTitle(R.string.tos_title);
			dialog.setMessage(R.string.tos);
			dialog.show();
		} 

		String ident = Config.get(getApplicationContext(), "ident");
		if (ident == null || ident.equals("")) {
			Config.set(getApplicationContext(), "ident", "nvcapp:"+UUID.randomUUID().toString());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}
	
	

}
