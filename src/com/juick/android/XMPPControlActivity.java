package com.juick.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.juickadvanced.R;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: san
 * Date: 8/11/12
 */
public class XMPPControlActivity extends Activity {

    Handler handler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        JuickAdvancedApplication.maybeEnableAcceleration(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xmpp_control);
        final TextView xmppStatus = (TextView) findViewById(R.id.xmpp_status);
        final TextView logView = (TextView) findViewById(R.id.logview);
        final View logViewScroll = findViewById(R.id.logview_scroll);
        final TextView jamStatus = (TextView) findViewById(R.id.jam_status);
        final TextView lastGCM = (TextView) findViewById(R.id.last_gcm);
        final TextView lastGCMId = (TextView) findViewById(R.id.last_gcm_id);
        final TextView lastWS = (TextView) findViewById(R.id.last_ws);
        final TextView lastWSId = (TextView) findViewById(R.id.last_ws_id);
        final TextView lastException = (TextView) findViewById(R.id.last_exception);
        final TextView exceptionTime = (TextView) findViewById(R.id.exception_time);
        final TextView alarmScheduled = (TextView) findViewById(R.id.alarm_scheduled);
        final TextView juickGCMStatus = (TextView) findViewById(R.id.juick_gcm_status);
        final TextView juickGCMReceived = (TextView) findViewById(R.id.juick_gcm_received);
        final TextView alarmFired = (TextView) findViewById(R.id.alarm_fired);
        final TextView juickbot = (TextView) findViewById(R.id.xmpp_juickbot);
        final TextView jubobot = (TextView) findViewById(R.id.xmpp_jubobot);
        final TextView juickBlacklist = (TextView) findViewById(R.id.xmpp_juickblacklist);
        final TextView juboBlacklist = (TextView) findViewById(R.id.xmpp_juboblacklist);
        final TextView lastConnect = (TextView) findViewById(R.id.jam_last_connect);
        final TextView memoryTotal = (TextView) findViewById(R.id.memory_total);
        final TextView memoryUsed = (TextView) findViewById(R.id.memory_used);
        final TextView instCount = (TextView) findViewById(R.id.inst_count);
        final TextView hardwareAcceleration = (TextView) findViewById(R.id.hardware_acceleration);
        final TextView infoDate = (TextView) findViewById(R.id.info_date);
        final Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        final Utils.ServiceGetter<JAMService> jamServiceServiceGetter = new Utils.ServiceGetter<JAMService>(this, JAMService.class);
        final Button retry = (Button) findViewById(R.id.retry);
        final Button showMessages = (Button) findViewById(R.id.show_messages);
        final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        logViewScroll.setVisibility(View.GONE);
        findViewById(R.id.show_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder logs = new StringBuilder();
                for (String s : XMPPService.log) {
                    logs.insert(0, "\n");
                    logs.insert(0, s);
                }
                logView.setText(logs);
                logViewScroll.setVisibility(View.VISIBLE);
            }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean("useXMPP", false).commit();
                GCMIntentService.keepAlive();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                sp.edit().putBoolean("useXMPP", true).commit();
                            }
                        });
                    }
                }.start();
            }
        });
        showMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent nintent = new Intent(XMPPControlActivity.this, XMPPIncomingMessagesActivity.class);
                startActivity(nintent);
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
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
        handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Runnable thiz = this;
                boolean jamRunning = MainActivity.isJAMServiceRunning(XMPPControlActivity.this);
                JAMService jamService = JAMService.instance;
                if (jamRunning && jamService != null) {
                    StringBuilder status = new StringBuilder("svc UP, ");
                    if (jamService.client != null) {
                        if (jamService.client.wsClient != null) {
                            status.append("websock UP");
                        } else {
                            status.append("client UP, ws DOWN");
                        }
                    } else {
                        status.append("client DOWN");
                    }
                    jamStatus.setText(status.toString());
                } else {
                    jamStatus.setText("not running");
                }

                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        String status;
                        if (service.currentThread == null) {
                            status = "not running";
                        } else {
                            XMPPService.ExternalXMPPThread t = (XMPPService.ExternalXMPPThread) service.currentThread;
                            status = "JA extXMPP: Ws=" + (t.client != null && t.client.wsClient != null ? "OK" : "Down");

                        }
                        xmppStatus.setText(status);

                        lastException.setText(XMPPService.lastException != null ? XMPPService.lastException : " --- ");
                        juickbot.setText(service.botOnline ? "ONLINE" : "offline");
                        jubobot.setText(service.juboOnline ? "ONLINE" : "offline");
                        if (XMPPService.juickBlacklist != null) {
                            juickBlacklist.setText(XMPPService.juickBlacklist.info() + "; got from bot");
                        } else if (XMPPService.juickBlacklist_tmp != null) {
                            juickBlacklist.setText(XMPPService.juickBlacklist_tmp.info() + "; from cache");
                        }
                        if (XMPPService.juboMessageFilter != null) {
                            juboBlacklist.setText(XMPPService.juboMessageFilter.info() + "; got from bot");
                        }
                        if (XMPPService.juboMessageFilter_tmp != null) {
                            juboBlacklist.setText(XMPPService.juboMessageFilter_tmp.info() + "; from cache");
                        }
                        lastConnect.setText(XMPPService.lastSuccessfulConnect == 0 ? "never" : sdf.format(new Date(XMPPService.lastSuccessfulConnect)));
                        memoryTotal.setText(getTotalMemoryString());
                        memoryUsed.setText(getUsedMemoryString());
                        instCount.setText(getInstancesString());
                        infoDate.setText("" + sdf.format(new Date()));
                        lastGCM.setText("" + (XMPPService.lastGCMMessage != null ? sdf.format(XMPPService.lastGCMMessage) : " --- "));
                        lastWS.setText("" + (XMPPService.lastWSMessage != null ? sdf.format(XMPPService.lastWSMessage) : " --- "));
                        alarmScheduled.setText(XMPPService.lastAlarmScheduled != 0 ? "" + sdf.format(new Date(XMPPService.lastAlarmScheduled)) : " --- ");
                        alarmFired.setText(XMPPService.lastAlarmFired != 0 ? "" + sdf.format(new Date(XMPPService.lastAlarmFired)) : " --- ");
                        exceptionTime.setText(XMPPService.lastExceptionTime != 0 ? "" + sdf.format(new Date(XMPPService.lastExceptionTime)) : " --- ");
                        lastGCMId.setText(XMPPService.lastGCMMessageID + " count=" + XMPPService.nGCMMessages);
                        lastWSId.setText(XMPPService.lastWSMessageID + " count=" + XMPPService.nWSMessages);
                        juickGCMStatus.setText(XMPPService.juickGCMStatus);
                        juickGCMReceived.setText("cnt="+XMPPService.juickGCMReceived+" last="+(XMPPService.juickGCMKLasReceived != null ? sdf.format(XMPPService.juickGCMKLasReceived) : "never"));
                        try {
                            hardwareAcceleration.setText(""+getWindow().getDecorView().isHardwareAccelerated());
                        } catch (Throwable th) {
                            hardwareAcceleration.setText("not supported in API");
                        }
                        handler.postDelayed(thiz, 2000);
                    }

                    @Override
                    public void withoutService() {
                        super.withoutService();
                    }
                });


            }
        }, 20);
    }

    private static String getUsedMemoryString() {
        return "" + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024) + " KB";
    }

    private static String getTotalMemoryString() {
        return "" + (Runtime.getRuntime().totalMemory() / 1024) + " KB";
    }

    private static String getInstancesString() {
        return "PLL:"+ PressableLinearLayout.instanceCount+" MIV:"+ MyImageView.instanceCount+" JMA:" + JuickMessagesAdapter.instanceCount + " BC:"+ BitmapCounts.counts.size()+" TF:"+ ThreadFragment.instanceCount+" TA:"+ThreadFragment.instanceCount+" WS:"+ WsClient.instanceCount;
    }

    public static String getMemoryStatusString() {
        return "Used: "+getUsedMemoryString()+" Total: "+getTotalMemoryString()+" Instances: "+getInstancesString();
    }

}
