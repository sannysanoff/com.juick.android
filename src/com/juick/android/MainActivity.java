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

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.*;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.*;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.juick.android.datasource.AllMessagesSource;
import com.juick.android.datasource.JuickCompatibleURLMessagesSource;
import com.juick.android.datasource.SavedMessagesSource;
import com.juick.android.datasource.UnreadSegmentMessagesSource;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import yuku.ambilwarna.widget.AmbilWarnaPreference;

import java.io.*;
import java.util.*;

/**
 * @author Ugnich Anton
 *         todo: http://juick.com/Umnik/1612234
 *         todo: subscribe to thread
 */
public class MainActivity extends FragmentActivity implements
        ActionBar.OnNavigationListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int ACTIVITY_SIGNIN = 2;
    public static final int ACTIVITY_PREFERENCES = 3;
    public static final int PENDINGINTENT_CONSTANT = 713242183;

    public static int displayWidth;
    public static int displayHeight;

    NavigationItem lastNavigationItem = null;
    MessagesFragment mf;
    Object restoreData;
    private SharedPreferences sp;

    class NavigationItem {
        int labelId;

        NavigationItem(int labelId) {
            this.labelId = labelId;
        }

        void action() {}

        void restoreReadMarker() {

        }

    }

    ArrayList<NavigationItem> navigationItems = new ArrayList<NavigationItem>();

    void runDefaultFragmentWithBundle(Bundle args, NavigationItem ni) {
        mf = new MessagesFragment(restoreData);
        restoreData = null;
        lastNavigationItem = ni;
        replaceFragment(mf, args);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        System.out.println(AmbilWarnaPreference.class);
        ExceptionReporter.register(this);
        Utils.updateThemeHolo(this);
        sp = PreferenceManager.getDefaultSharedPreferences(this);

        super.onCreate(savedInstanceState);
        displayWidth = getWindow().getWindowManager().getDefaultDisplay().getWidth();
        displayHeight = getWindow().getWindowManager().getDefaultDisplay().getHeight();

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

        //startCheckUpdates(this);
        startPreferencesStorage(this);


        sp.registerOnSharedPreferenceChangeListener(this);
        toggleXMPP();
        startService(new Intent(this, DatabaseService.class));

        clearObsoleteImagesInCache();
        updateNavigation();
        maybeSendUsageReport();

        ActionBar bar = getSupportActionBar();
        bar.setDisplayShowHomeEnabled(false);
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);


