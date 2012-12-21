package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebView;
import android.widget.*;
import com.google.gson.Gson;
import com.juickadvanced.R;
import com.juickadvanced.xmpp.ServerToClient;
import com.juickadvanced.xmpp.XMPPConnectionSetup;

import java.util.HashSet;

/**
 */
public class XMPPPreferenceActivity extends Activity {

    XMPPConnectionSetup value;
    SharedPreferences sp;
    Gson gson = new Gson();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xmpp_setup);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        String oldConfig = sp.getString("xmpp_config", "");
        if (oldConfig.length() != 0) {
            value = gson.fromJson(oldConfig, XMPPConnectionSetup.class);
        } else {
            value = new XMPPConnectionSetup("someone@jabber.org","",5222, 50, "JuickAdvanced","",true);
        }
        View view = getWindow().getDecorView();
        final Spinner templates = (Spinner) view.findViewById(R.id.template_selection);
        final TextView jid = (TextView) view.findViewById(R.id.jid);
        final TextView password = (TextView) view.findViewById(R.id.password);
        final TextView port = (TextView) view.findViewById(R.id.port);
        final TextView server = (TextView) view.findViewById(R.id.server);
        final CheckBox secure = (CheckBox) view.findViewById(R.id.secure);
        final TextView priority = (TextView) view.findViewById(R.id.priority);
        final TextView resource = (TextView) view.findViewById(R.id.resource);
        final Button btn = (Button) view.findViewById(R.id.test_button);
        final Button readmebtn = (Button) view.findViewById(R.id.readme_button);
        AdapterView.OnItemSelectedListener templatesUpdater = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch(position) {
                    case 1: // plain jabber
                        jid.setText("xxxxxxx@jabber.ru");
                        server.setText("");
                        port.setText("5222");
                        jid.requestFocus();
                        break;
                    case 2:
                        jid.setText("xxxxxxx@gmail.com");
                        server.setText("talk.google.com");
                        port.setText("5222");
                        secure.setChecked(true);
                        jid.requestFocus();
                        break;
                    case 3:
                        jid.setText("xxxxxx@ya.ru");
                        server.setText("xmpp.yandex.ru");
                        port.setText("5222");
                        jid.requestFocus();
                        break;
                }
            }
        };
        templates.setOnItemSelectedListener(templatesUpdater);
        readmebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView wv = new WebView(XMPPPreferenceActivity.this);
                Utils.setupWebView(wv, getString(R.string.XMPPReadme));
                new AlertDialog.Builder(XMPPPreferenceActivity.this)
                        .setTitle(R.string.ReadThis)
                        .setView(wv)
                        .setCancelable(true)
                        .show();
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ProgressDialog pd = new ProgressDialog(XMPPPreferenceActivity.this);
                pd.setMessage("Connecting to server..");
                pd.setIndeterminate(true);
                pd.show();
                pd.setCancelable(true);
                final XMPPConnectionSetup testValue = new XMPPConnectionSetup();

                testValue.jid = jid.getText().toString();
                testValue.password = password.getText().toString();
                testValue.port = atoi(port.getText().toString());
                testValue.resource = resource.getText().toString();
                testValue.server = server.getText().toString();
                testValue.priority = atoi(priority.getText().toString());
                testValue.secure = secure.isChecked();

                new Thread("XMPP test connect") {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.setMessage("Logging in...");
                            }
                        });
                        final JAXMPPClient client = new JAXMPPClient();
                        final String error = client.loginXMPP(XMPPPreferenceActivity.this, JuickAdvancedApplication.foreverHandler, testValue, new HashSet<String>());
                        if (error != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (pd.isShowing()) {
                                        Toast.makeText(XMPPPreferenceActivity.this, error, Toast.LENGTH_LONG).show();
                                        pd.hide();
                                    }
                                }
                            });
                        } else {
                            client.sendDisconnect(new Utils.Function<Void, ServerToClient>() {
                                @Override
                                public Void apply(ServerToClient serverToClient) {
                                    client.disconnect();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            pd.hide();
                                            Toast.makeText(XMPPPreferenceActivity.this, "SUCCESS !", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    return null;
                                }
                            });
                        }
                    }
                }.start();
            }
        });
        if (value == null) value = new XMPPConnectionSetup();
        jid.setText(value.getLogin()+"@"+value.getService());
        password.setText(value.password);
        port.setText("" + value.port);
        resource.setText(value.resource);
        server.setText(value.server);
        priority.setText("" + value.priority);
        secure.setChecked(value.secure);
        findViewById(R.id.okbutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                value.jid = jid.getText().toString();
                value.password = password.getText().toString();
                value.port = atoi(port.getText().toString());
                value.resource = resource.getText().toString();
                value.server = server.getText().toString();
                value.priority = atoi(priority.getText().toString());
                value.secure = secure.isChecked();
                String s = gson.toJson(value);
                sp.edit().putString("xmpp_config", s).commit();
                finish();
            }
        });
    }
    private int atoi(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
