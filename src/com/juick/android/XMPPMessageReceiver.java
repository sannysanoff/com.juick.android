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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.juick.R;

import java.util.ArrayList;

/**
 *
 */
public class XMPPMessageReceiver extends BroadcastReceiver {

    interface MessageReceiverListener {
        void onMessageReceived();
    }

    public static ArrayList<MessageReceiverListener> listeners = new ArrayList<MessageReceiverListener>();


    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(XMPPService.ACTION_MESSAGE_RECEIVED)) {
            int nMessages = intent.getIntExtra("messagesCount", 0);
            if (nMessages == 0) return;
            ArrayList<MessageReceiverListener> allListeners = (ArrayList<MessageReceiverListener>) listeners.clone();
            for (MessageReceiverListener listener : allListeners) {
                listener.onMessageReceived();
            }
            if (allListeners.size() == 0) {
                NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                String tickerText = "juick: new message";
                Notification notif = new Notification(R.drawable.juick_message_icon, null,System.currentTimeMillis());
                notif.defaults = Notification.DEFAULT_ALL;
                Intent nintent = new Intent(context, XMPPIncomingMessagesActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, nintent, 0);
                notif.setLatestEventInfo(context, "Juick: "+nMessages+" new message"+ (nMessages > 1 ? "s":""), tickerText, pendingIntent);
                nm.notify("", 2, notif);
            }
        }
    }

    public static void cancelInfo(Context context) {
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel("", 2);
    }
}