//        if (getIntent().hasExtra("lastNavigationPosition")) {
//            int lastNavigationPosition1 = getIntent().getExtras().getInt("lastNavigationPosition");
//            bar.selectTab(bar.getTabAt(lastNavigationPosition1));
//        }

        setContentView(R.layout.messages);
        restoreData = getLastCustomNonConfigurationInstance();
        new WhatsNew(this).runAll();
    }

    public static Thread usageReportThread;

    private void maybeSendUsageReport() {
        if (usageReportThread != null) return;
        String sendStats = sp.getString("usage_statistics", "no");
        if (!sendStats.equals("send") && !sendStats.equals("send_wifi")) return;
        long last_usage_sent = sp.getLong("last_usage_sent", 0);
        if (last_usage_sent == 0) {
            sp.edit().putLong("last_usage_sent", System.currentTimeMillis()).commit();
            return;
        }
        if (System.currentTimeMillis() - last_usage_sent > WhatsNew.REPORT_SEND_PERIOD) {
            ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (sendStats.equals("send_wifi") && !wifi.isConnected()) return;
            usageReportThread = new Thread() {
                @Override
                public void run() {
                    new WhatsNew(MainActivity.this).reportUsage();
                }
            };
            usageReportThread.start();
        }
    }

    public void updateNavigation() {
        navigationItems = new ArrayList<NavigationItem>();
        navigationItems.add(new NavigationItem(R.string.navigationSubscriptions) {
            @Override
            void action() {
                final Bundle args = new Bundle();
                JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://api.juick.com/home");
                ms.setKind("home");
                args.putSerializable("messagesSource", ms);
                runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new NavigationItem(R.string.navigationAll) {
            @Override
            void action() {
                final Bundle args = new Bundle();
                args.putSerializable("messagesSource", new AllMessagesSource(MainActivity.this));
                runDefaultFragmentWithBundle(args, this);
            }

            @Override
            void restoreReadMarker() {
                mf.clearSavedPosition(MainActivity.this);
            }
        });
        if (sp.getBoolean("msrcTopMessages", true)) {
            navigationItems.add(new NavigationItem(R.string.navigationTop) {
                @Override
                void action() {
                    final Bundle args = new Bundle();
                    JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this).putArg("popular", "1");
                    ms.setKind("popular");
                    args.putSerializable("messagesSource", ms);
                    runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcWithPhotos", true)) {
            navigationItems.add(new NavigationItem(R.string.navigationPhoto) {
                @Override
                void action() {
                    final Bundle args = new Bundle();
                    JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this).putArg("media", "all");
                    ms.setKind("media");
                    args.putSerializable("messagesSource", ms);
                    runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcMyBlog", true)) {
            navigationItems.add(new NavigationItem(R.string.navigationMy) {
                @Override
                void action() {
                    if (sp.getString("myUserId", "").equals("")) {
                        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);

                        alert.setTitle(R.string.Your_User_Name);
                        alert.setMessage(R.string.Your_User_Name_Explain);

                        // Set an EditText view to get user input
                        final EditText input = new EditText(MainActivity.this);
                        alert.setView(input);

                        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                final String value = input.getText().toString();
                                if (value.length() > 0) {
                                    final ProgressDialog pd = new ProgressDialog(MainActivity.this);
                                    pd.setTitle(getString(R.string.GettingYourId));
                                    pd.setMessage(getString(R.string.ConnectingToWwwJuick));
                                    pd.setIndeterminate(true);
                                    pd.show();
                                    final AndroidHttpClient httpClient = AndroidHttpClient.newInstance(getString(R.string.com_juick));
                                    new Thread("UserID obtainer") {
                                        @Override
                                        public void run() {
                                            String fullName = value;
                                            if (fullName.startsWith("@")) fullName = fullName.substring(1);
                                            try {
                                                HttpGet httpGet = new HttpGet("http://juick.com/" + fullName + "/");
                                                String retval = httpClient.execute(httpGet, new BasicResponseHandler());
                                                String SEARCH_MARKER = "http://i.juick.com/a/";
                                                int ix = retval.indexOf(SEARCH_MARKER);
                                                if (ix < 0) {
                                                    throw new RuntimeException("Website returned unrecognized response");
                                                }
                                                int ix2 = retval.indexOf(".png", ix + SEARCH_MARKER.length());
                                                if (ix2 < 0 || ix2 - (ix + SEARCH_MARKER.length()) > 15) {  // optimistic!
                                                    throw new RuntimeException("Website returned unrecognized response");
                                                }
                                                final String uidS = retval.substring(ix + SEARCH_MARKER.length(), ix2);
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        sp.edit().putString("myUserId", uidS).commit();
                                                        action();
                                                    }
                                                });

                                            } catch (final Exception e) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this, "Unable to detect nick: " + e.toString(), Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                            } finally {
                                                httpClient.close();
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        pd.hide();
                                                    }
                                                });
                                            }
                                        }
                                    }.start();


                                }
                            }
                        });

                        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                restoreLastNavigationPosition();
                            }
                        });

                        alert.show();

                    } else {
                        final Bundle args = new Bundle();
                        JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this).putArg("user_id", sp.getString("myUserId", "12234567788"));
                        ms.setKind("my_home");
                        args.putSerializable("messagesSource",ms);
                        runDefaultFragmentWithBundle(args, this);
                    }
                }
            });
        }
        if (sp.getBoolean("msrcSrachiki", false)) {
            navigationItems.add(new NavigationItem(R.string.navigationSrachiki) {
                @Override
                void action() {
                    final Bundle args = new Bundle();
                    JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://s.jugregator.org/api");
                    ms.setKind("srachiki");
                    args.putSerializable("messagesSource", ms);
                    runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcUnread", false)) {
            navigationItems.add(new NavigationItem(R.string.navigationUnread) {
                @Override
                void action() {
                    final NavigationItem thisNi = this;
                    final ProgressDialog pd = new ProgressDialog(MainActivity.this);
                    pd.setIndeterminate(true);
                    pd.setTitle(R.string.navigationUnread);
                    pd.setCancelable(true);
                    pd.show();
                    UnreadSegmentsView.loadPeriods(MainActivity.this, new Utils.Function<Void, ArrayList<DatabaseService.Period>>() {
                        @Override
                        public Void apply(ArrayList<DatabaseService.Period> periods) {
                            if (pd.isShowing()) {
                                pd.cancel();
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                final AlertDialog alerDialog;
                                if (periods.size() == 0) {
                                    alerDialog = builder
                                            .setTitle("Unread segments")
                                            .setMessage("You have not enabled last read markers in prefs, or you have not collected enough read history")
                                            .setCancelable(true)
                                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    dialogInterface.dismiss();
                                                    restoreLastNavigationPosition();
                                                }
                                            }).create();
                                } else {
                                    UnreadSegmentsView unreadSegmentsView = new UnreadSegmentsView(MainActivity.this, periods);
                                    final int myIndex = navigationItems.indexOf(thisNi);
                                    alerDialog = builder
                                            .setTitle("Choose unread segment")
                                            .setView(unreadSegmentsView)
                                            .setCancelable(true)
                                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    dialogInterface.dismiss();
                                                    restoreLastNavigationPosition();
                                                }
                                            }).create();
                                    unreadSegmentsView.setListener(new UnreadSegmentsView.PeriodListener() {
                                        @Override
                                        public void onPeriodClicked(DatabaseService.Period period) {
                                            alerDialog.dismiss();
                                            int beforeMid = period.beforeMid;
                                            Bundle args = new Bundle();
                                            args.putSerializable(
                                                    "messagesSource",
                                                    new UnreadSegmentMessagesSource(
                                                            getString(R.string.navigationUnread),
                                                            MainActivity.this,
                                                            period
                                                            ));
                                            getSupportActionBar().setSelectedNavigationItem(myIndex);
                                            runDefaultFragmentWithBundle(args, thisNi);
                                        }
                                    });
                                }
                                alerDialog.show();
                                restyleChildrenOrWidget(alerDialog.getWindow().getDecorView());
                            }
                            return null;
                        }
                    });
                    return;
                }
            });
        }
        if (sp.getBoolean("msrcSaved", false)) {
            navigationItems.add(new NavigationItem(R.string.navigationSaved) {
                @Override
                void action() {
                    final Bundle args = new Bundle();
                    args.putSerializable("messagesSource", new SavedMessagesSource(MainActivity.this));
                    runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcJubo", false)) {
            navigationItems.add(new NavigationItem(R.string.navigationJuboRSS) {
                @Override
                void action() {
                    final Bundle args = new Bundle();
                    args.putSerializable("messagesSource", new SavedMessagesSource(MainActivity.this));
                    runDefaultFragmentWithBundle(args, this);
                }
            });
        }

        final boolean compressedMenu = sp.getBoolean("compressedMenu", false);
        float menuFontScale = 1;
        try {
            menuFontScale = Float.parseFloat(sp.getString("menuFontScale", "1.0"));
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        final float finalMenuFontScale = menuFontScale;
        BaseAdapter navigationAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return navigationItems.size();
            }

            @Override
            public Object getItem(int position) {
                return navigationItems.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final int screenHeight = getWindow().getWindowManager().getDefaultDisplay().getHeight();
                View retval = convertView;
                if (retval == null) {
                    retval = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                }
                if (retval instanceof TextView) {
                    TextView tv = (TextView)retval;
                    tv.setText(getString(navigationItems.get(position).labelId));
                    if (compressedMenu) {
                        int minHeight = (int)((screenHeight * 0.7) / getCount());
                        tv.setMinHeight(minHeight);
                        tv.setMinimumHeight(minHeight);
                    }
                    tv.measure(1000, 1000);
                    tv.setTextSize(22 * finalMenuFontScale);
                }
                return retval;
            }
        };


        ActionBar bar = getSupportActionBar();
        bar.setListNavigationCallbacks(navigationAdapter, this);

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } catch (Exception e) {
            /*
            fix for 4.0:
                Caused+by:+java.lang.NullPointerException
                        at+android.view.View.dispatchRestoreInstanceState(View.java:10064)
                        at+android.view.ViewGroup.dispatchRestoreInstanceState(ViewGroup.java:2421)
                        at+android.view.View.restoreHierarchyState(View.java:10047)
                        at+com.android.internal.policy.impl.PhoneWindow.restoreHierarchyState(PhoneWindow.java:1630)
                        at+android.app.Activity.onRestoreInstanceState(Activity.java:906)

             */
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void clearObsoleteImagesInCache() {
        final File cacheDir = new File(getCacheDir(), "image_cache");
        int daysToKeep = 10;
        try {
            daysToKeep = Integer.parseInt(sp.getString("image.daystokeep", "10"));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid 'days to keep cache' value - number format", Toast.LENGTH_SHORT).show();
        }
        final int finalDaysToKeep = daysToKeep;
        new Thread("clearObsoleteImagesInCache") {
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
    public Object onRetainCustomNonConfigurationInstance() {
        if (mf != null) {
            return mf.saveState();
        }
        return super.onRetainCustomNonConfigurationInstance();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sp != null)
            sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void toggleXMPP() {
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
        } catch (Exception ex) {
        }
        if (interval > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, 5);
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), interval * 60000, sender);
        } else {
            am.cancel(sender);
        }
    }

    public boolean onNavigationItemSelected(final int itemPosition, long _) {
        restyle();
        NavigationItem thisItem = navigationItems.get(itemPosition);
        if (lastNavigationItem == thisItem) return false;       // happens during screen rotate
        thisItem.action();
        return true;
    }

    private void restoreLastNavigationPosition() {
        for (int i = 0; i < navigationItems.size(); i++) {
            NavigationItem navigationItem = navigationItems.get(i);
            if (navigationItem == lastNavigationItem) {
                getSupportActionBar().setSelectedNavigationItem(i);
                break;
            }
        }
    }

    private void replaceFragment(MessagesFragment mf, Bundle args) {
        final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        mf.setArguments(args);
        ft.replace(R.id.messagesfragment, mf);
        ft.commit();
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
                if (mf != null) {
                    Intent intent = new Intent(this, ExploreActivity.class);
                    intent.putExtra("messagesSource", mf.messagesSource);
                    startActivity(intent);
                }
                return true;
            case R.id.reload:
                if (lastNavigationItem != null) {
                    NavigationItem oldItem = lastNavigationItem;
                    lastNavigationItem.restoreReadMarker();
                    lastNavigationItem = null;
                    oldItem.action();
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
    protected void onResume() {
        restyle();
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.}
    }

    private void restyle() {
        final ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
        restyleChildrenOrWidget(decorView);
    }


    public static void restyleChildrenOrWidget(View view) {
        if (view == null) return;
        ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(view.getContext());
        boolean pressed = view.isPressed();
        if (view instanceof AbsListView) {
            ((AbsListView)view).setCacheColorHint(colorTheme.getBackground(pressed));
        }
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(colorTheme.getForeground(pressed));
            et.setBackgroundColor(colorTheme.getBackground(pressed));
        } else if (view instanceof Button) {
//            Button btn = (Button) view;
//            btn.setTextColor(colorTheme.getForeground(pressed));
//            btn.setBackgroundColor(colorTheme.getButtonBackground());
        } else if (view instanceof RadioButton) {
            RadioButton btn = (RadioButton) view;
            btn.setTextColor(colorTheme.getForeground(pressed));
            btn.setBackgroundColor(colorTheme.getButtonBackground());
        } else if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(colorTheme.getForeground(pressed));
        } else if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            int childCount = parent.getChildCount();
            parent.setBackgroundColor(colorTheme.getBackground(pressed));
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                System.out.println(child);
                restyleChildrenOrWidget(child);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals("useXMPP")) {
            toggleXMPP();
        }
    }

}
