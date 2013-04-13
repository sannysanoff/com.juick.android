package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuickPreferencesActivity extends PreferencesActivity implements IRunningActivity {

    Handler handler;
    boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        handler = new Handler();
        int prefs = getIntent().getIntExtra("prefs", 0);
        super.onCreate(savedInstanceState);
        if (prefs == R.xml.prefs_xmpp) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            boolean privacy_warned = sp.getBoolean("xmpp_privacy_warned", false);
            if (!privacy_warned) {
                launchXMPPPrivacyDialog(this, false);
            }
        }
    }

    public static void launchXMPPPrivacyDialog(final Activity activity, final boolean weDisabled) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        sp.edit().putBoolean("xmpp_privacy_warned", true).putBoolean("xmpp_privacy_should_warn", false).commit();
        Runnable loop = new Runnable() {
            Runnable thiz = this;
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                final AlertDialog alerDialog;
                alerDialog = builder
                        .setTitle(activity.getString(R.string.XMPP_client))
                        .setMessage(activity.getString(R.string.NowUsesServerSideXMPPClient)+" "+ (weDisabled ? activity.getString(R.string.WeTurnedOffXMPP) : ""))
                        .setCancelable(true)
                        .setNeutralButton(R.string.ReadPrivacyPolicy, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new WhatsNew(activity).showPrivacyPolicy(thiz);
                            }
                        })
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }).create();
                alerDialog.show();
            }
        };
        loop.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    protected void onResume() {
        resumed = true;
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getAction().equals("check_new_version")) {
            Toast.makeText(JuickPreferencesActivity.this, getString(R.string.WillCheckForNewVersion), Toast.LENGTH_LONG).show();
            WhatsNew.getLastCheck(this).delete();
            WhatsNew.checkForUpdates(this, new Utils.Function<Void,String>() {
                @Override
                public Void apply(String reason) {
                    Toast.makeText(JuickPreferencesActivity.this, getString(R.string.NewVersionNotFound)+": "+reason, Toast.LENGTH_LONG).show();
                    return null;
                }
            }, true);
        }
        if (intent.getAction().equals("whatsnew")) {
            final WhatsNew whatsNew = new WhatsNew(this);
            ListView lv = new ListView(this);
            final ArrayList<String> lst = new ArrayList<String>();
            for (WhatsNew.ReleaseFeatures feature : whatsNew.features) {
                lst.add(feature.sinceRelease);
            }
            lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, lst));
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("Choose release")
                    .setView(lv)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alertDialog = builder.create();
            alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    MainActivity.restyleChildrenOrWidget(alertDialog.getWindow().getDecorView());
                }
            });
            alertDialog.show();
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    alertDialog.dismiss();
                    whatsNew.reportFeatures(position, false, null);
                }
            });
        }
        if (intent.getAction().equals("privacy")) {
            new WhatsNew(this).showPrivacyPolicy(null);
        }
        if (intent.getAction().equals("various_info")) {
            Intent prefsIntent = new Intent(this, NewJuickPreferenceActivityMT.class);
            prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.REPORTS_CHARTS.name());
            startActivity(prefsIntent);
        }
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

    @Override
    public boolean isRunning() {
        return resumed;
    }
}
