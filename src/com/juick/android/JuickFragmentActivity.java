package com.juick.android;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.*;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/14/13
 * Time: 12:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuickFragmentActivity extends FragmentActivity {

    public ThreadFragment tf;
    public MessagesFragment mf;
    ImagePreviewHelper imagePreviewHelper;

    static HashMap<String, Long> whenDownloaded = new HashMap<String, Long>();

    BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getPackage().equals("com.juickadvanced")) {
                Long l = (Long)intent.getExtras().get("extra_download_id");
                if (l != null) {
                    final DownloadManager mgr = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Query q = new DownloadManager.Query();
                    q = q.setFilterById(l);
                    final Cursor query = mgr.query(q);
                    if (query.moveToNext()) {
                        final String localURI = query.getString(query.getColumnIndex("local_uri"));
                        final String uri = query.getString(query.getColumnIndex("uri"));
                        final Long when = whenDownloaded.get(uri);
                        if (when != null && System.currentTimeMillis() - when < 30000) {
                            // dup in 30 seconds
                        } else {
                            whenDownloaded.put(uri, System.currentTimeMillis());
                            if (imagePreviewHelper != null) {
                                imagePreviewHelper.scheduleDownloadedImage(l, localURI);
                            } else {
                                ImagePreviewHelper.queueToDisplay.add(localURI);
                            }
                        }
                    }
                    query.close();
                }
            }
            System.out.println("OK");
        }
    };

    @Override
    protected void onResume() {
        registerReceiver(downloadCompleteReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        super.onResume();
        JuickAdvancedApplication.instance.setActiveActivity(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getBoolean("turnOffButtons", false)) {
            Window win = getWindow();
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
            win.setAttributes(winParams);
        }
    }

    @Override
    protected void onPause() {
        JuickAdvancedApplication.instance.setActiveActivity(null);
        unregisterReceiver(downloadCompleteReceiver);
        super.onPause();
    }

    public void onFragmentCreated() {
    }

    public boolean onListTouchEvent(View view, MotionEvent event) {
        //To change body of created methods use File | Settings | File Templates.
        return false;
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mf != null) {
            Boolean maybeInterceptTouchEvent = mf.maybeInterceptTouchEventFromActivity(ev);
            if (maybeInterceptTouchEvent != null) {
                return maybeInterceptTouchEvent;
            }
        }
        return super.dispatchTouchEvent(ev);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String scollMode = sp.getString("keyScrollMode", "page");
        if (!scollMode.equals("none")) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (mf != null) mf.scrollMessages(-1);
                if (tf != null) tf.scrollMessages(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (mf != null) mf.scrollMessages(+1);
                if (tf != null) tf.scrollMessages(+1);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String scollMode = sp.getString("keyScrollMode", "page");
        if (!scollMode.equals("none")) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);    //To change body of overridden methods use File | Settings | File Templates.
    }

}
