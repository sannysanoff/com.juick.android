package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import com.google.gson.Gson;
import com.juickadvanced.R;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

/**
 */
public class XMPPPreference extends Preference {

    String currentValueJSON = "";

    public XMPPPreference(Context context) {
        super(context);
    }

    public XMPPPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public XMPPPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setWidgetLayoutResource(R.layout.xmpp_preference);
    }

    public static class Value {
        String login;
        String password;
        String service;
        String server;
        int port;
        String resource;
        int priority;
        boolean secure;

        public Value(String login, String password, int port, int priority, String resource, String server, String service, boolean secure) {
            this.login = login;
            this.password = password;
            this.port = port;
            this.priority = priority;
            this.resource = resource;
            this.server = server;
            this.service = service;
            this.secure = secure;
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Button btn = (Button) view.findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            Gson gson = new Gson();
            Value value = gson.fromJson((String) currentValueJSON, Value.class);

            @Override
            public void onClick(View v) {
                LayoutInflater mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View view = mInflater.inflate(R.layout.xmpp_setup, null);
                view.findViewById(R.id.test_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
                final Spinner templates = (Spinner) view.findViewById(R.id.template_selection);
                final TextView login = (TextView) view.findViewById(R.id.login);
                final TextView password = (TextView) view.findViewById(R.id.password);
                final TextView port = (TextView) view.findViewById(R.id.port);
                final TextView service = (TextView) view.findViewById(R.id.service);
                final TextView server = (TextView) view.findViewById(R.id.server);
                final CheckBox secure = (CheckBox) view.findViewById(R.id.secure);
                final TextView priority = (TextView) view.findViewById(R.id.priority);
                final TextView resource = (TextView) view.findViewById(R.id.resource);
                final Button btn = (Button) view.findViewById(R.id.test_button);
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
                                login.setText("xxxxxxx");
                                service.setText("");
                                server.setText("jabber.ru");
                                port.setText("5222");
                                login.requestFocus();
                                break;
                            case 2:
                                login.setText("xxxxxxx@gmail.com");
                                service.setText("gmail.com");
                                server.setText("talk.google.com");
                                port.setText("5222");
                                secure.setChecked(true);
                                login.requestFocus();
                        }
                    }
                };
                templates.setOnItemSelectedListener(templatesUpdater);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final ProgressDialog pd = new ProgressDialog(getContext());
                        pd.setMessage("Connecting to server..");
                        pd.setIndeterminate(true);
                        pd.show();
                        pd.setCancelable(true);
                        String svc = service.getText().toString();
                        String svr = server.getText().toString();
                        final ConnectionConfiguration configuration = new ConnectionConfiguration(svr, atoi(port.getText().toString()), Utils.nvl(svc, svr).trim());
                        configuration.setSecurityMode(secure.isChecked() ? ConnectionConfiguration.SecurityMode.required : ConnectionConfiguration.SecurityMode.enabled);
                        SASLAuthentication.supportSASLMechanism("PLAIN", 0);
                        final XMPPConnection connection = new XMPPConnection(configuration);
                        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                pd.hide();
                            }
                        });
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    connection.connect();
                                    ((Activity) getContext()).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            pd.setMessage("Logging in...");
                                        }
                                    });
                                    connection.login(login.getText().toString(), password.getText().toString());
                                    ((Activity) getContext()).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            pd.hide();
                                            Toast.makeText(getContext(), "SUCCESS !", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                } catch (final XMPPException e) {
                                    ((Activity) getContext()).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (pd.isShowing()) {
                                                Toast.makeText(getContext(), e.toString(), Toast.LENGTH_LONG).show();
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
                if (value == null) value = new Value("", "", 5222, 55, "com.juickadvanced", "jabber.ru", "", false);
                login.setText(value.login);
                password.setText(value.password);
                port.setText("" + value.port);
                resource.setText(value.resource);
                service.setText(value.service);
                server.setText(value.server);
                priority.setText("" + value.priority);
                secure.setChecked(value.secure);
                new AlertDialog.Builder(getContext())
                        .setView(view)
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                value.login = login.getText().toString();
                                value.password = password.getText().toString();
                                value.port = atoi(port.getText().toString());
                                value.resource = resource.getText().toString();
                                value.service = service.getText().toString();
                                value.server = server.getText().toString();
                                value.priority = atoi(priority.getText().toString());
                                value.secure = secure.isChecked();
                                currentValueJSON = gson.toJson(value);
                                onSetInitialValue(false, currentValueJSON);
                                dialog.cancel();
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                }).show();
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

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        Gson gson = new Gson();
        if (restorePersistedValue) {
            currentValueJSON = getPersistedString(currentValueJSON);
        } else {
            Value value = gson.fromJson((String) defaultValue, Value.class);
            currentValueJSON = gson.toJson(value);
            persistString(currentValueJSON);
        }
        Value value = gson.fromJson(currentValueJSON, Value.class);
        if (value == null || value.login == null || value.login.length() == 0 || value.password == null || value.password.length() == 0 || value.server == null || value.server.length() == 0) {
            setSummary("Not set up");
        } else {
            setSummary(value.login +", "+value.server);
        }
    }


}
