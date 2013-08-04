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
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.*;
import com.juick.android.bnw.BNWMicroBlog;
import com.juick.android.bnw.BnwCompatibleMessagesSource;
import com.juick.android.juick.*;
import com.juick.android.psto.PstoCompatibleMessagesSource;
import com.juick.android.psto.PstoMicroBlog;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.psto.PstoMessageID;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import org.acra.ACRA;

import java.io.File;
import java.util.*;

/**
 * @author Ugnich Anton
 */
public class MainActivity extends JuickFragmentActivity implements
        ActionBar.OnNavigationListener,
        IRunningActivity,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int ACTIVITY_SIGNIN = 2;
    public static final int ACTIVITY_PREFERENCES = 3;
    public static final int PENDINGINTENT_CONSTANT = 713242183;

    public static int displayWidth;
    public static int displayHeight;

    NavigationItem lastNavigationItem = null;
    Object restoreData;
    public SharedPreferences sp;
    public Handler handler;
    public boolean resumed;
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

    public static MicroBlog getMicroBlog(MessageID mid) {
        return microBlogs.get(mid.getMicroBlogCode());
    }

    public static MicroBlog getMicroBlog(JuickMessage msg) {
        return microBlogs.get(msg.getMID().getMicroBlogCode());
    }


    public static class NavigationItem {
        public int labelId;
        public int imageId;
        public int id;
        public int itemOrder;
        public String sharedPrefsKey;

        public NavigationItem(int id, int labelId, int imageId, String sharedPrefsKey) {
            this.labelId = labelId;
            this.imageId = imageId;
            this.id = id;
            this.sharedPrefsKey = sharedPrefsKey;
        }

        public void action() {
        }

        public void restoreReadMarker() {

        }

    }


    boolean navigationMenuShown = false;
    public boolean isNavigationMenuShown() {
        return navigationMenuShown;
    }

    public void openNavigationMenu(boolean animate) {
        if (isNavigationMenuShown()) return;
        navigationMenuShown = true;
        final ViewGroup navigationPanel = (ViewGroup)findViewById(R.id.navigation_panel);
        final View frag = (ViewGroup)findViewById(R.id.messagesfragment);
        if (animate) {
            AnimationSet set = new AnimationSet(true);
            TranslateAnimation translate = new TranslateAnimation(0, navigationPanel.getWidth(), 0, 0);
            translate.setFillAfter(false);
            translate.setFillEnabled(true);
            translate.setDuration(400);
            set.addAnimation(translate);
            set.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    frag.clearAnimation();
                    layoutNavigationPane();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
            frag.startAnimation(set);
        } else {
            layoutNavigationPane();
        }
    }

    public void closeNavigationMenu(boolean animate) {
        if (!isNavigationMenuShown()) return;
        final ViewGroup navigationPanel = (ViewGroup)findViewById(R.id.navigation_panel);
        final View frag = findViewById(R.id.messagesfragment);
        final DragSortListView navigationList = (DragSortListView)findViewById(R.id.navigation_list);
        if (navigationList.isDragEnabled()) {
            navigationList.setDragEnabled(false);
            updateNavigation();
        }
        if (animate) {
            final AnimationSet set = new AnimationSet(true);
            TranslateAnimation translate = new TranslateAnimation(0, -navigationPanel.getWidth(), 0, 0);
            translate.setFillAfter(false);
            translate.setFillEnabled(true);
            translate.setDuration(400);
            set.addAnimation(translate);
            set.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    navigationMenuShown = false;
                    frag.clearAnimation();
                    layoutNavigationPane();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    frag.startAnimation(set);
                    getActivity().findViewById(R.id.layout_container).invalidate();
                }
            }, 800); // without this animation does not always run if launched by list item click (back button ok). Investigate

        } else {
            navigationMenuShown = false;
            layoutNavigationPane();
        }
    }



    public ArrayList<NavigationItem> navigationItems = new ArrayList<NavigationItem>();
    public ArrayList<NavigationItem> allNavigationItems = new ArrayList<NavigationItem>();

    public void runDefaultFragmentWithBundle(Bundle args, NavigationItem ni) {
        mf = new MessagesFragment(restoreData, this);
        setFragmentNeededMetrics();
        restoreData = null;
        lastNavigationItem = ni;
        final SharedPreferences spn = getSharedPreferences("saved_last_navigation_type", MODE_PRIVATE);
        spn.edit().putInt("last_navigation", ni.labelId).commit();
        replaceFragment(mf, args);
    }

    private void setFragmentNeededMetrics() {
        if (sp.getBoolean("googlePlusNavigation", false)) {
            final ViewGroup navigationPanel = (ViewGroup)findViewById(R.id.navigation_panel);
            if (mf != null && navigationPanel.getWidth() != 0) {
                mf.setRightScrollBound(navigationPanel.getWidth());
            }
        }
    }

    public static int nActiveMainActivities = 0;

    @Override
    protected void onNewIntent(Intent intent) {
        if (navigationItems.size() == 0) {
            initStage2();
        }
        super.onNewIntent(intent);
        maybeLaunchIntent(intent, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        JuickAdvancedApplication.setupTheme(this);
        XMPPService.log("MainActivity.create()");

        nActiveMainActivities++;
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        handler = new Handler();
        super.onCreate(savedInstanceState);
        displayWidth = getWindow().getWindowManager().getDefaultDisplay().getWidth();
        displayHeight = getWindow().getWindowManager().getDefaultDisplay().getHeight();

        if (maybeLaunchIntent(getIntent(), true)) return;

        initStage2();

    }

    private void initStage2() {
        startPreferencesStorage(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        if (sp.getBoolean("enableLoginNameWithCrashReport", false)) {
            ACRA.getErrorReporter().putCustomData("juick_user", JuickAPIAuthorizer.getJuickAccountName(this));
        }
        if (sp.getBoolean("fullScreenMessages", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        }
        toggleXMPP(this);
        toggleJAMessaging();
        startService(new Intent(this, DatabaseService.class));

        clearObsoleteImagesInCache();


        maybeSendUsageReport();
        maybeWarnXMPP();

        updateActionBarMode();


//        if (getIntent().hasExtra("lastNavigationPosition")) {
//            int lastNavigationPosition1 = getIntent().getExtras().getInt("lastNavigationPosition");
//            bar.selectTab(bar.getTabAt(lastNavigationPosition1));
//        }

        MessageListBackingData mlbd = JuickAdvancedApplication.instance.getSavedList(getActivity());
        if (mlbd != null) {
            for (int i = 0; i < navigationItems.size(); i++) {
                NavigationItem navigationItem = navigationItems.get(i);
                if (navigationItem.labelId == mlbd.navigationItemLabelId) {
                    setSelectedNavigationItem(i);
                    break;
                }
            }
        }

        setContentView(R.layout.main);
        final MyRelativeLayout mll = (MyRelativeLayout)findViewById(R.id.layout_container);
        mll.listener = new MyRelativeLayout.Listener() {
            @Override
            public void onLayout() {
                layoutNavigationPane();
                setFragmentNeededMetrics();
            }
        };
        restoreData = getLastCustomNonConfigurationInstance();
        new WhatsNew(this).runAll();
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());

        WhatsNew.checkForUpdates(this, null, false);
        updateNavigation();

    }

    int selectedNavigationItem = -1;



    private int getSelectedNavigationIndex() {
        return selectedNavigationItem;
    }

    private void setSelectedNavigationItem(int index) {
        boolean googlePlus = sp.getBoolean("googlePlusNavigation", false);
        selectedNavigationItem = index;
        if (!googlePlus) {
            getSupportActionBar().setSelectedNavigationItem(index);
        } else {
            onNavigationItemSelected(index, 0);
        }
        updateNavigationBarTitle();
    }

    private void updateActionBarMode() {
        boolean googlePlus = sp.getBoolean("googlePlusNavigation", false);
        ActionBar bar = getSupportActionBar();
        bar.setDisplayShowTitleEnabled(false);
        bar.setDisplayShowHomeEnabled(false);
        bar.setDisplayHomeAsUpEnabled(false);
        bar.setNavigationMode(googlePlus ? ActionBar.NAVIGATION_MODE_STANDARD : ActionBar.NAVIGATION_MODE_LIST);
        bar.setDisplayShowCustomEnabled(googlePlus);

        if (googlePlus) {
            View inflate = getLayoutInflater().inflate(R.layout.navbar, null);
            bar.setCustomView(inflate);
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isNavigationMenuShown()) {
                        closeNavigationMenu(true);
                    } else {
                        openNavigationMenu(true);
                    }
                }
            };
            inflate.findViewById(R.id.button).setOnClickListener(listener);
            inflate.findViewById(R.id.text).setOnClickListener(listener);
            restyleChildrenOrWidget(inflate, false);
        }
        updateNavigationBarTitle();
    }

    private void updateNavigationBarTitle() {
        if (getSelectedNavigationIndex() != -1) {
            ActionBar bar = getSupportActionBar();
            String title = getString(navigationItems.get(getSelectedNavigationIndex()).labelId);
            bar.setTitle(title);
            if (bar.getCustomView() != null) {
                ((TextView)bar.getCustomView().findViewById(R.id.text)).setText(title);
            }
        }
    }

    @Override
    public void onFragmentCreated() {
        super.onFragmentCreated();    //To change body of overridden methods use File | Settings | File Templates.
        // ((MyListView)mf.getListView()).mindLeftFling = true;
    }

    private void layoutNavigationPane() {
        final MyRelativeLayout mll = (MyRelativeLayout)findViewById(R.id.layout_container);
        final View navigationPanel = mll.findViewById(R.id.navigation_panel);
        boolean oldBlockLayoutRequests = mll.blockLayoutRequests;
        mll.blockLayoutRequests = true;
        int pix = navigationPanel.getWidth();
        final View frag = mll.findViewById(R.id.messagesfragment);
        if (isNavigationMenuShown()) {
            frag.layout(pix, 0, pix + mll.getWidth(), mll.getHeight());
        } else {
            frag.layout(0, 0, mll.getWidth(), mll.getHeight());
        }
        frag.clearAnimation();
        mll.blockLayoutRequests = oldBlockLayoutRequests;
    }


    public static float spToPixels(Context context, Float sp) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return sp * scaledDensity;
    }

    private void maybeWarnXMPP() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean shouldWarn = sp.getBoolean("xmpp_privacy_should_warn", false);
        if (shouldWarn) {
            JuickPreferencesActivity.launchXMPPPrivacyDialog(this, true);
        }
        //To change body of created methods use File | Settings | File Templates.
    }

    public static String updateAvailable;

    private boolean maybeLaunchIntent(Intent intent, boolean shouldFinish) {
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null && uri.getPathSegments().size() > 0 && parseUri(uri, shouldFinish)) {
                return true;
            }
        }
        return false;
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

    public static final int NAVITEM_UNREAD = 1001;
    public static final int NAVITEM_SAVED = 1002;



    public void updateNavigation() {
        navigationItems = new ArrayList<NavigationItem>();

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
        navigationItems.add(new NavigationItem(NAVITEM_UNREAD, R.string.navigationUnread, R.drawable.navicon_juickadvanced, "msrcUnread") {
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
                                        if (getSelectedNavigationIndex() != myIndex) {
                                            setSelectedNavigationItem(myIndex);
                                        }
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
        navigationItems.add(new NavigationItem(NAVITEM_SAVED, R.string.navigationSaved, R.drawable.navicon_juickadvanced,"msrcSaved") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                args.putSerializable("messagesSource", new SavedMessagesSource(MainActivity.this));
                runDefaultFragmentWithBundle(args, this);
            }
        });
        int index = 10000;
        final SharedPreferences sp_order = getSharedPreferences("messages_source_ordering", MODE_PRIVATE);
        for (NavigationItem navigationItem : navigationItems) {
            navigationItem.itemOrder = sp_order.getInt("order_" + navigationItem.id, -1);
            if (navigationItem.itemOrder == -1) {
                navigationItem.itemOrder = index;
                sp_order.edit().putInt("order_" + navigationItem.id, navigationItem.itemOrder).commit();
            }
            index++;
        }
        Collections.sort(navigationItems, new Comparator<NavigationItem>() {
            @Override
            public int compare(NavigationItem lhs, NavigationItem rhs) {
                return lhs.itemOrder - rhs.itemOrder;       // increasing
            }
        });
        allNavigationItems = new ArrayList<NavigationItem>(navigationItems);
        final Iterator<NavigationItem> iterator = navigationItems.iterator();
        while (iterator.hasNext()) {
            NavigationItem next = iterator.next();
            if (next.sharedPrefsKey != null) {
                if (!sp.getBoolean(next.sharedPrefsKey, defaultValues(next.sharedPrefsKey))) {
                    iterator.remove();
                }
            }
        }
        sp_order.edit().commit();   // save

        final boolean compressedMenu = sp.getBoolean("compressedMenu", false);
        float menuFontScale = 1;
        try {
            menuFontScale = Float.parseFloat(sp.getString("menuFontScale", "1.0"));
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        final float finalMenuFontScale = menuFontScale;
        final DragSortListView navigationList = (DragSortListView)findViewById(R.id.navigation_list);

        // adapter for old-style navigation
        final BaseAdapter navigationAdapter = new BaseAdapter() {
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
                if (position == -1) {
                    // NOOK is funny
                    return convertView;
                }
                final int screenHeight = getWindow().getWindowManager().getDefaultDisplay().getHeight();
                final int layoutId = R.layout.simple_list_item_1_mine;
                final PressableLinearLayout retval = convertView != null ?
                        (PressableLinearLayout)convertView
                        : (PressableLinearLayout)getLayoutInflater().inflate(layoutId, null);
                TextView tv = (TextView) retval.findViewById(android.R.id.text1);
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
                retval.setPressedListener(new PressableLinearLayout.PressedListener() {
                    @Override
                    public void onPressStateChanged(boolean selected) {
                        MainActivity.restyleChildrenOrWidget(retval, false);
                    }

                    @Override
                    public void onSelectStateChanged(boolean selected) {
                        MainActivity.restyleChildrenOrWidget(retval, false);
                    }
                });
                MainActivity.restyleChildrenOrWidget(retval, false);
                return retval;
            }
        };
        // adapter for new-style navigation
        final BaseAdapter navigationListAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return getItems().size();
            }

            private ArrayList<NavigationItem> getItems() {
                if (navigationList.isDragEnabled()) {
                    return allNavigationItems;
                } else {
                    return navigationItems;
                }
            }

            @Override
            public Object getItem(int position) {
                return getItems().get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            float textSize = -1;

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (position == -1) {
                    // NOOK is funny
                    return convertView;
                }
                if (textSize < 0) {
                    View inflate = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                    TextView text = (TextView)inflate.findViewById(android.R.id.text1);
                    textSize = text.getTextSize();
                }
                final int layoutId = R.layout.navigation_list_item;
                final PressableLinearLayout retval = convertView != null ?
                        (PressableLinearLayout)convertView
                        : (PressableLinearLayout)getLayoutInflater().inflate(layoutId, null);
                TextView tv = (TextView) retval.findViewById(android.R.id.text1);
                final ArrayList<NavigationItem> items = getItems();
                final NavigationItem theItem = items.get(position);
                tv.setText(getString(theItem.labelId));
                ImageView iv = (ImageView) retval.findViewById(android.R.id.icon);
                iv.setImageResource(theItem.imageId);
                retval.findViewById(R.id.draggable).setVisibility(items != allNavigationItems ? View.GONE : View.VISIBLE);
                retval.findViewById(R.id.checkbox).setVisibility(items != allNavigationItems || theItem.sharedPrefsKey == null ? View.GONE: View.VISIBLE);
                CheckBox cb = (CheckBox)retval.findViewById(R.id.checkbox);
                cb.setOnCheckedChangeListener(null);
                if (theItem.sharedPrefsKey != null) {
                    cb.setChecked(sp.getBoolean(theItem.sharedPrefsKey, defaultValues(theItem.sharedPrefsKey)));
                }
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        sp.edit().putBoolean(theItem.sharedPrefsKey, isChecked).commit();
                    }
                });
                int spacing = sp.getInt("navigation_spacing", 0);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize + spacing);
                retval.setPadding(0, spacing, 0, spacing);
                return retval;
            }
        };
        ActionBar bar = getSupportActionBar();
        updateActionBarMode();
        navigationList.setDragEnabled(false);
        ((DragSortController)navigationList.getFloatViewManager()).setDragHandleId(R.id.draggable);
        navigationList.setAdapter(navigationListAdapter);
        navigationList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                setSelectedNavigationItem(position);
                closeNavigationMenu(true);
            }
        });
        navigationList.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                final NavigationItem item = allNavigationItems.remove(from);
                allNavigationItems.add(to, item);

                int index = 0;
                for (NavigationItem allNavigationItem : allNavigationItems) {
                    if (allNavigationItem.itemOrder != index) {
                        allNavigationItem.itemOrder = index;
                        sp_order.edit().putInt("order_" + allNavigationItem.id, allNavigationItem.itemOrder).commit();
                    }
                    index++;
                }
                sp_order.edit().commit();   // save
                navigationListAdapter.notifyDataSetChanged();
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        bar.setListNavigationCallbacks(navigationAdapter, this);
        final View navigationMenuButton = (View) findViewById(R.id.navigation_menu_button);
        navigationMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setItems(navigationList.isDragEnabled() ? R.array.navigation_menu_end : R.array.navigation_menu_start, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int spacing = sp.getInt("navigation_spacing", 0);
                                switch(which) {
                                    case 0:
                                        if (navigationList.isDragEnabled()) {
                                            navigationList.setDragEnabled(false);
                                            updateNavigation();
                                        } else {
                                            navigationList.setDragEnabled(true);
                                        }
                                        break;
                                    case 1:
                                        final Map<String, ?> all = sp_order.getAll();
                                        for (String s : all.keySet()) {
                                            sp_order.edit().remove(s).commit();
                                        }
                                        sp_order.edit().commit();
                                        updateNavigation();
                                        break;
                                    case 2:     // wider
                                        sp.edit().putInt("navigation_spacing", spacing+1).commit();
                                        ((BaseAdapter)navigationList.getAdapter()).notifyDataSetInvalidated();
                                        break;
                                    case 3:     // narrower
                                        if (spacing > 0) {
                                            sp.edit().putInt("navigation_spacing", spacing-1).commit();
                                            ((BaseAdapter)navigationList.getAdapter()).notifyDataSetInvalidated();
                                        }
                                        break;

                                }
                                navigationListAdapter.notifyDataSetChanged();
                            }
                        })
                        .create().show();
            }
        });

        final SharedPreferences spn = getSharedPreferences("saved_last_navigation_type", MODE_PRIVATE);
        int restoredLastNavItem = spn.getInt("last_navigation", 0);
        if (restoredLastNavItem != 0) {
            for (int i = 0; i < navigationItems.size(); i++) {
                NavigationItem navigationItem = navigationItems.get(i);
                if (navigationItem.labelId == restoredLastNavItem) {
                    lastNavigationItem = navigationItem;
                    break;
                }
            }
            if (lastNavigationItem != null) {
                restoreLastNavigationPosition();
                lastNavigationItem.action();
            }
        }


    }

    private boolean defaultValues(String sharedPrefsKey) {
        if (sharedPrefsKey.equals("msrcTopMessages")) return true;
        if (sharedPrefsKey.equals("msrcWithPhotos")) return true;
        return false;
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
        XMPPService.log("MainActivity.destroy()");
        nActiveMainActivities--;
        saveState(true);
        super.onDestroy();
        if (sp != null)
            sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void saveState(boolean urgent) {
        if (mf != null) {
            if (lastNavigationItem != null) {
                MessageListBackingData messageListBackingData = mf.getMessageListBackingData();
                if (messageListBackingData != null) {
                    messageListBackingData.navigationItemLabelId = lastNavigationItem.labelId;
                    JuickAdvancedApplication.instance.setSavedList(messageListBackingData, urgent);
                }
            }
        }
    }

    public static void toggleXMPP(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean useXMPP = sp.getBoolean("useXMPP", false);
        if (useXMPP) {
            ctx.startService(new Intent(ctx, XMPPService.class));
        } else {
            if (isXMPPServiceRunning(ctx)) {
                Intent service = new Intent(ctx, XMPPService.class);
                service.putExtra("terminate", true);
                ctx.startService(service);
            }
        }
    }

    private void toggleJAMessaging() {
        boolean useJAM = sp.getBoolean("enableJAMessaging", false);
        toggleJAMessaging(this, useJAM);
    }

    public static boolean commandJAMService(Context ctx, String command) {
        if (isJAMServiceRunning(ctx)) {
            Intent service = new Intent(ctx, JAMService.class);
            String[] split = command.split(":");
            if (split.length == 1) {
                service.putExtra(command, true);
            } else {
                service.putExtra(split[0], split[1]);
            }
            ctx.startService(service);
            return true;
        } else {
            return false;
        }
    }
    public static void toggleJAMessaging(Context ctx, boolean useJAM) {
        if (isJAMServiceRunning(ctx) && useJAM) {
            return; // already
        }
        XMPPService.log("MainActivity.toggleJAMessaging("+useJAM+")");
        if (useJAM) {
            JuickAdvancedApplication.showXMPPToast("toggleJAMessaging: " + useJAM);
            ctx.startService(new Intent(ctx, JAMService.class));
        } else {
            if (isJAMServiceRunning(ctx)) {
                JuickAdvancedApplication.showXMPPToast("toggleJAMessaging: " + useJAM);
                Intent service = new Intent(ctx, JAMService.class);
                service.putExtra("terminate", true);
                ctx.startService(service);
            }
        }
    }

    static boolean isXMPPServiceRunning(Context ctx) {
        ActivityManager manager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        String className = XMPPService.class.getName();
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (className.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isJAMServiceRunning(Context ctx) {
        ActivityManager manager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        String className = JAMService.class.getName();
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo service : runningServices) {
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
        MessageListBackingData savedList = JuickAdvancedApplication.instance.getSavedList(this);
        if (savedList != null) {
            if (thisItem.labelId != savedList.navigationItemLabelId) {
                JuickAdvancedApplication.instance.setSavedList(null, false);
            }
        }
        thisItem.action();
        return true;
    }

    public boolean restoreLastNavigationPosition() {
        for (int i = 0; i < navigationItems.size(); i++) {
            NavigationItem navigationItem = navigationItems.get(i);
            if (navigationItem == lastNavigationItem) {
                if (getSelectedNavigationIndex() != i) {
                    setSelectedNavigationItem(i);
                    updateNavigationBarTitle();
                    return true;
                }
            }
        }
        return false;
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
                initStage2();
            } else {
                finish();
            }
        } else if (requestCode == ACTIVITY_PREFERENCES) {
            if (resultCode == RESULT_OK) {
//                Intent intent = getIntent();
//                finish();
//                startActivity(intent);
//
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
        if (item == null) return true;
        switch (item.getItemId()) {
            case R.id.menuitem_preferences:
                Intent prefsIntent = new Intent(this, NewJuickPreferenceActivity.class);
                prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.TOP_LEVEL.name());
                startActivityForResult(prefsIntent, ACTIVITY_PREFERENCES);
                return true;
            case R.id.menuitem_newmessage:
                if (mf != null) {
                    Intent intent1 = new Intent(this, NewMessageActivity.class);
                    intent1.putExtra("messagesSource", mf.messagesSource);
                    startActivity(intent1);
                    return true;
                }
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
    public void doReload() {
        if (System.currentTimeMillis() - lastReload < 1000) return;
        if (resumed) {
            if (lastNavigationItem != null) {
                JuickAdvancedApplication.instance.setSavedList(null, false);
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

    private boolean parseUri(Uri uri, boolean shouldFinish) {
        List<String> segs = uri.getPathSegments();
        if (uri.getHost().contains("juick.com")) {
            if ((segs.size() == 1 && segs.get(0).matches("\\A[0-9]+\\z"))
                    || (segs.size() == 2 && segs.get(1).matches("\\A[0-9]+\\z") && !segs.get(0).equals("places"))) {
                int mid = Integer.parseInt(segs.get(segs.size() - 1));
                if (mid > 0) {
                    if (shouldFinish) finish();
                    Intent intent = new Intent(this, ThreadActivity.class);
                    intent.setData(null);
                    intent.putExtra("mid", new JuickMessageID(mid));
                    intent.putExtra("messagesSource", new JuickCompatibleURLMessagesSource(this, "mid_from_url"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    return true;
                }
            } else if (segs.size() == 1 && segs.get(0).matches("\\A[a-zA-Z0-9\\-]+\\z")) {
                //TODO show user
            }
        }
        if (uri.getHost().contains("psto.net")) {
            String[] hostPart = uri.getHost().split("\\.");
            if (hostPart.length == 2) {
                // open main page
                if (shouldFinish) finish();
                Intent intent = new Intent(this, MessagesActivity.class);
                intent.setData(null);
                intent.putExtra("messagesSource", new PstoCompatibleMessagesSource(this, "main", "PSTO Main","http://psto.net/recent"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            if (hostPart.length == 3 && segs.size() == 1) { // http://abc.psto.net/klmnop
                // open thread
                if (shouldFinish) finish();
                Intent intent = new Intent(this, ThreadActivity.class);
                intent.setData(null);
                PstoMessageID mid = new PstoMessageID(hostPart[0], segs.get(0));
                intent.putExtra("mid", mid);
                intent.putExtra("messagesSource", new PstoCompatibleMessagesSource(this, "direct_mid", getString(R.string.navigationPSTORecent),"http://psto.net/recent"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
        if (uri.getHost().contains("bnw.im")) {
            String[] hostPart = uri.getHost().split("\\.");
            if (hostPart.length == 2 && segs.size() == 2 && segs.get(0).equals("p")) { // http://bnw.im/p/KLMNOP
                // open thread
                if (shouldFinish) finish();
                Intent intent = new Intent(this, ThreadActivity.class);
                intent.setData(null);
                BnwMessageID mid = new BnwMessageID(segs.get(1));
                intent.putExtra("mid", mid);
                intent.putExtra("messagesSource", new BnwCompatibleMessagesSource(this, "direct_mid", getString(R.string.navigationBNWAll),"/show"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }

        }
        return false;
    }

    public static Runnable installerOnResume;

    @Override
    protected void onResume() {
        restyle();
        resumed = true;
        super.onResume();
        if (reloadOnResume) {
            reloadOnResume = false;
            doReload();
        }
        if (installerOnResume != null) {
            try {
                installerOnResume.run();
            } finally {
                installerOnResume = null;
            }
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
        restyleChildrenOrWidget(view, false);
    }

    public static void restyleChildrenOrWidget(View view, boolean dontBackground) {
        if (view == null) return;
        ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(view.getContext());
        boolean pressed = view.isPressed();
        boolean selected = view.isSelected();
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
                if ("shouldRecolor".equals(scan.getTag())) {
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
                restyleViewGroup((Spinner) view, colorTheme, pressed, selected, dontBackground);
        } else if (view instanceof Button) {
//            Button btn = (Button) view;
//            btn.setTextColor(colorTheme.getForeground(pressed));
//            btn.setBackgroundColor(colorTheme.getButtonBackground());
        } else if (view instanceof TextView) {
            TextView text = (TextView) view;
            final int id = text.getId();
            if (id != R.id.old_title && id != R.id.gotoMain) // keep it authentic
                text.setTextColor(colorTheme.getForeground(pressed));
        } else if (view instanceof ViewGroup) {
            restyleViewGroup((ViewGroup) view, colorTheme, pressed, selected, dontBackground);
        }
    }

    private static void restyleViewGroup(ViewGroup view, ColorsTheme.ColorTheme colorTheme, boolean pressed, boolean selected, boolean dontBackground) {
        ViewGroup parent = view;
        if (parent.getId() == R.id.navigation_panel)
            return;
        int childCount = parent.getChildCount();
        int background = colorTheme.getBackground(pressed);
        int foreground = colorTheme.getForeground(pressed);
        if (selected) {
            background  = calculatePressedBackground(background, foreground);
        }
        if (!dontBackground) {
            boolean skipDraw = false;
            if ((view instanceof LinearLayout || view instanceof FrameLayout || view instanceof RelativeLayout)) {
                Context context = view.getContext();
                if (context instanceof MainActivity || context instanceof MessagesActivity || context instanceof ThreadActivity) {
                    // no unneeded background in given scrolling activities
                    skipDraw = true;
                    if (view.getBackground() != null) {
                        // but enable for layouts that with color
                        skipDraw = false;
                    }
                    if (!skipDraw && view.getClass().getName().toLowerCase().contains("decorview")) {
                        // given activities manage themselves
                        skipDraw = true;
                    }
                }
            }
            if (!skipDraw)
                parent.setBackgroundColor(background);
        }
        if (view instanceof ListView)
            dontBackground = true;
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            restyleChildrenOrWidget(child, dontBackground);
        }
    }

    private static int calculatePressedBackground(int background, int foreground) {
        int r1 = (background & 0x00FF0000) >> 16;
        int g1 = (background & 0x0000FF00) >> 8;
        int b1 = (background & 0x000000FF) >> 0;
        int r2= (foreground & 0x00FF0000) >> 16;
        int g2 = (foreground & 0x0000FF00) >> 8;
        int b2 = (foreground & 0x000000FF) >> 0;
        final double K = r1 > r2 ? 0.1 : 0.2;
        int r = r1 + (int)((r2-r1)* K);
        int g = g1 + (int)((g2-g1)* K);
        int b = b1 + (int)((b2-b1)* K);
        int newColor = 0xFF000000 + (r << 16) + (g << 8) + b;
        return newColor;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s == null) return;
        if (s.equals("useXMPP")) {
            toggleXMPP(this);
        }
        if (s.equals("enableJAMessaging")) {
            toggleJAMessaging();
        }
        if (s.equals("googlePlusNavigation")) {
            updateActionBarMode();
        }
        if (s.equals("enableBrowserComponent")) {
            ComponentName cn = new ComponentName("com.juickadvanced", "com.juick.android.SimpleBrowser");
            getPackageManager().setComponentEnabledSetting(cn,
                    sp.getBoolean(s, true) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        boolean dontWatchPreferences = sp.getBoolean("dontWatchPreferences", false);
        if (dontWatchPreferences) return;
        if (s.startsWith("msrc")) {
            final DragSortListView navigationList = (DragSortListView)findViewById(R.id.navigation_list);
            if (navigationList.isDragEnabled()) return; // editing is being done
            updateNavigation();
        }
        boolean invalidateRendering = false;
        if (s.startsWith("Colors.")) {
            invalidateRendering = true;
        }
        String[] refreshCauses = new String[] {
                "messagesFontScale",
                "showNumbers",
                "wrapUserpics",
                "showUserpics",
        };
        for (String refreshCause : refreshCauses) {
            if (refreshCause.equals(s)) {
                if (s.equals("messagesFontScale") && sp.getBoolean("enableScaleByGesture", true)) continue; // should have been pinch zoom
                invalidateRendering = true;
                break;
            }
        }
        if (invalidateRendering) {
            mf.listAdapter.notifyDataSetInvalidated();
        }
        if (s.equals("enableLoginNameWithCrashReport")) {
            if (sp.getBoolean("enableLoginNameWithCrashReport", false)) {
                ACRA.getErrorReporter().putCustomData("juick_user", JuickAPIAuthorizer.getJuickAccountName(this));
            } else {
                ACRA.getErrorReporter().putCustomData("juick_user", "");
            }

        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveState(false);
        super.onSaveInstanceState(outState);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

    @Override
    public void onBackPressed() {
        if (isNavigationMenuShown()) {
            closeNavigationMenu(true);
            return;
        }
        if (mf != null && mf.listAdapter != null && mf.listAdapter.imagePreviewHelper != null && mf.listAdapter.imagePreviewHelper.handleBack()) return;
        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            return super.dispatchTouchEvent(ev);
        } catch (Exception e) {
            ACRA.getErrorReporter().handleException(new RuntimeException("Handled NPE in alcatel", e), false);
            return true;
        }
    }

    @Override
    public boolean isRunning() {
        return resumed;
    }

}
