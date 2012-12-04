package com.juick.android;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;
import com.google.android.gcm.GCMRegistrar;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

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

    @Override
    public void onCreate() {
        instance = this;
        ACRA.init(this);
        super.onCreate();
        foreverHandler = new Handler();
        try {
            GCMRegistrar.checkDevice(getApplicationContext());
            GCMRegistrar.checkManifest(getApplicationContext());
            GCMRegistrar.register(getApplicationContext(), GCMIntentService.SENDER_ID);
            supportsGCM = true;
        } catch (Throwable th) {
        }
        GCMIntentService.rescheduleAlarm(this, 15);
        startService(new Intent(this, XMPPService.class));

    }

    public static void showToast(final String msg) {
        foreverHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(instance, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void showToast(final Toast toast) {
        foreverHandler.post(new Runnable() {
            @Override
            public void run() {
                toast.show();
            }
        });
    }

    public static String registrationId;

}
