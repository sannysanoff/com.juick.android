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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {

    static boolean connectivity = true;

    static String currentConnectivityTypeKey = null;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String ky = getCurrentConnectivityTypeKey(context);
        JuickAdvancedApplication.addToGlobalLog("ConnChange: "+ky, null);
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
                XMPPService.log("Connectivity: "+getCurrentConnectivityTypeKey(context));
                context.startService(new Intent(context, XMPPService.class));
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                MainActivity.toggleJAMessaging(context, sp.getBoolean("enableJAMessaging",false));
            } else {
                XMPPService.log("No connectivity.");
                Intent service = new Intent(context, XMPPService.class);
                service.putExtra("terminate", true);
                service.putExtra("terminateMessage", "connectivity lost");
                context.startService(service);
                MainActivity.toggleJAMessaging(context, false);
            }
        }
    }

    public static String getCurrentConnectivityTypeKey(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active = connManager.getActiveNetworkInfo();
        if (active != null) {
            currentConnectivityTypeKey = "UNKNOWN";
            // ?? maybe
            if (active.getType() == ConnectivityManager.TYPE_MOBILE_DUN || active.getType() == ConnectivityManager.TYPE_MOBILE_HIPRI || active.getType() == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager manager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
                String carrierName = manager.getSimOperatorName();
                currentConnectivityTypeKey = "MOB/"+carrierName;
            } else {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    if (info != null) {
                        String ssid = info.getSSID();
                        currentConnectivityTypeKey = "WIFI/"+ssid;
                    }
                }
            }
        } else {
            currentConnectivityTypeKey = null;
        }
        return currentConnectivityTypeKey;
    }

    static HashMap<String, Long> bestPeriods = null;

    public synchronized static void recordSuccessfulIdlePeriod(Context context, long period) {
        if (currentConnectivityTypeKey == null)
            getCurrentConnectivityTypeKey(context);
        if (currentConnectivityTypeKey != null) {
            getBestPeriods(context);
            Long oldPeriod = bestPeriods.get(currentConnectivityTypeKey);
            if (oldPeriod == null || oldPeriod < period) {
                bestPeriods.put(currentConnectivityTypeKey, period);
                String s = new Gson().toJson(bestPeriods);
                context.getSharedPreferences("network_socket_alive_periods", Context.MODE_PRIVATE).edit().putString("stats", s).commit();
            }
        }
    }

    public synchronized static HashMap<String, Long> getBestPeriods(Context context) {
        if (bestPeriods == null) {
            String savedStats = context.getSharedPreferences("network_socket_alive_periods", Context.MODE_PRIVATE).getString("stats", null);
            if (savedStats != null) {
                bestPeriods = new Gson().fromJson(savedStats, HashMap.class);
                Set entries = bestPeriods.entrySet();
                for (Object oentry : entries) {
                    Map.Entry entry = (Map.Entry)oentry;
                    if (entry.getValue() instanceof Double) {
                        bestPeriods.put((String)entry.getKey(), new Long(((Double)entry.getValue()).longValue()));
                    }
                }
            }
            if (bestPeriods == null)
                bestPeriods = new HashMap<String, Long>();
        }
        return bestPeriods;
    }

    public synchronized static void resetStatistics(Context context) {
        bestPeriods = new HashMap<String, Long>();
        context.getSharedPreferences("network_socket_alive_periods", Context.MODE_PRIVATE).edit().remove("stats").commit();
    }

    // this stuff will potentially grow towards maximum sleep time set to the period network keeps socket connection in NAT alive.
    // So, for busy NATs this will be reduced.
    public static void adjustMaximumSleepInterval(Context context, int adjustSleepInterval) {
        if (adjustSleepInterval < 3) {  // nonono, double check
            return;
        }
        context.getSharedPreferences("network_socket_alive_periods", Context.MODE_PRIVATE).edit().putInt("sleep_interval", adjustSleepInterval).commit();
    }

    public static int getMaximumSleepInterval(Context context) {
        try {
            return context.getSharedPreferences("network_socket_alive_periods", Context.MODE_PRIVATE).getInt("sleep_interval", 15);
        } catch (Exception e) {
            return 15;  // just in case.
        }
    }
}
