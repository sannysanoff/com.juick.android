package com.juick.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.juickadvanced.R;
import org.jivesoftware.smack.XMPPConnection;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/11/12
 * Time: 1:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMPPControlActivity extends Activity {

    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xmpp_control);
        final TextView xmppStatus = (TextView) findViewById(R.id.xmpp_status);
        final TextView lastGCM = (TextView) findViewById(R.id.last_gcm);
        final TextView ngcm = (TextView) findViewById(R.id.ngcm);
        final TextView lastException = (TextView) findViewById(R.id.last_exception);
        final TextView messagesReceived = (TextView) findViewById(R.id.messages_received);
        final TextView alarmScheduled = (TextView) findViewById(R.id.alarm_scheduled);
        final TextView alarmFired = (TextView) findViewById(R.id.alarm_fired);
        final TextView lastGCMId = (TextView) findViewById(R.id.last_gcm_id);
        final TextView juickbot = (TextView) findViewById(R.id.xmpp_juickbot);
        final TextView jubobot = (TextView) findViewById(R.id.xmpp_jubobot);
        final TextView juickBlacklist = (TextView) findViewById(R.id.xmpp_juickblacklist);
        final TextView juboBlacklist = (TextView) findViewById(R.id.xmpp_juboblacklist);
        final TextView lastConnect = (TextView) findViewById(R.id.xmpp_last_connect);
        final TextView memoryTotal = (TextView) findViewById(R.id.memory_total);
        final TextView memoryUsed = (TextView) findViewById(R.id.memory_used);
        final TextView webviewCount = (TextView) findViewById(R.id.webview_count);
        final TextView jmaCount = (TextView) findViewById(R.id.jma_count);
        final TextView infoDate = (TextView) findViewById(R.id.info_date);
        final Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        final Button retry = (Button) findViewById(R.id.retry);
        final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ssZ");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean xmppExternal = sp.getBoolean("xmpp_external", false);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        service.cleanup("Manual reconnect");
                        service.startup();
                    }
                });
            }
        });
        final Button gc = (Button) findViewById(R.id.gc);
        gc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        Runtime.getRuntime().gc();
                    }
                });
            }
        });
        handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Runnable thiz = this;
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        String status = "";
                        synchronized (service.connections) {
                            if (service.connections.size() == 0) {
                                if (xmppExternal) {
                                    status = "external (ja) XMPP";
                                } else {
                                    status = "idle.";
                                }
                            } else {
                                XMPPConnection conn = service.connections.get(service.connections.size() - 1);
                                if (conn.isConnected()) {
                                    if (conn.isAuthenticated()) {
                                        status = "CONNECTED, AUTH-ed.";
                                    } else {
                                        status = "CONNECTED, no auth";
                                    }
                                } else {
                                    status = "connecting...";
                                }
                            }
                        }
                        xmppStatus.setText(status);
                        lastException.setText(service.lastException != null ? service.lastException.toString() : " --- ");
                        messagesReceived.setText(""+service.messagesReceived);
                        juickbot.setText(service.botOnline ? "ONLINE" : "offline");
                        jubobot.setText(service.juboOnline ? "ONLINE" : "offline");
                        if (XMPPService.juickBlacklist != null) {
                            juickBlacklist.setText(XMPPService.juickBlacklist.info()+"; got from bot");
                        } else
                            if (XMPPService.juickBlacklist_tmp != null) {
                                juickBlacklist.setText(XMPPService.juickBlacklist_tmp.info()+"; from cache");
                            }
                        if (XMPPService.juboMessageFilter != null) {
                            juboBlacklist.setText(XMPPService.juboMessageFilter.info()+"; got from bot");
                        }
                        if (XMPPService.juboMessageFilter_tmp != null) {
                            juboBlacklist.setText(XMPPService.juboMessageFilter_tmp.info()+"; from cache");
                        }
                        lastConnect.setText(XMPPService.lastSuccessfulConnect == 0 ? "never" : sdf.format(new Date(XMPPService.lastSuccessfulConnect)));
                        memoryTotal.setText(""+(Runtime.getRuntime().totalMemory()/1024)+" KB");
                        memoryUsed.setText(""+((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()) /1024)+" KB");
                        webviewCount.setText("" + MyWebView.instanceCount + " instances");
                        jmaCount.setText("" + JuickMessagesAdapter.instanceCount + " instances");
                        infoDate.setText("" + sdf.format(new Date()));
                        lastGCM.setText("" + (XMPPService.lastGCMMessage != null ? sdf.format(XMPPService.lastGCMMessage): " --- "));
                        ngcm.setText("" + service.nGCMMessages);
                        alarmScheduled.setText(XMPPService.lastAlarmScheduled != 0 ? "" + sdf.format(new Date(XMPPService.lastAlarmScheduled)): " --- ");
                        alarmFired.setText(XMPPService.lastAlarmFired != 0 ? "" + sdf.format(new Date(XMPPService.lastAlarmFired)): " --- ");
                        lastGCMId.setText(XMPPService.lastGCMMessageID);
                        handler.postDelayed(thiz, 2000);
                    }
                });


            }
        }, 20);
    }
}
