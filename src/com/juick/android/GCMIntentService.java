package com.juick.android;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.*;

public class GCMIntentService extends com.google.android.gcm.GCMBaseIntentService {

    public interface GCMMessageListener {
        public void onGCMMessage(String message, Set<String> categories);
        public void onRegistration(String regid);
        public void onUnregistration(String registrationId);
    }

    static ArrayList<GCMMessageListener> listeners = new ArrayList<GCMMessageListener>();

    public static ArrayList<ServerPingTimerListener> serverPingTimerListeners = new ArrayList<ServerPingTimerListener>();
    public interface ServerPingTimerListener {
        public void onKeepAlive();
    }


    public static final String SENDER_ID = "61277897514";
    Handler handler;

    public GCMIntentService() {
        super(SENDER_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getCategories() != null && intent.getCategories().contains(CATEGORY_HARDWARE_ALARM)) {
            XMPPService.lastAlarmFired = System.currentTimeMillis();
            keepAlive();
            rescheduleAlarm(this, ConnectivityChangeReceiver.getMaximumSleepInterval(getApplicationContext())*60);
            return 0;
        }
        if (intent != null && intent.getAction() == null) return START_NOT_STICKY;
        return super.onStartCommand(intent, flags, startId);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public static void keepAlive() {
        for (ServerPingTimerListener serverPingTimerListener : new ArrayList<ServerPingTimerListener>(serverPingTimerListeners)) {
            serverPingTimerListener.onKeepAlive();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
    }

    @Override
    protected void onError(final Context context, final String errorId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JuickAdvancedApplication.showXMPPToast(errorId);
            }
        });
    }

    @Override
    protected void onMessage(final Context context, final Intent intent) {
        final Object msg = intent.getExtras().get("message");
        final Set<String> categories = intent.getCategories();
        if (msg != null && msg instanceof String) {
            final String messag = (String)msg;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    for (GCMMessageListener listener : (List<GCMMessageListener>)listeners.clone()) {
                        listener.onGCMMessage(messag, categories);
                    }
                }
            });
        }
    }

    @Override
    protected void onRegistered(Context context, final String registrationId) {
        Log.i("JuickAdvanced", "GCM registered");
        JuickAdvancedApplication.registrationId = registrationId;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                for (GCMMessageListener listener : (List<GCMMessageListener>)listeners.clone()) {
                    listener.onRegistration(registrationId);
                }
            }
        }, 10000);

    }

    @Override
    protected void onUnregistered(Context context, final String registrationId) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                for (GCMMessageListener listener : (List<GCMMessageListener>)listeners.clone()) {
                    listener.onUnregistration(registrationId);
                }
            }
        }, 10000);
        JuickAdvancedApplication.registrationId = null;
    }

    private static final String CATEGORY_HARDWARE_ALARM = "HARDWARE_ALARM";

    public static void rescheduleAlarm(Context ctx, int seconds) {
        Intent intent = new Intent(ctx, com.juickadvanced.GCMIntentService.class);
        intent.addCategory(CATEGORY_HARDWARE_ALARM);
        PendingIntent scheduledAlarm = PendingIntent.getService(ctx, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.add(Calendar.SECOND, seconds);
        alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), scheduledAlarm);
        XMPPService.lastAlarmScheduled = calendar.getTimeInMillis();
    }


}
