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
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.juick.android.datasource.*;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;
import yuku.ambilwarna.widget.AmbilWarnaPreference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
    Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter;
    Handler handler;


    class NavigationItem {
        int labelId;

        NavigationItem(int labelId) {
            this.labelId = labelId;
        }

        void action() {
        }

        void restoreReadMarker() {

        }

    }

    ArrayList<NavigationItem> navigationItems = new ArrayList<NavigationItem>();

    void runDefaultFragmentWithBundle(Bundle args, NavigationItem ni) {
        mf = new MessagesFragment(restoreData, this);
        restoreData = null;
        lastNavigationItem = ni;
        replaceFragment(mf, args);
    }

    public static int nActiveMainActivities = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        nActiveMainActivities++;
        System.out.println(AmbilWarnaPreference.class);
        ExceptionReporter.register(this);
        Utils.updateThemeHolo(this);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        handler = new Handler();

        super.onCreate(savedInstanceState);
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        displayWidth = getWindow().getWindowManager().getDefaultDisplay().getWidth();
        displayHeight = getWindow().getWindowManager().getDefaultDisplay().getHeight();

        Intent intent = getIntent();
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null && uri.getPathSegments().size() > 0 && parseUri(uri)) {
                return;
            }
        }

        if (!Utils.hasAuth(getApplicationContext())) {
            startActivityForResult(new Intent(this, SignInActivity.class), ACTIVITY_SIGNIN);
            return;
        }

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
                JuickMessagesSource ms = getSubscriptionsMessageSource(labelId);
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
                    final NavigationItem thiz = this;
                    withUserId(MainActivity.this, new Utils.Function<Void, Integer>() {
                        @Override
                        public Void apply(Integer uid) {
                            final Bundle args = new Bundle();
                            JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this).putArg("user_id", ""+uid);
                            ms.setKind("my_home");
                            args.putSerializable("messagesSource", ms);
                            runDefaultFragmentWithBundle(args, thiz);
                            return null;
                        }
                    });
                }
            });
            if (sp.getBoolean("msrcSrachiki", false)) {
                navigationItems.add(new NavigationItem(R.string.navigationSrachiki) {
                    @Override
                    void action() {
                        final Bundle args = new Bundle();
                        JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://s.jugregator.org/api");
                        ms.canNext = false;
                        ms.setKind("srachiki");
                        args.putSerializable("messagesSource", ms);
                        runDefaultFragmentWithBundle(args, this);
                    }
                });
            }
            if (sp.getBoolean("msrcPrivate", false)) {
                navigationItems.add(new NavigationItem(R.string.navigationPrivate) {
                    @Override
                    void action() {
                        final Bundle args = new Bundle();
                        JuickMessagesSource ms = new JuickWebCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://dev.juick.com/?show=private");
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
                                                .setTitle(getString(R.string.UnreadSegments))
                                                .setMessage(getString(R.string.YouHaveNotEnabledForUnreadSegments))
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
                                                .setTitle(getString(R.string.ChooseUnreadSegment))
                                                .setView(unreadSegmentsView)
                                                .setCancelable(true)
                                                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
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
                        final int myIndex = navigationItems.indexOf(this);
                        String juboRssURL = sp.getString("juboRssURL", "");
                        if (juboRssURL.length() == 0) {
                            xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                                @Override
                                public void withService(XMPPService service) {
                                    boolean canAskJubo = false;
                                    String message = getString(R.string.JuboRSSURLIsUnknown);
                                    if (!service.juboOnline) {
                                        boolean useXMPP = sp.getBoolean("useXMPP", false);
                                        if (!useXMPP) {
                                            message += getString(R.string.ICouldAskButXMPPIsOff);
                                        } else {
                                            if (service.botOnline) {
                                                message += getString(R.string.JuboNotThere);
                                            } else {
                                                message += getString(R.string.ICouldButXMPPIfNotWorking);
                                            }
                                        }
                                    } else {
                                        canAskJubo = true;
                                        message += getString(R.string.OrICanAskJuboNoe);
                                    }
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                                            .setTitle(R.string.navigationJuboRSS)
                                            .setMessage(message)
                                            .setCancelable(true)
                                            .setNeutralButton(getString(R.string.EnterJuboRSSURLManually), new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    enterJuboURLManually(myIndex);
                                                }
                                            })
                                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    dialogInterface.dismiss();
                                                    restoreLastNavigationPosition();
                                                }
                                            });
                                    if (canAskJubo) {
                                        builder.setPositiveButton(getString(R.string.AskJuBo), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                askJuboFirst(myIndex);
                                            }
                                        });
                                    }
                                    builder.show();
                                }
                            });
                        } else {
                            openJuboMessages(myIndex);
                        }
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
                        TextView tv = (TextView) retval;
                        tv.setText(getString(navigationItems.get(position).labelId));
                        if (compressedMenu) {
                            int minHeight = (int) ((screenHeight * 0.7) / getCount());
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
    }

    private JuickMessagesSource getSubscriptionsMessageSource(int labelId) {
        if (sp.getBoolean("web_for_subscriptions", false)) {
            return new JuickWebCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://dev.juick.com/?show=my");
        } else {
            return new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://api.juick.com/home");
        }
    }

    public static void withUserId(final Activity activity, final Utils.Function<Void,Integer> action) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        final String myUserId = sp.getString("myUserId", "");
        if (myUserId.equals("")) {
            final String value = Utils.getAccountName(activity.getApplicationContext());
            final ProgressDialog pd = new ProgressDialog(activity);
            pd.setTitle(activity.getString(R.string.GettingYourId));
            pd.setMessage(activity.getString(R.string.ConnectingToWwwJuick));
            pd.setIndeterminate(true);
            pd.show();
            final AndroidHttpClient httpClient = AndroidHttpClient.newInstance(activity.getString(R.string.com_juick));
            new Thread("UserID obtainer") {
                @Override
                public void run() {
                    String fullName = value;
                    if (fullName.startsWith("@")) fullName = fullName.substring(1);
                    try {
                        URL u = new URL("http://juick.com/" + fullName.trim() + "/");
                        HttpURLConnection urlConnection = (HttpURLConnection)u.openConnection();
                        urlConnection.setInstanceFollowRedirects(true);
                        Utils.RESTResponse response = Utils.streamToString((InputStream) urlConnection.getContent(), null);
                        if (response.getErrorText() != null) {
                            throw new IOException(response.getErrorText());
                        } else {
                            String retval = response.getResult();
                            String SEARCH_MARKER = "http://i.juick.com/a/";
                            int ix = retval.indexOf(SEARCH_MARKER);
                            if (ix < 0) {
                                throw new RuntimeException(activity.getString(R.string.WebSiteReturnedBad));
                            }
                            int ix2 = retval.indexOf(".png", ix + SEARCH_MARKER.length());
                            if (ix2 < 0 || ix2 - (ix + SEARCH_MARKER.length()) > 15) {  // optimistic!
                                throw new RuntimeException(activity.getString(R.string.WebSiteReturnedBad));
                            }
                            final String uidS = retval.substring(ix + SEARCH_MARKER.length(), ix2);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    sp.edit().putString("myUserId", uidS).commit();
                                    action.apply(Integer.parseInt(uidS));
                                }
                            });
                        }

                    } catch (final Exception e) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, activity.getString(R.string.UnableToDetectNick) + e.toString(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } finally {
                        httpClient.close();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.hide();
                            }
                        });
                    }
                }
            }.start();
        } else {
            action.apply(Integer.parseInt(myUserId));
        }
    }

    private void askJuboFirst(final int juboIndex) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setIndeterminate(true);
        pd.setMessage(getString(R.string.TalkingToJuBo));
        pd.show();
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withService(XMPPService service) {
                service.askJuboRSS();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkJuboReturnedRSS(juboIndex, pd);
                    }
                }, 10000);
            }
        });
    }

    private void checkJuboReturnedRSS(final int juboIndex, final ProgressDialog pd) {
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withService(XMPPService service) {
                pd.hide();
                if (service.juboRSS != null) {
                    sp.edit().putString("juboRssURL", service.juboRSS).commit();
                    openJuboMessages(juboIndex);
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.navigationJuboRSS)
                            .setMessage(service.juboRSSError)
                            .setCancelable(true)
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    restoreLastNavigationPosition();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                    restoreLastNavigationPosition();
                                }
                            }).show();
                }
            }
        });
    }

    private void enterJuboURLManually(final int myIndex) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.EnterJuboRSSURLManually)
                .setView(et)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sp.edit().putString("juboRssURL", "" + et.getText()).commit();
                        openJuboMessages(myIndex);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        restoreLastNavigationPosition();
                    }
                }).show();
    }

    private void openJuboMessages(int myIndex) {
        final Bundle args = new Bundle();
        args.putSerializable("messagesSource", new JuboMessagesSource(MainActivity.this));
        runDefaultFragmentWithBundle(args, navigationItems.get(myIndex));
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
        nActiveMainActivities--;
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
                Intent prefsIntent = new Intent(this, NewJuickPreferenceActivity.class);
                prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.TOP_LEVEL.name());
                startActivityForResult(prefsIntent, ACTIVITY_PREFERENCES);
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
                intent.putExtra("isolated", true);
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
            ((AbsListView) view).setCacheColorHint(colorTheme.getBackground(pressed));
        }
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(colorTheme.getForeground(pressed));
            et.setBackgroundColor(colorTheme.getBackground(pressed));
        } else if (view instanceof RadioButton) {
            RadioButton btn = (RadioButton) view;
            btn.setTextColor(colorTheme.getForeground(pressed));
            btn.setBackgroundColor(colorTheme.getBackground());
        } else if (view instanceof Spinner) {
            View scan = view;
            boolean shouldRecolor = false;
            while (scan != null) {
                if (scan.getClass().getName().toLowerCase().contains("action")) {
                    shouldRecolor = true;
                    break;
                }
                try {
                    scan = (View)scan.getParent();
                } catch (Exception e) {
                    scan = null;
                }
            }
            if (shouldRecolor)
                restyleViewGroup((Spinner) view, colorTheme, pressed);
        } else if (view instanceof Button) {
//            Button btn = (Button) view;
//            btn.setTextColor(colorTheme.getForeground(pressed));
//            btn.setBackgroundColor(colorTheme.getButtonBackground());
        } else if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(colorTheme.getForeground(pressed));
        } else if (view instanceof ViewGroup) {
            restyleViewGroup((ViewGroup) view, colorTheme, pressed);
        }
    }

    private static void restyleViewGroup(ViewGroup view, ColorsTheme.ColorTheme colorTheme, boolean pressed) {
        ViewGroup parent = (ViewGroup) view;
        int childCount = parent.getChildCount();
        parent.setBackgroundColor(colorTheme.getBackground(pressed));
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            System.out.println(child);
            restyleChildrenOrWidget(child);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals("useXMPP")) {
            toggleXMPP();
        }
    }

}
