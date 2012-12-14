/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 *
 */
public class XMPPMessageReceiver extends BroadcastReceiver {

    interface MessageReceiverListener {
        boolean onMessageReceived(XMPPService.IncomingMessage message);
    }

    public static ArrayList<MessageReceiverListener> listeners = new ArrayList<MessageReceiverListener>();


    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(XMPPService.ACTION_MESSAGE_RECEIVED)) {
            int nMessages = intent.getIntExtra("messagesCount", 0);
            boolean sound = intent.getBooleanExtra("sound", true);
            XMPPService.IncomingMessage messag = (XMPPService.IncomingMessage)intent.getSerializableExtra("message");
            if (nMessages == 0) return;
            ArrayList<MessageReceiverListener> allListeners = (ArrayList<MessageReceiverListener>) listeners.clone();
            boolean handled = false;
            for (MessageReceiverListener listener : allListeners) {
                handled |= listener.onMessageReceived(messag);
            }
            if (!handled) {
                updateInfo(context, nMessages, !sound);
            }
        }
        if (intent.getAction().equals(XMPPService.ACTION_LAUNCH_MESSAGELIST)) {
            Intent nintent = new Intent(context, XMPPIncomingMessagesActivity.class);
            nintent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            nintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(nintent);
        }
    }

    static long lastVibrate = 0;

    public static void updateInfo(Context context, int nMessages, boolean silent) {
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String tickerText = "juick: new message";
        Notification notif = new Notification(R.drawable.juick_message_icon, null,System.currentTimeMillis());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int notification = 0;
        if (!silent) {
            if (prefs.getBoolean("led_enabled", true)) notification |= Notification.DEFAULT_LIGHTS;
            if (System.currentTimeMillis() - lastVibrate > 5000) {
                // add some sound
                if (prefs.getBoolean("vibration_enabled", true)) notification |= Notification.DEFAULT_VIBRATE;
                if (prefs.getBoolean("ringtone_enabled", true)) {
                    String ringtone_uri = prefs.getString("ringtone_uri", "");
                    if (ringtone_uri.length() > 0) {
                        notif.sound = Uri.parse(ringtone_uri);
                    }
                    else
                        notification |= Notification.DEFAULT_SOUND;
                }
                lastVibrate = System.currentTimeMillis();
            }
        }
        notif.defaults = silent ? 0 : notification;
        Intent intent = new Intent();
        intent.setAction(XMPPService.ACTION_LAUNCH_MESSAGELIST);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1000, intent, 0);
        //PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, nintent, 0);
        notif.setLatestEventInfo(context, "Juick: " + nMessages + " new message" + (nMessages > 1 ? "s" : ""), tickerText, pendingIntent);
        nm.notify("", 2, notif);
    }

    public static void cancelInfo(Context context) {
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel("", 2);
    }
}
