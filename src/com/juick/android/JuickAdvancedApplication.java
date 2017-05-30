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
import android.view.WindowManager;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.crashlytics.android.Crashlytics;
import com.google.android.gcm.GCMRegistrar;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juickadvanced.R;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.lang.ISimpleDateFormat;
import com.juickadvanced.parsers.DevJuickComMessages;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import io.fabric.sdk.android.Fabric;

import static com.juick.android.Utils.JA_API_URL;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/14/12
 * Time: 11:12 AM
 * To change this template use File | Settings | File Templates.
 */

public class JuickAdvancedApplication extends Application {

    static boolean supportsGCM = false;
    public static JuickAdvancedApplication instance;
    public static Crashlytics crashlytics = new Crashlytics();

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

    private static float lastTouchX = 0;
    private static float lastTouchY = 0;

    public static void setupTheme(Activity activity) {
        // String nativeTheme = sp.getString("nativeTheme", "default");
//        String nativeTheme = "Theme_Sherlock_Light";
//        Integer themeId = themesMap.get(nativeTheme);
//        if (themeId != null) {
//            activity.setTheme(themeId);
//        }
        boolean doit = true;
        System.out.println("Oh");
        if (activity instanceof SherlockFragmentActivity) {
            ((SherlockFragmentActivity)activity).requestWindowFeature((long)android.view.Window.FEATURE_ACTION_BAR);
            //activity.setTheme(R.style.Theme_Sherlock_JustNavigation);
        }
    }

    public static void initAuthorizers(Context ctx) {
        for (Utils.URLAuth authorizer : Utils.authorizers) {
            authorizer.maybeLoadCredentials(ctx);
        }
    }


    @Override
    public void onCreate() {
        long l = System.currentTimeMillis();

        final Fabric fabric = new Fabric.Builder(this)
                .kits(crashlytics)
                .debuggable(true)
                .build();
        Fabric.with(fabric);
        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                String className = ex.getClass().getName();
                if (className.contains("BadTokenException")) return;                        // some irrelevant callbacks
                if (className.contains("java.util.concurrent.TimeoutException")) return;    // from finalizer
                if (className.contains("No permission to modify given thread")) return;    // some webkit stuff
                previousHandler.uncaughtException(thread, ex);
            }
        });
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
            GCMRegistrar.register(getApplicationContext(), GCMIntentService.SENDER_ID, "284195356092");
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
                    final RESTResponse restResponse = Utils.postForm(instance, JA_API_URL+"/collect_log?user=" + juickAccountName + "&fsize=" + file.length() + "&postsize=" + str.length(), data);
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

    public static float getLastTouchX() {
        return lastTouchX;
    }

    public static float getLastTouchY() {
        return lastTouchY;
    }

    public static void setLastTouchCoords(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;
    }

    static {
        DevJuickComMessages.sdftz = new DevJuickComMessages.SDFTZ() {

            @Override
            public ISimpleDateFormat createSDF(String format, String lang, String country, String tz) {
                final SimpleDateFormat rv = new SimpleDateFormat(format, new Locale(lang, country));
                if (tz != null) {
                    rv.setTimeZone(TimeZone.getTimeZone(tz));
                } else {
                    System.out.println("TZ requested is null");
                }
                return new ISimpleDateFormat() {
                    @Override
                    public long parse(String str) throws IllegalArgumentException {
                        try {
                            return rv.parse(str).getTime();
                        } catch (ParseException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }

                    @Override
                    public String format(long date) {
                        return rv.format(new Date(date));
                    }
                };
            }
        };
    }
}
