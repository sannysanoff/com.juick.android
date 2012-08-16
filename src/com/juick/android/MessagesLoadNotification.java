package com.juick.android;

import android.app.Activity;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.juickadvanced.R;

/**
* Created with IntelliJ IDEA.
* User: san
* Date: 8/17/12
* Time: 1:27 AM
* To change this template use File | Settings | File Templates.
*/
public class MessagesLoadNotification implements Utils.DownloadProgressNotification, Utils.RetryNotification, Utils.DownloadErrorNotification {
    int nretry = 0;
    TextView statusText;
    ProgressBar progressBar;
    Handler handler;
    String lastError;

    MessagesLoadNotification(Activity activity, Handler handler) {
        statusText = (TextView)activity.findViewById(R.id.status_text);
        progressBar = (ProgressBar)activity.findViewById(R.id.progress_bar);
        statusText.setText("Loading..");
        this.handler = handler;
    }

    @Override
    public void notifyDownloadProgress(final int progressBytes) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String str = "Loading.. " + (progressBytes/1024)+"K ";
                if (nretry>0)
                    str += " (retry "+(nretry+1)+")";
                statusText.setText(str);
            }
        });
    }

    @Override
    public void notifyDownloadError(String error) {
        lastError = error;
    }

    @Override
    public void notifyRetryIsInProgress(int retry) {
        nretry = retry;
        handler.post(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Loading.. retry="+nretry);
            }
        });
    }
}
