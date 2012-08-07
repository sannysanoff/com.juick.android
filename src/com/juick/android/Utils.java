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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import com.juick.R;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Ugnich Anton
 */
public class Utils {

    public static void updateTheme(Activity activity) {
        /*
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (sp.getString("theme", "0").equals("0")) {
        activity.setTheme(android.R.style.Theme_Light);
        }
         */
    }

    public static void updateThemeHolo(Activity activity) {
        /*
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (sp.getString("theme", "0").equals("0")) {
        activity.setTheme(R.style.Theme_Sherlock_Light);
        }
         */
    }

    public static int doHttpGetRequest(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();
            conn.setUseCaches(false);
            conn.connect();
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;
        } catch (Exception e) {
            Log.e("doHttpGetRequest", e.toString());
        }
        return 0;
    }

    public static boolean hasAuth(Context context) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        return accs.length > 0;
    }

    public static String getAuthHash(Context context) {
        String jsonStr = getJSON(context, "http://api.juick.com/auth");
        if (jsonStr != null && !jsonStr.equals("")) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("hash")) {
                    return json.getString("hash");
                }
            } catch (JSONException e) {
            }
        }
        return null;
    }

    public static String getBasicAuthString(Context context) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        if (accs.length > 0) {
            Bundle b = null;
            try {
                b = am.getAuthToken(accs[0], "", null, null, null, null).getResult();
            } catch (Exception e) {
                Log.e("Juick", e.toString());
            }
            if (b != null) {
                String authStr = b.getString(AccountManager.KEY_ACCOUNT_NAME) + ":" + b.getString(AccountManager.KEY_AUTHTOKEN);
                return "Basic " + Base64.encodeToString(authStr.getBytes(), Base64.NO_WRAP);
            }
        }
        return "";
    }

    public static String getJSON(Context context, String url) {
        String ret = null;
        try {
            URL jsonURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) jsonURL.openConnection();

            String basicAuth = getBasicAuthString(context);
            if (basicAuth.length() > 0) {
                conn.setRequestProperty("Authorization", basicAuth);
            }

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                ret = streamToString(conn.getInputStream());
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e("getJSON", e.toString());
        }
        return ret;
    }

    public static String postJSON(Context context, String url, String data) {
        String ret = null;
        try {
            URL jsonURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) jsonURL.openConnection();

            String basicAuth = getBasicAuthString(context);
            if (basicAuth.length() > 0) {
                conn.setRequestProperty("Authorization", basicAuth);
            }

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.connect();

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.close();

            if (conn.getResponseCode() == 200) {
                ret = streamToString(conn.getInputStream());
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e("getJSON", e.toString());
        }
        return ret;
    }

    public static String streamToString(InputStream is) {
        try {
            BufferedReader buf = new BufferedReader(new InputStreamReader(is));
            StringBuilder str = new StringBuilder();
            String line;
            do {
                line = buf.readLine();
                str.append(line + "\n");
            } while (line != null);
            return str.toString();
        } catch (Exception e) {
            Log.e("streamReader", e.toString());
        }
        return null;
    }

    public static Bitmap downloadImage(String url) {
        try {
            URL imgURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) imgURL.openConnection();
            conn.setDoInput(true);
            conn.connect();
            return BitmapFactory.decodeStream(conn.getInputStream());
        } catch (Exception e) {
            Log.e("downloadImage", e.toString());
        }
        return null;
    }

    public static Set<String> string2set(String str) {
        return new HashSet<String>(Arrays.asList(str.split("@")));
    }
    public static String set2string(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() != 0)
                sb.append("@");
            sb.append(s);
        }
        return sb.toString();
    }
}
