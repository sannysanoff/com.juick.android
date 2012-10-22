package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.*;
import com.google.gson.Gson;
import com.juickadvanced.R;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;

/**
 */
public class XMPPPreferenceActivity extends Activity {

    public static class Value {
        public String login;
        public String password;
        public String service;
        public String server;
        public String jid;
        public int port;
        public String resource;
        public int priority;
        public boolean secure;

        public Value() {
        }

        public Value(String jid, String password, int port, int priority, String resource, String server, boolean secure) {
            this.port = port;
            this.jid = jid;
            this.priority = priority;
            this.resource = resource;
            this.server = server;
            this.secure = secure;
        }

        public String getJid() {
            if (jid == null) {
                return nvl(login)+"@"+nvl(getService());
            }
            return jid;
        }

        private String nvl(String str) {
            if (str == null) return "";
            return str;
        }


        public String getLogin() {
            if (jid == null || jid.length() == 0) {
                return login;
            }
            int index = jid.indexOf("@");
            if (index == -1) {
                return jid;
            }
            return jid.substring(0, index);
        }

        public String getService() {
            if (jid == null || jid.length() == 0) {
                if (service == null || service.length() == 0) {
                    return server;
                }
                return service;
            }
            int index = jid.indexOf("@");
            if (index == -1) {
                return server;
            }
            return jid.substring(index+1);
        }

        public String getServer() {
            if (server == null || server.length() == 0) {
                return getService();
            }
            return server;
        }
    }

    Value value;
    SharedPreferences sp;
    Gson gson = new Gson();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xmpp_setup);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        String oldConfig = sp.getString("xmpp_config", "");
        value = gson.fromJson(oldConfig, Value.class);
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
                //To change body of implemented methods use File | Settings | File Templates.
            }
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object itemAtPosition = templates.getItemAtPosition(position);
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
                    case 3:
                        jid.setText("xxxxxx@ya.ru");
                        server.setText("xmpp.yandex.ru");
                        port.setText("5222");
                        jid.requestFocus();
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
                String svr = server.getText().toString();
                final Value testValue = new Value();

                testValue.jid = jid.getText().toString();
                testValue.password = password.getText().toString();
                testValue.port = atoi(port.getText().toString());
                testValue.resource = resource.getText().toString();
                testValue.server = server.getText().toString();
                testValue.priority = atoi(priority.getText().toString());
                testValue.secure = secure.isChecked();

                final ConnectionConfiguration configuration = new ConnectionConfiguration(testValue.getServer(), testValue.port, testValue.getService());
                configuration.setSecurityMode(secure.isChecked() ? ConnectionConfiguration.SecurityMode.required : ConnectionConfiguration.SecurityMode.enabled);
                SASLAuthentication.supportSASLMechanism("PLAIN", 0);
                final XMPPConnection connection = new XMPPConnection(configuration);
                pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        pd.hide();
                    }
                });
                new Thread("XMPP test connect") {
                    @Override
                    public void run() {
                        try {
                            connection.connect();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pd.setMessage("Logging in...");
                                }
                            });
                            connection.login(testValue.getLogin(), password.getText().toString());
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pd.hide();
                                    Toast.makeText(XMPPPreferenceActivity.this, "SUCCESS !", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (pd.isShowing()) {
                                        Toast.makeText(XMPPPreferenceActivity.this, e.toString(), Toast.LENGTH_LONG).show();
                                        pd.hide();
                                    }
                                }
                            });
                        } finally {
                            try {
                                connection.disconnect();
                            } catch (Exception e) {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                    }
                }.start();
            }
        });
        if (value == null) value = new Value();
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
