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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.v4.app.NotificationCompat;
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
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            context.startService(new Intent(context, XMPPService.class));
        }
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

    public static final int[] NOTIFICATION_ICONS = {
            R.drawable.juick_message_icon,
            R.drawable.juick_message_icon_001,
            R.drawable.juick_message_icon_002,
            R.drawable.juick_message_icon_003,
            R.drawable.juick_message_icon_004,
            R.drawable.juick_message_icon_005,
            R.drawable.juick_message_icon_006,
            R.drawable.juick_message_icon_007,
            R.drawable.juick_message_icon_008,
            R.drawable.juick_message_icon_009,
            R.drawable.juick_message_icon_010,
            R.drawable.juick_message_icon_011,
            R.drawable.juick_message_icon_012,
            R.drawable.juick_message_icon_013,
            R.drawable.juick_message_icon_014,
            R.drawable.juick_message_icon_015,
            R.drawable.juick_message_icon_016,
            R.drawable.juick_message_icon_017,
            R.drawable.juick_message_icon_018,
            R.drawable.juick_message_icon_019,
            R.drawable.juick_message_icon_020,
            R.drawable.juick_message_icon_021,
            R.drawable.juick_message_icon_022,
            R.drawable.juick_message_icon_023,
            R.drawable.juick_message_icon_024,
            R.drawable.juick_message_icon_025,
            R.drawable.juick_message_icon_026,
            R.drawable.juick_message_icon_027,
            R.drawable.juick_message_icon_028,
            R.drawable.juick_message_icon_029,
            R.drawable.juick_message_icon_030,
            R.drawable.juick_message_icon_031,
            R.drawable.juick_message_icon_032,
            R.drawable.juick_message_icon_033,
            R.drawable.juick_message_icon_034,
            R.drawable.juick_message_icon_035,
            R.drawable.juick_message_icon_036,
            R.drawable.juick_message_icon_037,
            R.drawable.juick_message_icon_038,
            R.drawable.juick_message_icon_039,
            R.drawable.juick_message_icon_040,
            R.drawable.juick_message_icon_041,
            R.drawable.juick_message_icon_042,
            R.drawable.juick_message_icon_043,
            R.drawable.juick_message_icon_044,
            R.drawable.juick_message_icon_045,
            R.drawable.juick_message_icon_046,
            R.drawable.juick_message_icon_047,
            R.drawable.juick_message_icon_048,
            R.drawable.juick_message_icon_049,
            R.drawable.juick_message_icon_050,
            R.drawable.juick_message_icon_051,
            R.drawable.juick_message_icon_052,
            R.drawable.juick_message_icon_053,
            R.drawable.juick_message_icon_054,
            R.drawable.juick_message_icon_055,
            R.drawable.juick_message_icon_056,
            R.drawable.juick_message_icon_057,
            R.drawable.juick_message_icon_058,
            R.drawable.juick_message_icon_059,
            R.drawable.juick_message_icon_060,
            R.drawable.juick_message_icon_061,
            R.drawable.juick_message_icon_062,
            R.drawable.juick_message_icon_063,
            R.drawable.juick_message_icon_064,
            R.drawable.juick_message_icon_065,
            R.drawable.juick_message_icon_066,
            R.drawable.juick_message_icon_067,
            R.drawable.juick_message_icon_068,
            R.drawable.juick_message_icon_069,
            R.drawable.juick_message_icon_070,
            R.drawable.juick_message_icon_071,
            R.drawable.juick_message_icon_072,
            R.drawable.juick_message_icon_073,
            R.drawable.juick_message_icon_074,
            R.drawable.juick_message_icon_075,
            R.drawable.juick_message_icon_076,
            R.drawable.juick_message_icon_077,
            R.drawable.juick_message_icon_078,
            R.drawable.juick_message_icon_079,
            R.drawable.juick_message_icon_080,
            R.drawable.juick_message_icon_081,
            R.drawable.juick_message_icon_082,
            R.drawable.juick_message_icon_083,
            R.drawable.juick_message_icon_084,
            R.drawable.juick_message_icon_085,
            R.drawable.juick_message_icon_086,
            R.drawable.juick_message_icon_087,
            R.drawable.juick_message_icon_088,
            R.drawable.juick_message_icon_089,
            R.drawable.juick_message_icon_090,
            R.drawable.juick_message_icon_091,
            R.drawable.juick_message_icon_092,
            R.drawable.juick_message_icon_093,
            R.drawable.juick_message_icon_094,
            R.drawable.juick_message_icon_095,
            R.drawable.juick_message_icon_096,
            R.drawable.juick_message_icon_097,
            R.drawable.juick_message_icon_098,
            R.drawable.juick_message_icon_099
    };


    public static void updateInfo(Context context, int nMessages, boolean silent) {
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String tickerText = "juick: new message";

        // public Notification(int icon, java.lang.CharSequence tickerText, long when) { /* compiled code */ }
        // Notification notif = new Notification(R.drawable.juick_message_icon, null,System.currentTimeMillis());

        int iconIndex;
        if (nMessages < 1) {
            iconIndex = 0; // to prevent out of bounds
        } else if (nMessages > NOTIFICATION_ICONS.length - 1) {
            iconIndex = NOTIFICATION_ICONS.length - 1; // to prevent out of bounds
        } else {
            iconIndex = nMessages;
        }

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(NOTIFICATION_ICONS[iconIndex])
                        .setWhen(System.currentTimeMillis());

        //context.getResources().getDrawable(smallIcon)
        // public Notification(int icon, java.lang.CharSequence tickerText, long when) { /* compiled code */ }

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
                        notificationBuilder.setSound(Uri.parse(ringtone_uri));
                    }
                    else
                        notification |= Notification.DEFAULT_SOUND;
                }
                lastVibrate = System.currentTimeMillis();
            }
        }
        notificationBuilder.setDefaults(silent ? 0 : notification);
        Intent intent = new Intent();
        intent.setAction(XMPPService.ACTION_LAUNCH_MESSAGELIST);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1000, intent, 0);
        //PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, nintent, 0);
        notificationBuilder.setContentTitle("Juick: " + nMessages + " new message" + (nMessages > 1 ? "s" : "")).
                setContentText(tickerText).
                setContentIntent(pendingIntent).
                setNumber(nMessages);
        nm.notify("", 2, notificationBuilder.getNotification());
    }

    public static void cancelInfo(Context context) {
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel("", 2);
    }
}
