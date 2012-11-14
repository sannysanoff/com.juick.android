package com.juick.android;

import android.app.Application;
import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/14/12
 * Time: 11:12 AM
 * To change this template use File | Settings | File Templates.
 */

@ReportsCrashes(formKey = "0AulfZDKVUroUdDh3SG5BSTkzSkRXV2pEUGI0cU5MVEE")
public class JuickAdvancedApplication extends Application {
    @Override
    public void onCreate() {
        ACRA.init(this);
        super.onCreate();
    }
}
