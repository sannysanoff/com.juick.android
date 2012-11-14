package com.juick.android;

import android.app.Application;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.ACRAConfigurationException;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/14/12
 * Time: 11:12 AM
 * To change this template use File | Settings | File Templates.
 */

@ReportsCrashes(formKey = "dDh3SG5BSTkzSkRXV2pEUGI0cU5MVEE6MA", resDialogText = R.string.JuickAdvancedCrashed)
public class JuickAdvancedApplication extends Application {
    @Override
    public void onCreate() {
        ACRA.init(this);
//        try {
//            ACRA.getConfig().setMode(ReportingInteractionMode.DIALOG);
//        } catch (ACRAConfigurationException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
        super.onCreate();
    }
}
