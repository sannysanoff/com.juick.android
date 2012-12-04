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
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.juickadvanced.R;

import java.util.ArrayList;
import java.util.Set;

/**
 *
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {

    static boolean connectivity = true;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        boolean newConnectivity = connectivity;
        Bundle extras = intent.getExtras();
        if (extras.getBoolean(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
            newConnectivity = false;
        } else {
            NetworkInfo ni = (NetworkInfo) extras.get(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (ni != null && ni.isAvailable()) {
                newConnectivity = true;
            }
        }
        if (newConnectivity != connectivity) {
            connectivity = newConnectivity;
            if (connectivity) {
                context.startService(new Intent(context, XMPPService.class));
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                MainActivity.toggleJAMessaging(context, sp.getBoolean("enableJAMessaging",false));
            } else {
                Intent service = new Intent(context, XMPPService.class);
                service.putExtra("terminate", true);
                service.putExtra("terminateMessage", "connectivity lost");
                context.startService(service);
                MainActivity.toggleJAMessaging(context, false);
            }
        }
    }
}
