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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.text.TextUtils;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.juick.android.bnw.BNWMicroBlog;
import com.juick.android.juick.*;
import com.juick.android.psto.PstoMicroBlog;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;
import yuku.ambilwarna.widget.AmbilWarnaPreference;

import java.io.File;
import java.util.*;

/**
 * @author Ugnich Anton
 *         todo: http://juick.com/Umnik/1612234
 *
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
    public MessagesFragment mf;
    Object restoreData;
    public SharedPreferences sp;
    public Handler handler;
    private boolean resumed;
    private boolean reloadOnResume;

    public static Map<String, MicroBlog> microBlogs = new HashMap<String, MicroBlog>();

    static {
        JuickMicroBlog juickMicroBlog = new JuickMicroBlog();
        microBlogs.put(juickMicroBlog.getCode(), juickMicroBlog);
        BNWMicroBlog bnwMicroBlog = new BNWMicroBlog();
        microBlogs.put(bnwMicroBlog.getCode(), bnwMicroBlog);
        PstoMicroBlog pstoMicroBlog = new PstoMicroBlog();
        microBlogs.put(pstoMicroBlog.getCode(), pstoMicroBlog);

        for (MicroBlog microBlog : microBlogs.values()) {
            microBlog.initialize();
        }
    }

    public static MicroBlog getMicroBlog(String key) {
        return microBlogs.get(key);
    }


    public static class NavigationItem {
        public int labelId;

        public NavigationItem(int labelId) {
            this.labelId = labelId;
        }

        public void action() {
        }

        public void restoreReadMarker() {

        }

    }

    public ArrayList<NavigationItem> navigationItems = new ArrayList<NavigationItem>();

    public void runDefaultFragmentWithBundle(Bundle args, NavigationItem ni) {
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

    private void gotoSubscriptions() {
        final Bundle args = new Bundle();
        JuickMessagesSource ms = getSubscriptionsMessageSource(R.string.navigationSubscriptions);
        ms.setKind("home");
        args.putSerializable("messagesSource", ms);
        runDefaultFragmentWithBundle(args, subscriptionsItem);
    }

    NavigationItem subscriptionsItem;
    public void updateNavigation() {
        navigationItems = new ArrayList<NavigationItem>();
        subscriptionsItem = new NavigationItem(R.string.navigationSubscriptions) {
            @Override
            public void action() {
                gotoSubscriptions();
            }

        };
        navigationItems.add(subscriptionsItem);

        List<MicroBlog> blogs = new ArrayList<MicroBlog>(microBlogs.values());
        Collections.<MicroBlog>sort(blogs, new Comparator<MicroBlog>() {
            @Override
            public int compare(MicroBlog microBlog, MicroBlog microBlog2) {
                return microBlog.getPiority() - microBlog2.getPiority();
            }
        });
        for (MicroBlog blog : blogs) {
            blog.addNavigationSources(navigationItems, this);
        }
        if (sp.getBoolean("msrcUnread", false)) {
            navigationItems.add(new NavigationItem(R.string.navigationUnread) {
                @Override
                public void action() {
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
                public void action() {
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
                    TextView tv = (TextView) retval;
                    if (parent instanceof Spinner) {
                        tv.setTextSize(18 * finalMenuFontScale);
                        tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                    } else {
                        tv.setTextSize(22 * finalMenuFontScale);
                    }
                    tv.setText(getString(navigationItems.get(position).labelId));
                    if (compressedMenu) {
                        int minHeight = (int) ((screenHeight * 0.7) / getCount());
                        tv.setMinHeight(minHeight);
                        tv.setMinimumHeight(minHeight);
                    }
                }
                return retval;
            }
        };


        ActionBar bar = getSupportActionBar();
        bar.setListNavigationCallbacks(navigationAdapter, this);


    }


    private JuickMessagesSource getSubscriptionsMessageSource(int labelId) {
        if (sp.getBoolean("web_for_subscriptions", false)) {
            return new JuickWebCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://dev.juick.com/?show=my");
        } else {
            return new JuickCompatibleURLMessagesSource(getString(labelId), MainActivity.this, "http://api.juick.com/home");
        }
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

    public void restoreLastNavigationPosition() {
        for (int i = 0; i < navigationItems.size(); i++) {
            NavigationItem navigationItem = navigationItems.get(i);
            if (navigationItem == lastNavigationItem) {
                getSupportActionBar().setSelectedNavigationItem(i);
                break;
            }
        }
    }

    private void replaceFragment(MessagesFragment mf, Bundle args) {
        try {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            mf.setArguments(args);
            ft.replace(R.id.messagesfragment, mf);
            ft.commit();
        } catch (Exception e) {
            // tanunax
        }
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
                Intent intent1 = new Intent(this, NewMessageActivity.class);
                intent1.putExtra("messagesSource", mf.messagesSource);
                startActivity(intent1);
                return true;
            case R.id.menuitem_search:
                if (mf != null) {
                    Intent intent = new Intent(this, ExploreActivity.class);
                    intent.putExtra("messagesSource", mf.messagesSource);
                    startActivity(intent);
                }
                return true;
            case R.id.reload:
                doReload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    long lastReload = System.currentTimeMillis();
    private void doReload() {
        if (System.currentTimeMillis() - lastReload < 1000) return;
        if (resumed) {
            if (lastNavigationItem != null) {
                NavigationItem oldItem = lastNavigationItem;
                lastNavigationItem.restoreReadMarker();
                lastNavigationItem = null;
                oldItem.action();
                lastReload = System.currentTimeMillis();
            }
        } else {
            reloadOnResume = true;
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
                intent.putExtra("messageSource", new JuickCompatibleURLMessagesSource(this));
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
        resumed = true;
        super.onResume();
        if (reloadOnResume) {
            doReload();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
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
        boolean dontWatchPreferences = sp.getBoolean("dontWatchPreferences", false);
        if (dontWatchPreferences) return;
        if (s.startsWith("msrc")) {
            updateNavigation();
        }
        if (s.startsWith("Colors.")) {
            doReload();
        }
        String[] refreshCauses = new String[] {
                "messagesFontScale",
                "showNumbers",
                "showUserpics",
        };
        for (String refreshCause : refreshCauses) {
            if (refreshCause.equals(s)) {
                doReload();
                break;
            }
        }
    }

}
