package com.juick.android;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.google.android.gcm.GCMRegistrar;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import java.io.*;

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
    static JuickAdvancedApplication instance;

    public static Handler foreverHandler;
    public static SharedPreferences sp;
    private MessageListBackingData savedList;

    @Override
    public void onCreate() {
        if (instance == null)
            ACRA.init(this);
        instance = this;
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate();
        foreverHandler = new Handler();
        try {
            GCMRegistrar.checkDevice(getApplicationContext());
            GCMRegistrar.checkManifest(getApplicationContext());
            GCMRegistrar.register(getApplicationContext(), GCMIntentService.SENDER_ID);
            supportsGCM = true;
        } catch (Throwable th) {
        }
        GCMIntentService.rescheduleAlarm(this, 15*60);
        startService(new Intent(this, XMPPService.class));

    }

    public static void showXMPPToast(final String msg) {
        if (sp.getBoolean("xmpp_verbose", false)) {
            foreverHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(instance, msg, Toast.LENGTH_LONG).show();
            }
        });
        }
    }

    public static String registrationId;

    public MessageListBackingData getSavedList() {
        if (savedList == null) {
            File savedListFile = getSavedListFile();
            if (savedListFile.exists()) {
                try {
                    ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(savedListFile));
                    savedList = (MessageListBackingData)objectInputStream.readObject();
                    objectInputStream.close();
                    if (savedList != null) {
                        savedList.messagesSource.setContext(this);
                    }
                } catch (Exception e) {
                    // bad luck!
                }
            }
        }
        return savedList;
    }

    public void setSavedList(MessageListBackingData o) {
        savedList = o;
        if (savedList == null) {
            getSavedListFile().delete();
        } else {
            try {
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(getSavedListFile()));
                objectOutputStream.writeObject(o);
                objectOutputStream.close();
            } catch (IOException e) {
                System.out.println(e);
                //
            }
        }
    }

    public File getSavedListFile() {
        return new File(getCacheDir(),"savedMainList.ser");
    }
}
