package com.juick.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.juickadvanced.R;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: san
 * Date: 8/11/12
 */
public class XMPPControlActivity extends Activity {

    Handler handler;

    String testClassList = "android.support.v4.accessibilityservice.AccessibilityServiceInfoCompat$AccessibilityServiceInfoStubImpl\n" +
            "android.support.v4.accessibilityservice.AccessibilityServiceInfoCompat$AccessibilityServiceInfoVersionImpl\n" +
            "android.support.v4.accessibilityservice.AccessibilityServiceInfoCompat\n" +
            "android.support.v4.accessibilityservice.AccessibilityServiceInfoCompatIcs\n" +
            "android.support.v4.widget.SearchViewCompat$SearchViewCompatImpl\n" +
            "android.support.v4.widget.EdgeEffectCompat\n" +
            "android.support.v4.widget.CursorAdapter$ChangeObserver\n" +
            "android.support.v4.widget.SearchViewCompatHoneycomb$1\n" +
            "android.support.v4.widget.CursorFilter\n" +
            "android.support.v4.widget.SearchViewCompatHoneycomb\n" +
            "android.support.v4.widget.ResourceCursorAdapter\n" +
            "android.support.v4.widget.SimpleCursorAdapter$ViewBinder\n" +
            "android.support.v4.widget.SimpleCursorAdapter$CursorToStringConverter\n" +
            "android.support.v4.widget.CursorAdapter$1\n" +
            "android.support.v4.widget.CursorAdapter$MyDataSetObserver\n" +
            "android.support.v4.widget.EdgeEffectCompat$BaseEdgeEffectImpl\n" +
            "android.support.v4.widget.SearchViewCompat$SearchViewCompatStubImpl\n" +
            "android.support.v4.widget.CursorFilter$CursorFilterClient\n" +
            "android.support.v4.widget.SimpleCursorAdapter\n" +
            "android.support.v4.widget.SearchViewCompat$SearchViewCompatHoneycombImpl\n" +
            "android.support.v4.widget.SearchViewCompat$SearchViewCompatHoneycombImpl$1\n" +
            "android.support.v4.widget.EdgeEffectCompat$EdgeEffectImpl\n" +
            "android.support.v4.widget.CursorAdapter\n" +
            "android.support.v4.widget.SearchViewCompatHoneycomb$OnQueryTextListenerCompatBridge\n" +
            "android.support.v4.widget.EdgeEffectCompatIcs\n" +
            "android.support.v4.widget.SearchViewCompat$OnQueryTextListenerCompat\n" +
            "android.support.v4.widget.SearchViewCompat\n" +
            "android.support.v4.database.DatabaseUtilsCompat\n" +
            "android.support.v4.os.ParcelableCompatCreatorCallbacks\n" +
            "android.support.v4.os.ParcelableCompat$CompatCreator\n" +
            "android.support.v4.os.ParcelableCompatCreatorHoneycombMR2Stub\n" +
            "android.support.v4.os.ParcelableCompatCreatorHoneycombMR2\n" +
            "android.support.v4.os.ParcelableCompat\n" +
            "android.support.v4.app.HCSparseArray\n" +
            "android.support.v4.app.BackStackState\n" +
            "android.support.v4.app.FragmentTransaction\n" +
            "android.support.v4.app.FragmentManagerState$1\n" +
            "android.support.v4.app.FragmentActivity$6\n" +
            "android.support.v4.app.ActionBar\n" +
            "android.support.v4.app.FragmentActivity$HoneycombInvalidateOptionsMenu\n" +
            "android.support.v4.app.DialogFragment\n" +
            "android.support.v4.app.FragmentActivity\n" +
            "android.support.v4.app.FragmentManagerState\n" +
            "android.support.v4.app.ActionBar$OnNavigationListener\n" +
            "android.support.v4.app.NoSaveStateFrameLayout\n" +
            "android.support.v4.app.ListFragment$1\n" +
            "android.support.v4.app.FragmentActivity$2\n" +
            "android.support.v4.app.ActivityCompatHoneycomb\n" +
            "android.support.v4.app.LoaderManagerImpl\n" +
            "android.support.v4.app.FragmentActivity$3\n" +
            "android.support.v4.app.FragmentManager$OnBackStackChangedListener\n" +
            "android.support.v4.app.FragmentManagerImpl$1\n" +
            "android.support.v4.app.FragmentActivity$FragmentTag\n" +
            "android.support.v4.app.ActionBar$Tab\n" +
            "android.support.v4.app.Fragment$SavedState\n" +
            "android.support.v4.app.FragmentManagerImpl$4\n" +
            "android.support.v4.app.FragmentManagerImpl$5\n" +
            "android.support.v4.app.ListFragment\n" +
            "android.support.v4.app.BackStackState$1\n" +
            "android.support.v4.app.Fragment\n" +
            "android.support.v4.app.FragmentState\n" +
            "android.support.v4.app.BackStackRecord\n" +
            "android.support.v4.app.ServiceCompat\n" +
            "android.support.v4.app.FragmentManager$BackStackEntry\n" +
            "android.support.v4.app.SuperNotCalledException\n" +
            "android.support.v4.app.SupportActivity\n" +
            "android.support.v4.app.FragmentActivity$1\n" +
            "android.support.v4.app.FragmentManagerImpl$2\n" +
            "android.support.v4.app.FragmentActivity$OverridePendingTransition\n" +
            "android.support.v4.app.Fragment$SavedState$1\n" +
            "android.support.v4.app.FragmentManagerImpl$3\n" +
            "android.support.v4.app.ActionBar$OnMenuVisibilityListener\n" +
            "android.support.v4.app.LoaderManager$LoaderCallbacks\n" +
            "android.support.v4.app.BackStackRecord$Op\n" +
            "android.support.v4.app.FragmentStatePagerAdapter\n" +
            "android.support.v4.app.FragmentPagerAdapter\n" +
            "android.support.v4.app.Fragment$InstantiationException\n" +
            "android.support.v4.app.ActionBar$TabListener\n" +
            "android.support.v4.app.FragmentActivity$HoneycombHasFeature\n" +
            "android.support.v4.app.FragmentActivity$NonConfigurationInstances\n" +
            "android.support.v4.app.SupportActivity$InternalCallbacks\n" +
            "android.support.v4.app.FragmentActivity$5\n" +
            "android.support.v4.app.FragmentState$1\n" +
            "android.support.v4.app.ListFragment$2\n" +
            "android.support.v4.app.ActionBar$LayoutParams\n" +
            "android.support.v4.app.FragmentManagerImpl\n" +
            "android.support.v4.app.LoaderManagerImpl$LoaderInfo\n" +
            "android.support.v4.app.FragmentActivity$4\n" +
            "android.support.v4.app.LoaderManager\n" +
            "android.support.v4.app.FragmentManager\n" +
            "android.support.v4.util.DebugUtils\n" +
            "android.support.v4.util.LogWriter\n" +
            "android.support.v4.util.LruCache\n" +
            "android.support.v4.util.TimeUtils\n" +
            "android.support.v4.view.PagerAdapter\n" +
            "android.support.v4.view.MotionEventCompat\n" +
            "android.support.v4.view.MenuCompat$BaseMenuVersionImpl\n" +
            "android.support.v4.view.MenuItemCompat$BaseMenuVersionImpl\n" +
            "android.support.v4.view.ViewCompatGingerbread\n" +
            "android.support.v4.view.SubMenu\n" +
            "android.support.v4.view.MenuItemCompat\n" +
            "android.support.v4.view.ViewGroupCompat\n" +
            "android.support.v4.view.ViewCompat$GBViewCompatImpl\n" +
            "android.support.v4.view.ViewConfigurationCompatFroyo\n" +
            "android.support.v4.view.ViewCompatICS\n" +
            "android.support.v4.view.ActionMode$Callback\n" +
            "android.support.v4.view.PagerTitleStrip\n" +
            "android.support.v4.view.ViewPager$LayoutParams\n" +
            "android.support.v4.view.accessibility.AccessibilityRecordCompatIcs\n" +
            "android.support.v4.view.accessibility.AccessibilityRecordCompat$AccessibilityRecordImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityManagerCompat$AccessibilityManagerStubImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityManagerCompatIcs\n" +
            "android.support.v4.view.accessibility.AccessibilityManagerCompat\n" +
            "android.support.v4.view.accessibility.AccessibilityEventCompat$AccessibilityEventVersionImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityManagerCompat$AccessibilityManagerVersionImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityNodeInfoCompatIcs\n" +
            "android.support.v4.view.accessibility.AccessibilityNodeInfoCompat$AccessibilityNodeInfoStubImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityManagerCompat$AccessibilityStateChangeListenerCompat\n" +
            "android.support.v4.view.accessibility.AccessibilityRecordCompat$AccessibilityRecordStubImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityEventCompatIcs\n" +
            "android.support.v4.view.accessibility.AccessibilityEventCompat\n" +
            "android.support.v4.view.accessibility.AccessibilityRecordCompat\n" +
            "android.support.v4.view.accessibility.AccessibilityNodeInfoCompat$AccessibilityNodeInfoImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityEventCompat$AccessibilityEventStubImpl\n" +
            "android.support.v4.view.accessibility.AccessibilityNodeInfoCompat\n" +
            "android.support.v4.view.MenuCompat$MenuVersionImpl\n" +
            "android.support.v4.view.MotionEventCompat$MotionEventVersionImpl\n" +
            "android.support.v4.view.PagerTitleStrip$PageListener\n" +
            "android.support.v4.view.KeyEventCompat$BaseKeyEventVersionImpl\n" +
            "android.support.v4.view.AccessibilityDelegateCompatIcs\n" +
            "android.support.v4.view.MenuCompat\n" +
            "android.support.v4.view.VelocityTrackerCompat\n" +
            "android.support.v4.view.MenuItem$OnMenuItemClickListener\n" +
            "android.support.v4.view.ViewPager$ItemInfo\n" +
            "android.support.v4.view.ViewConfigurationCompat\n" +
            "android.support.v4.view.KeyEventCompat$KeyEventVersionImpl\n" +
            "android.support.v4.view.MenuItemCompat$MenuVersionImpl\n" +
            "android.support.v4.view.MenuItem\n" +
            "android.support.v4.view.VelocityTrackerCompat$BaseVelocityTrackerVersionImpl\n" +
            "android.support.v4.view.MotionEventCompatEclair\n" +
            "android.support.v4.view.VelocityTrackerCompat$VelocityTrackerVersionImpl\n" +
            "android.support.v4.view.ViewCompat\n" +
            "android.support.v4.view.MenuItemCompat$HoneycombMenuVersionImpl\n" +
            "android.support.v4.view.Window\n" +
            "android.support.v4.view.VelocityTrackerCompatHoneycomb\n" +
            "android.support.v4.view.ViewPager$OnAdapterChangeListener\n" +
            "android.support.v4.view.ViewPager$OnPageChangeListener\n" +
            "android.support.v4.view.MenuItemCompatHoneycomb\n" +
            "android.support.v4.view.AccessibilityDelegateCompat$AccessibilityDelegateImpl\n" +
            "android.support.v4.view.KeyEventCompat\n" +
            "android.support.v4.view.PagerTitleStrip$1\n" +
            "android.support.v4.view.ViewPager$2\n" +
            "android.support.v4.view.ViewConfigurationCompat$ViewConfigurationVersionImpl\n" +
            "android.support.v4.view.ActionMode\n" +
            "android.support.v4.view.KeyEventCompatHoneycomb\n" +
            "android.support.v4.view.MotionEventCompat$EclairMotionEventVersionImpl\n" +
            "android.support.v4.view.ViewGroupCompat$ViewGroupCompatImpl\n" +
            "android.support.v4.view.AccessibilityDelegateCompat$AccessibilityDelegateStubImpl\n" +
            "android.support.v4.view.MotionEventCompat$BaseMotionEventVersionImpl\n" +
            "android.support.v4.view.ViewCompat$BaseViewCompatImpl\n" +
            "android.support.v4.view.ViewPager$Decor\n" +
            "android.support.v4.view.ViewGroupCompatIcs\n" +
            "android.support.v4.view.Menu\n" +
            "android.support.v4.view.ViewGroupCompat$ViewGroupCompatStubImpl\n" +
            "android.support.v4.view.MenuCompat$HoneycombMenuVersionImpl\n" +
            "android.support.v4.view.ViewPager$SavedState\n" +
            "android.support.v4.view.ViewPager$PagerObserver\n" +
            "android.support.v4.view.VelocityTrackerCompat$HoneycombVelocityTrackerVersionImpl\n" +
            "android.support.v4.view.ViewConfigurationCompat$BaseViewConfigurationVersionImpl\n" +
            "android.support.v4.view.AccessibilityDelegateCompat\n" +
            "android.support.v4.view.ViewConfigurationCompat$FroyoViewConfigurationVersionImpl\n" +
            "android.support.v4.view.ViewCompat$ViewCompatImpl\n" +
            "android.support.v4.view.ViewPager$SavedState$1\n" +
            "android.support.v4.view.ViewPager$1\n" +
            "android.support.v4.view.KeyEventCompat$HoneycombKeyEventVersionImpl\n" +
            "android.support.v4.view.ViewPager$SimpleOnPageChangeListener\n" +
            "android.support.v4.view.ViewPager\n" +
            "android.support.v4.content.ModernAsyncTask$Status\n" +
            "android.support.v4.content.LocalBroadcastManager$ReceiverRecord\n" +
            "android.support.v4.content.ModernAsyncTask$1\n" +
            "android.support.v4.content.AsyncTaskLoader$LoadTask\n" +
            "android.support.v4.content.Loader$OnLoadCompleteListener\n" +
            "android.support.v4.content.ModernAsyncTask\n" +
            "android.support.v4.content.ModernAsyncTask$WorkerRunnable\n" +
            "android.support.v4.content.ModernAsyncTask$4\n" +
            "android.support.v4.content.Loader$ForceLoadContentObserver\n" +
            "android.support.v4.content.LocalBroadcastManager\n" +
            "android.support.v4.content.CursorLoader\n" +
            "android.support.v4.content.pm.ActivityInfoCompat\n" +
            "android.support.v4.content.LocalBroadcastManager$BroadcastRecord\n" +
            "android.support.v4.content.IntentCompat\n" +
            "android.support.v4.content.AsyncTaskLoader\n" +
            "android.support.v4.content.ModernAsyncTask$2\n" +
            "android.support.v4.content.ModernAsyncTask$AsyncTaskResult\n" +
            "android.support.v4.content.ModernAsyncTask$InternalHandler\n" +
            "android.support.v4.content.LocalBroadcastManager$1\n" +
            "android.support.v4.content.ModernAsyncTask$3\n" +
            "android.support.v4.content.Loader\n" +
            "com.actionbarsherlock.internal.widget.ActionBarContainer\n" +
            "com.actionbarsherlock.internal.widget.IcsLinearLayout$LayoutParams\n" +
            "com.actionbarsherlock.internal.widget.ActionBarView$1\n" +
            "com.actionbarsherlock.internal.widget.ActionBarView$2\n" +
            "com.actionbarsherlock.internal.widget.ActionBarView$TabImpl\n" +
            "com.actionbarsherlock.internal.widget.ActionBarView\n" +
            "com.actionbarsherlock.internal.widget.ActionBarView$TabImpl$1\n" +
            "com.actionbarsherlock.internal.widget.IcsLinearLayout\n" +
            "com.actionbarsherlock.internal.widget.ScrollingTextView\n" +
            "com.actionbarsherlock.internal.app.SherlockActivity\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$Impl$3\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$1\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$Impl$ActionModeWrapper\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$Impl$TabImpl\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$Impl$2\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$Impl$1\n" +
            "com.actionbarsherlock.internal.app.ActionBarImpl\n" +
            "com.actionbarsherlock.internal.app.ActionBarWrapper$Impl\n" +
            "com.actionbarsherlock.internal.view.View_OnAttachStateChangeListener\n" +
            "com.actionbarsherlock.internal.view.menu.MenuWrapper\n" +
            "com.actionbarsherlock.internal.view.menu.MenuInflaterImpl\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuView\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenu\n" +
            "com.actionbarsherlock.internal.view.menu.MenuInflaterImpl$InflatedOnMenuItemClickListener\n" +
            "com.actionbarsherlock.internal.view.menu.MenuItemImpl$1\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuView$LayoutParams\n" +
            "com.actionbarsherlock.internal.view.menu.SubMenuBuilder\n" +
            "com.actionbarsherlock.internal.view.menu.MenuItemImpl\n" +
            "com.actionbarsherlock.internal.view.menu.MenuBuilder\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter\n" +
            "com.actionbarsherlock.internal.view.menu.MenuView\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuItem\n" +
            "com.actionbarsherlock.internal.view.menu.MenuItemWrapper\n" +
            "com.actionbarsherlock.internal.view.menu.BaseMenuPresenter\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter$3\n" +
            "com.actionbarsherlock.internal.view.menu.MenuPresenter\n" +
            "com.actionbarsherlock.internal.view.menu.MenuBuilder$ItemInvoker\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuItem$1\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter$2\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuItemView\n" +
            "com.actionbarsherlock.internal.view.menu.MenuPresenter$Callback\n" +
            "com.actionbarsherlock.internal.view.menu.MenuItemWrapper$HoneycombMenuItem\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuView$ActionMenuChildView\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter$4\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter$SavedState\n" +
            "com.actionbarsherlock.internal.view.menu.MenuView$ItemView\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter$SavedState$1\n" +
            "com.actionbarsherlock.internal.view.menu.MenuInflaterWrapper\n" +
            "com.actionbarsherlock.internal.view.menu.SubMenuWrapper\n" +
            "com.actionbarsherlock.internal.view.menu.MenuInflaterImpl$MenuState\n" +
            "com.actionbarsherlock.internal.view.menu.MenuBuilder$Callback\n" +
            "com.actionbarsherlock.internal.view.menu.ActionMenuPresenter$1\n" +
            "com.actionbarsherlock.internal.view.View_HasStateListenerSupport";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        test();
        setContentView(R.layout.xmpp_control);
        final TextView xmppStatus = (TextView) findViewById(R.id.xmpp_status);
        final TextView jamStatus = (TextView) findViewById(R.id.jam_status);
        final TextView lastGCM = (TextView) findViewById(R.id.last_gcm);
        final TextView lastGCMId = (TextView) findViewById(R.id.last_gcm_id);
        final TextView lastWS = (TextView) findViewById(R.id.last_ws);
        final TextView lastWSId = (TextView) findViewById(R.id.last_ws_id);
        final TextView lastException = (TextView) findViewById(R.id.last_exception);
        final TextView messagesReceived = (TextView) findViewById(R.id.messages_received);
        final TextView alarmScheduled = (TextView) findViewById(R.id.alarm_scheduled);
        final TextView alarmFired = (TextView) findViewById(R.id.alarm_fired);
        final TextView juickbot = (TextView) findViewById(R.id.xmpp_juickbot);
        final TextView jubobot = (TextView) findViewById(R.id.xmpp_jubobot);
        final TextView juickBlacklist = (TextView) findViewById(R.id.xmpp_juickblacklist);
        final TextView juboBlacklist = (TextView) findViewById(R.id.xmpp_juboblacklist);
        final TextView lastConnect = (TextView) findViewById(R.id.jam_last_connect);
        final TextView memoryTotal = (TextView) findViewById(R.id.memory_total);
        final TextView memoryUsed = (TextView) findViewById(R.id.memory_used);
        final TextView webviewCount = (TextView) findViewById(R.id.webview_count);
        final TextView jmaCount = (TextView) findViewById(R.id.jma_count);
        final TextView infoDate = (TextView) findViewById(R.id.info_date);
        final Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        final Utils.ServiceGetter<JAMService> jamServiceServiceGetter = new Utils.ServiceGetter<JAMService>(this, JAMService.class);
        final Button retry = (Button) findViewById(R.id.retry);
        final Button showMessages = (Button) findViewById(R.id.show_messages);
        final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean("useXMPP", false).commit();
                GCMIntentService.keepAlive();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                sp.edit().putBoolean("useXMPP", true).commit();
                            }
                        });
                    }
                }.start();
            }
        });
        showMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent nintent = new Intent(XMPPControlActivity.this, XMPPIncomingMessagesActivity.class);
                startActivity(nintent);
            }
        });
        final Button gc = (Button) findViewById(R.id.gc);
        gc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        Runtime.getRuntime().gc();
                    }
                });
            }
        });
        handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Runnable thiz = this;
                boolean jamRunning = MainActivity.isJAMServiceRunning(XMPPControlActivity.this);
                JAMService jamService = JAMService.instance;
                if (jamRunning && jamService != null) {
                    StringBuilder status = new StringBuilder("svc UP, ");
                    if (jamService.client != null) {
                        if (jamService.client.wsClient != null) {
                            status.append("websock UP");
                        } else {
                            status.append("client UP, ws DOWN");
                        }
                    } else {
                        status.append("client DOWN");
                    }
                    jamStatus.setText(status.toString());
                } else {
                    jamStatus.setText("not running");
                }

                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        String status;
                        if (service.currentThread == null) {
                            status = "not running";
                        } else {
                            XMPPService.ExternalXMPPThread t = (XMPPService.ExternalXMPPThread) service.currentThread;
                            status = "JA extXMPP: Ws=" + (t.client != null && t.client.wsClient != null ? "OK" : "Down");

                        }
                        xmppStatus.setText(status);

                        lastException.setText(XMPPService.lastException != null ? XMPPService.lastException : " --- ");
                        messagesReceived.setText("" + service.messagesReceived);
                        juickbot.setText(service.botOnline ? "ONLINE" : "offline");
                        jubobot.setText(service.juboOnline ? "ONLINE" : "offline");
                        if (XMPPService.juickBlacklist != null) {
                            juickBlacklist.setText(XMPPService.juickBlacklist.info() + "; got from bot");
                        } else if (XMPPService.juickBlacklist_tmp != null) {
                            juickBlacklist.setText(XMPPService.juickBlacklist_tmp.info() + "; from cache");
                        }
                        if (XMPPService.juboMessageFilter != null) {
                            juboBlacklist.setText(XMPPService.juboMessageFilter.info() + "; got from bot");
                        }
                        if (XMPPService.juboMessageFilter_tmp != null) {
                            juboBlacklist.setText(XMPPService.juboMessageFilter_tmp.info() + "; from cache");
                        }
                        lastConnect.setText(XMPPService.lastSuccessfulConnect == 0 ? "never" : sdf.format(new Date(XMPPService.lastSuccessfulConnect)));
                        memoryTotal.setText("" + (Runtime.getRuntime().totalMemory() / 1024) + " KB");
                        memoryUsed.setText("" + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024) + " KB");
                        webviewCount.setText("" + MyWebView.instanceCount + " instances");
                        jmaCount.setText("" + JuickMessagesAdapter.instanceCount + " instances");
                        infoDate.setText("" + sdf.format(new Date()));
                        lastGCM.setText("" + (XMPPService.lastGCMMessage != null ? sdf.format(XMPPService.lastGCMMessage) : " --- "));
                        lastWS.setText("" + (XMPPService.lastWSMessage != null ? sdf.format(XMPPService.lastWSMessage) : " --- "));
                        alarmScheduled.setText(XMPPService.lastAlarmScheduled != 0 ? "" + sdf.format(new Date(XMPPService.lastAlarmScheduled)) : " --- ");
                        alarmFired.setText(XMPPService.lastAlarmFired != 0 ? "" + sdf.format(new Date(XMPPService.lastAlarmFired)) : " --- ");
                        lastGCMId.setText(XMPPService.lastGCMMessageID + " count=" + XMPPService.nGCMMessages);
                        lastWSId.setText(XMPPService.lastWSMessageID + " count=" + XMPPService.nWSMessages);
                        handler.postDelayed(thiz, 2000);
                    }
                });


            }
        }, 20);
    }

    private void test() {
        try {
            String[] classes = testClassList.split("\n");
            ClassLoader classLoader = getClass().getClassLoader();
            StringBuilder notLoaded = new StringBuilder();
            Method findLoadedClass = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            findLoadedClass.setAccessible(true);
            for (String className : classes) {
                Class clz = (Class) findLoadedClass.invoke(classLoader, className);
                if (clz == null) {
                    notLoaded.append(className);
                    notLoaded.append("\n");
                }
            }
            System.out.println(notLoaded);
        } catch (Throwable e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
