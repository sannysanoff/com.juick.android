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

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.*;
import android.support.v4.view.*;
import android.text.Html;
import android.view.MenuInflater;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import com.juick.R;
import de.quist.app.errorreporter.ExceptionReporter;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.*;

/**
 * @author Ugnich Anton
 * todo: http://juick.com/Umnik/1612234
 * todo: subscribe to thread
 */
public class MainActivity extends FragmentActivity implements ActionBar.OnNavigationListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int ACTIVITY_SIGNIN = 2;
    public static final int ACTIVITY_PREFERENCES = 3;
    public static final int PENDINGINTENT_CONSTANT = 713242183;

    int lastNavigationPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        Utils.updateThemeHolo(this);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null && uri.getPathSegments().size() > 0 && parseUri(uri)) {
                return;
            }
        }

        if (!Utils.hasAuth(this)) {
            startActivityForResult(new Intent(this, SignInActivity.class), ACTIVITY_SIGNIN);
            return;
        }

        startCheckUpdates(this);
        startPreferencesStorage(this);


        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        toggleXMPP();

        clearObsoleteImagesInCache();

        ActionBar bar = getSupportActionBar();
        bar.setDisplayShowHomeEnabled(false);
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        bar.setListNavigationCallbacks(ArrayAdapter.createFromResource(this, R.array.messagesLists, android.R.layout.simple_list_item_1), this);

        setContentView(R.layout.messages);


    }

    private void clearObsoleteImagesInCache() {
        final File cacheDir = new File(getCacheDir(), "image_cache");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        int daysToKeep = 10;
        try {
            daysToKeep = Integer.parseInt(sp.getString("image.daystokeep", "10"));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid 'days to keep cache' value - number format", Toast.LENGTH_SHORT).show();
        }
        final int finalDaysToKeep = daysToKeep;
        new Thread() {
            @Override
            public void run() {
                String[] list = cacheDir.list();
                if (list != null) {
                    for (String fname : list) {
                        File file = new File(cacheDir, fname);
                        if (file.lastModified() < System.currentTimeMillis() - finalDaysToKeep * 24 * 60 * 60 * 1000) {
                            file.delete();
                        }
                    }
                }
            }

        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void toggleXMPP() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useXMPP = sp.getBoolean("useXMPP", false);
        if (useXMPP) {
            startService(new Intent(this, XMPPService.class));
        } else {
            if (isMyServiceRunning()) {
                Intent service = new Intent(this, XMPPService.class);
                service.putExtra("terminate", true);
                startService(service);
            }
        }
    }

    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        String className = XMPPService.class.getName();
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (className.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startPreferencesStorage(final MainActivity mainActivity) {
//        new Thread() {
//            @Override
//            public void run() {
//                String body = "BL";
//                String whitelist = Utils.postJSON(mainActivity, "http://api.juick.com/post", "body=" + body);
//                System.out.println(whitelist);
//            }
//        }.start();
    }

    public static void startCheckUpdates(Context context) {
        Intent intent = new Intent(context, CheckUpdatesReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, PENDINGINTENT_CONSTANT, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        int interval = 5;
        try {
            interval = Integer.parseInt(sp.getString("refresh", "5"));
        } catch (Exception ex) {}
        if (interval > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, 5);
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), interval * 60000, sender);
        } else {
            am.cancel(sender);
        }
    }

    public boolean onNavigationItemSelected(int itemPosition, long _) {
        lastNavigationPosition = itemPosition;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        MessagesFragment mf = new MessagesFragment();
        Bundle args = new Bundle();
        if (itemPosition == 0) {
            args.putBoolean("home", true);
        } else if (itemPosition == 2) {
            args.putBoolean("popular", true);
        } else if (itemPosition == 3) {
            args.putBoolean("media", true);
        }
        mf.setArguments(args);
        ft.replace(R.id.messagesfragment, mf);
        ft.commit();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_SIGNIN) {
            if (resultCode == RESULT_OK) {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            } else {
                finish();
            }
        } else if (requestCode == ACTIVITY_PREFERENCES) {
            if (resultCode == RESULT_OK) {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuitem_preferences:
                startActivityForResult(new Intent(this, JuickPreferencesActivity.class), ACTIVITY_PREFERENCES);
                return true;
            case R.id.menuitem_newmessage:
                startActivity(new Intent(this, NewMessageActivity.class));
                return true;
            case R.id.menuitem_search:
                startActivity(new Intent(this, ExploreActivity.class));
                return true;
            case R.id.reload:
                if (lastNavigationPosition != -1) {
                    if (lastNavigationPosition == 1) {
                        // clear save pointer
                        MessagesFragment.clearSavedPosition(this);
                    }
                    onNavigationItemSelected(lastNavigationPosition, -1);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean parseUri(Uri uri) {
        List<String> segs = uri.getPathSegments();
        if ((segs.size() == 1 && segs.get(0).matches("\\A[0-9]+\\z"))
                || (segs.size() == 2 && segs.get(1).matches("\\A[0-9]+\\z") && !segs.get(0).equals("places"))) {
            int mid = Integer.parseInt(segs.get(segs.size() - 1));
            if (mid > 0) {
                finish();
                Intent intent = new Intent(this, ThreadActivity.class);
                intent.setData(null);
                intent.putExtra("mid", mid);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            }
        } else if (segs.size() == 1 && segs.get(0).matches("\\A[a-zA-Z0-9\\-]+\\z")) {
            //TODO show user
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals("useXMPP")) {
            toggleXMPP();
        }
    }
}
