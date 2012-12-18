package com.juick.android;

import android.app.Activity;
import android.os.Handler;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 12/18/12
 * Time: 1:00 PM
 * To change this template use File | Settings | File Templates.
 */
public interface IRunningActivity {
    public Activity getActivity();
    public Handler getHandler();
    public boolean isRunning();
}
