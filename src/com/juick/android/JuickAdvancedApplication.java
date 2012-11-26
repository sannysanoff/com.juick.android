package com.juick.android;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.google.android.gcm.GCMRegistrar;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.ACRAConfigurationException;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import static org.acra.ReportField.*;
import static org.acra.ReportField.SETTINGS_SECURE;
import static org.acra.ReportField.SETTINGS_SYSTEM;

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

    @Override
    public void onCreate() {
        ACRA.init(this);
        super.onCreate();
        GCMRegistrar.checkDevice(getApplicationContext());
        GCMRegistrar.checkManifest(getApplicationContext());
        GCMRegistrar.register(getApplicationContext(), GCMIntentService.SENDER_ID);
        GCMIntentService.rescheduleAlarm(this);
    }

    public static String registrationId;

}
