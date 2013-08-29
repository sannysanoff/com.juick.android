package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;
import com.google.android.gcm.GCMRegistrar;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juick.android.juick.JuickMicroBlog;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import java.io.*;
import java.util.HashMap;

import static org.acra.ReportField.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/14/12
 * Time: 11:12 AM
 * To change this template use File | Settings | File Templates.
 */

@ReportsCrashes(formKey = "dDh3SG5BSTkzSkRXV2pEUGI0cU5MVEE6MA", resDialogText = R.string.JuickAdvancedCrashed, customReportContent =
                    { REPORT_ID, APP_VERSION_CODE, APP_VERSION_NAME,
            PACKAGE_NAME, FILE_PATH, PHONE_MODEL, BRAND, PRODUCT, ANDROID_VERSION, BUILD, TOTAL_MEM_SIZE,
            AVAILABLE_MEM_SIZE, CUSTOM_DATA, IS_SILENT, STACK_TRACE, INITIAL_CONFIGURATION, CRASH_CONFIGURATION,
            DISPLAY, USER_COMMENT, USER_EMAIL, USER_APP_START_DATE, USER_CRASH_DATE, DUMPSYS_MEMINFO, LOGCAT,
            INSTALLATION_ID, DEVICE_FEATURES, ENVIRONMENT, SETTINGS_SYSTEM, SETTINGS_SECURE })
public class JuickAdvancedApplication extends Application {

    static boolean supportsGCM = false;
    public static JuickAdvancedApplication instance;

    public static Handler foreverHandler;
    public static SharedPreferences sp;
    final private Object savedListLock = new Object();
    public static String version = "unknown";
    Activity currentActivity;
    JuickGCMClient juickGCMClient;

    static HashMap<String, Integer> themesMap = new HashMap<String, Integer>() {{
        put("Theme_Sherlock_Light",R.style.Theme_Sherlock_Light);
        put("Theme_Black",android.R.style.Theme_Black);
        put("Theme_Holo",android.R.style.Theme_Holo);
        put("Theme_Holo_Light",android.R.style.Theme_Holo_Light);
        put("Theme_Light",android.R.style.Theme_Light);
        put("Theme_Translucent",android.R.style.Theme_Translucent);
    }};
    public static Typeface dinWebPro;

    public static void setupTheme(Activity activity) {
        // String nativeTheme = sp.getString("nativeTheme", "default");
//        String nativeTheme = "Theme_Sherlock_Light";
//        Integer themeId = themesMap.get(nativeTheme);
//        if (themeId != null) {
//            activity.setTheme(themeId);
//        }
        boolean doit = true;
        System.out.println("Oh");
        if (activity instanceof FragmentActivity) {
            ((FragmentActivity)activity).requestWindowFeature((long)android.view.Window.FEATURE_ACTION_BAR);
            //activity.setTheme(R.style.Theme_Sherlock_JustNavigation);
        }
    }


    @Override
    public void onCreate() {
        if (instance == null)
            ACRA.init(this);
        instance = this;
        foreverHandler = new Handler();
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useXMPP = sp.getBoolean("useXMPP", false);
        boolean privacy_warned = sp.getBoolean("xmpp_privacy_warned", false);
        if (!privacy_warned && useXMPP) {
            sp.edit().putBoolean("useXMPP", false).putBoolean("xmpp_privacy_should_warn", true).commit();
        }
        super.onCreate();
        try {
            GCMRegistrar.checkDevice(getApplicationContext());
            GCMRegistrar.checkManifest(getApplicationContext());
            GCMRegistrar.register(getApplicationContext(), GCMIntentService.SENDER_ID, "314097120259");
            supportsGCM = true;
        } catch (Throwable th) {
        }
        try {
            version = ""+getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionCode;
        } catch (Exception ex) {

        }
        try {
            dinWebPro = Typeface.createFromAsset(this.getAssets(), "fonts/DINWebPro-CondensedMedium.ttf");
        } catch (Throwable e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        GCMIntentService.rescheduleAlarm(this, ConnectivityChangeReceiver.getMaximumSleepInterval(getApplicationContext()) * 60);
        startService(new Intent(this, XMPPService.class));

    }

    public void maybeStartJuickGCMClient(Context context) {
        if (juickGCMClient == null && JuickAPIAuthorizer.getJuickAccountName(context) != null) {
            juickGCMClient = new JuickGCMClient(context);
            if (sp.getBoolean("juick_gcm", false)) {
                new Thread("start juickGCMClient on startup") {
                    @Override
                    public void run() {
                        juickGCMClient.start();
                    }
                }.start();
            }
        }
    }

    public static void showXMPPToast(final String msg) {
        if (sp.getBoolean("xmpp_verbose", false)) {
            foreverHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(instance, msg, Toast.LENGTH_SHORT).show();
            }
        });
        }
    }

    public static String registrationId;

    public MessageListBackingData getSavedList(Activity context) {
        MessageListBackingData savedList = null;
        File savedListFile = getSavedListFile();
        if (savedListFile.exists()) {
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(savedListFile));
                savedList = (MessageListBackingData) objectInputStream.readObject();
                objectInputStream.close();
                if (savedList != null) {
                    savedList.messagesSource.setContext(context);
                    if (savedList.messages.size() == 0) {
                        savedList = null;
                    }
                }
            } catch (Exception e) {
                // bad luck!
            }
        }
        return savedList;
    }

    public void setSavedList(final MessageListBackingData o, final boolean urgent) {
        if (o == null) {
            getSavedListFile().delete();
        } else {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    synchronized (savedListLock) {
                        try {
                            if (!urgent) {
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                }
                            }
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(getSavedListFile()));
                            objectOutputStream.writeObject(o);
                            objectOutputStream.close();
                        } catch (Exception e) {
                            System.out.println(e);
                            //
                        }
                    }
                }
            };
            if (urgent) {
                thread.run();
            } else {
                thread.start();
            }
        }
    }

    public File getSavedListFile() {
        return new File(getCacheDir(),"savedMainList.ser");
    }

    public static void confirmAdvancedPrivacy(final MainActivity activity, final Runnable success, final Runnable refuse) {
        final boolean confirmed = sp.getBoolean("advanced_privacy_confirmed", false);
        if (!confirmed) {
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.PrivacyWarning))
                    .setMessage(activity.getString(R.string.PressOkToTrust))
                    .setNeutralButton(R.string.ReadPrivacyPolicy, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new WhatsNew(activity).showPrivacyPolicy(new Runnable() {
                                @Override
                                public void run() {
                                    confirmAdvancedPrivacy(activity, success, refuse);
                                }
                            });
                        }
                    })
                    .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            sp.edit().putBoolean("advanced_privacy_confirmed", true).commit();
                            success.run();
                        }
                    })
                    .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (refuse != null) {
                                refuse.run();
                            }
                        }
                    }).show();
        } else {
            success.run();
        }
    }

    public void setActiveActivity(Activity a) {
        currentActivity = a;
    }
}
