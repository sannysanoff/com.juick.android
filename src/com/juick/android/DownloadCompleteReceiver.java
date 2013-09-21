package com.juick.android;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/29/13
 * Time: 2:55 PM
 * To change this template use File | Settings | File Templates.
 */
public class DownloadCompleteReceiver extends BroadcastReceiver {
    static HashMap<String, Long> whenDownloaded = new HashMap<String, Long>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getPackage().equals("com.juickadvanced")) {
            Long l = (Long)intent.getExtras().get("extra_download_id");
            if (l != null) {
                final DownloadManager mgr = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query q = new DownloadManager.Query();
                q = q.setFilterById(l);
                final Cursor query = mgr.query(q);
                try {
                    if (query.moveToNext()) {
                        final String localURI = query.getString(query.getColumnIndex("local_uri"));
                        final String uri = query.getString(query.getColumnIndex("uri"));
                        final Long when = whenDownloaded.get(uri);
                        if (when != null && System.currentTimeMillis() - when < 30000) {
                            // dup in 30 seconds
                        } else {
                            whenDownloaded.put(uri, System.currentTimeMillis());
                            if (localURI != null && localURI.contains("com.juickadvanced-20")) {
                                WhatsNew.installUpdate(context, localURI);
                            }
                        }
                    }
                } finally {
                    query.close();
                }
            }
        }
    }
}
