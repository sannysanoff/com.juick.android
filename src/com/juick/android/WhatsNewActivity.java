package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.webkit.WebView;
import android.widget.RadioButton;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.juickadvanced.R;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/8/12
 * Time: 4:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class WhatsNewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String mode = getIntent().getStringExtra("mode");
        if (mode.equals("whatsnew")) {
            setContentView(R.layout.enable_statistics);
            findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String option = WhatsNew.getSendStatValueFromUI(getWindow().getDecorView());
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(WhatsNewActivity.this);
                    sp.edit().putString("usage_statistics", option).commit();
                    finish();
                }
            });
        }
        if (mode.equals("privacy")) {
            WebView wv = new WebView(this);
            Utils.setupWebView(wv, getString(R.string.privacy_policy));
        }
    }
}
