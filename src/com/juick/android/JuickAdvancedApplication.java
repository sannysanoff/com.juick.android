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
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.WindowManager;
import android.widget.Toast;
import com.google.android.gcm.GCMRegistrar;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    public static final int MAX_LOG_FOR_SEND = 200000;

    static HashMap<String, Integer> themesMap = new HashMap<String, Integer>() {{
        put("Theme_Sherlock_Light",R.style.Theme_Sherlock_Light);
        put("Theme_Black",android.R.style.Theme_Black);
        put("Theme_Holo",android.R.style.Theme_Holo);
        put("Theme_Holo_Light",android.R.style.Theme_Holo_Light);
        put("Theme_Light",android.R.style.Theme_Light);
        put("Theme_Translucent",android.R.style.Theme_Translucent);
    }};
    public static Typeface dinWebPro;
    public static Typeface helvNue;
    public static Typeface helvNueBold;

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
        long l = System.currentTimeMillis();
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
        try {
            helvNue = Typeface.createFromAsset(this.getAssets(), "fonts/HelveticaNeueLTW1G-Cn.otf");
        } catch (Throwable e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        try {
            helvNueBold = Typeface.createFromAsset(this.getAssets(), "fonts/HelveticaNeueLTW1G-BdCn.otf");
        } catch (Throwable e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        GCMIntentService.rescheduleAlarm(this, ConnectivityChangeReceiver.getMaximumSleepInterval(getApplicationContext()) * 60);
        startService(new Intent(this, XMPPService.class));
        l = System.currentTimeMillis() - l;
        int currentVersionCode = 0;
        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ex) {
            //
        }
        addToGlobalLog("Application onCreate: "+l+" msec, app version: "+currentVersionCode, null);
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

    public static void maybeEnableAcceleration(Activity a) {
        try {
            if (PreferenceManager.getDefaultSharedPreferences(a).getBoolean("hardware_accelerated", true)) {
                a.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            } else {
                a.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        0);
            }
        } catch (Throwable e) {
            // missing API
        }
    }

    public static void addToGlobalLog(String message, Throwable th) {
        final Context context = JuickAdvancedApplication.instance.getApplicationContext();
        final File file = new File(context.getFilesDir(), "global.log");
        try {
            if (file.length() > 2000000) {
                file.delete();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            final FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmmssZ");
            StringBuilder sb = new StringBuilder();
            sb.append(df.format(new Date()));
            if (message != null) {
                sb.append(" "+message);
            }
            sb.append("\n");
            if (th != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final PrintWriter err = new PrintWriter(baos);
                th.printStackTrace(err);
                err.println("\n");
                err.flush();
                sb.append(baos.toString());
            }
            sb.append("\n");
            fileOutputStream.write(sb.toString().getBytes());
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    // ui thread optional
    public static void sendGlobalLog() {
        boolean uiThread = Looper.getMainLooper().getThread() == Thread.currentThread();
        final Handler handler = uiThread ? new Handler() : null;
        final String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(instance);
        final Context context = JuickAdvancedApplication.instance.getApplicationContext();
        final File file = new File(context.getFilesDir(), "global.log");
        try {
            FileInputStream fis = new FileInputStream(file);
            final byte[] arr = new byte[(int)file.length()];
            int ix = 0;
            while(ix < arr.length) {
                int rd = fis.read(arr, ix, arr.length - ix);
                if (rd < 1) {
                    break;
                }
                ix += rd;
            }
            int start = 0;
            if (ix > MAX_LOG_FOR_SEND) {
                start = ix - MAX_LOG_FOR_SEND;
                ix = MAX_LOG_FOR_SEND;
            }
            final String str = new String(arr, start, ix);
            new Thread("sending errlog") {
                @Override
                public void run() {
                    ArrayList<Utils.NameValuePair> data = new ArrayList<Utils.NameValuePair>();
                    data.add(new Utils.NameStringValuePair("file", str));
                    final Utils.RESTResponse restResponse = Utils.postForm(instance, "http://ja.ip.rt.ru:8080/api/collect_log?user=" + juickAccountName + "&fsize=" + file.length() + "&postsize=" + str.length(), data);
                    //final Utils.RESTResponse restResponse = Utils.postForm(instance, "http://192.168.1.77:8080/api/collect_log?user=" + juickAccountName + "&fsize=" + file.length() + "&postsize=" + str.length(), data);
                    if (restResponse.getErrorText() == null) {
                        file.delete();
                    }
                    if (handler != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (restResponse.getErrorText() == null) {
                                    Toast.makeText(context, "OK, Sent "+str.length()/1024+" kBytes of log", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(context, "Error sending log:"+restResponse.getErrorText(), Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }
            }.start();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
