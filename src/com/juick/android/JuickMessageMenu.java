/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;
import com.juick.R;
import com.juick.android.api.JuickMessage;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

/**
 *
 * @author Ugnich Anton
 */
public class JuickMessageMenu implements OnItemLongClickListener, OnClickListener {

    Activity activity;
    JuickMessage listSelectedItem;
    ArrayList<String> urls;
    ListView listView;
    JuickMessagesAdapter listAdapter;

    public JuickMessageMenu(Activity activity, ListView listView, JuickMessagesAdapter listAdapter) {
        this.activity = activity;
        this.listView = listView;
        this.listAdapter = listAdapter;
    }

    ArrayList<RunnableItem> menuActions = new ArrayList<RunnableItem>();

    static class RunnableItem implements Runnable {
        String title;

        RunnableItem(String title) {
            this.title = title;
        }

        @Override
        public void run() {
            //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    public boolean onItemLongClick(final AdapterView parent, View view, final int position, long id) {

        menuActions.clear();
        listSelectedItem = (JuickMessage) parent.getAdapter().getItem(position);

        urls = new ArrayList<String>();
        if (listSelectedItem.Photo != null) {
            urls.add(listSelectedItem.Photo);
        }
        if (listSelectedItem.Video != null) {
            urls.add(listSelectedItem.Video);
        }

        int pos = 0;
        Matcher m = JuickMessagesAdapter.urlPattern.matcher(listSelectedItem.Text);
        while (m.find(pos)) {
            urls.add(listSelectedItem.Text.substring(m.start(), m.end()));
            pos = m.end();
        }

        pos = 0;
        m = JuickMessagesAdapter.msgPattern.matcher(listSelectedItem.Text);
        while (m.find(pos)) {
            urls.add(listSelectedItem.Text.substring(m.start(), m.end()));
            pos = m.end();
        }
        /*
        pos = 0;
        m = JuickMessagesAdapter.usrPattern.matcher(listSelectedItem.Text);
        while (m.find(pos)) {
        urls.add(listSelectedItem.Text.substring(m.start(), m.end()));
        pos = m.end();
        }
         */

        if (urls.size() > 0) {
            for (final String url : urls) {
                menuActions.add(new RunnableItem(url) {
                    @Override
                    public void run() {
                        if (url.startsWith("#")) {
                            int mid = Integer.parseInt(url.substring(1));
                            if (mid > 0) {
                                Intent intent = new Intent(activity, ThreadActivity.class);
                                intent.putExtra("mid", mid);
                                activity.startActivity(intent);
                            }
                            //} else if (url.startsWith("@")) {
                        } else {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        }
                    }
                });

            }
        }
        final String UName = listSelectedItem.User.UName;
        menuActions.add(new RunnableItem('@' + UName + " " + activity.getResources().getString(R.string.blog)) {
            @Override
            public void run() {
                Intent i = new Intent(activity, MessagesActivity.class);
                i.putExtra("uid", listSelectedItem.User.UID);
                i.putExtra("uname", listSelectedItem.User.UName);
                activity.startActivity(i);
            }
        });
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Subscribe_to) + " @" + UName) {
            @Override
            public void run() {
                postMessage("S @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Subscribed));
            }
        });
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Blacklist) + " @" + UName) {
            @Override
            public void run() {
                postMessage("BL @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Added_to_BL));
            }
        });
        if (listSelectedItem.RID == 0) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Recommend_message)) {
                @Override
                public void run() {
                    postMessage("! #" + listSelectedItem.MID, activity.getResources().getString(R.string.Recommended));
                }
            });
        }
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Share)) {
            @Override
            public void run() {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, listSelectedItem.toString());
                activity.startActivity(intent);
            }
        });


        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.FilterOutUser) + " @" + UName) {
            @Override
            public void run() {

                new AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(activity.getResources().getString(R.string.ReallyFilterOut))
                        .setPositiveButton(R.string.OK, new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Set<String> filteredOutUzers = JuickMessagesAdapter.getFilteredOutUsers(activity);
                                filteredOutUzers.add(UName);
                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                                sp.edit().putString("filteredOutUsers", Utils.set2string(filteredOutUzers)).commit();
                                for (int i = 0; i < listAdapter.getCount(); i++) {
                                    JuickMessage jm = listAdapter.getItem(i);
                                    if (jm.User.UName.equals(UName)) {
                                        listAdapter.remove(jm);
                                        i--;
                                    }
                                }
                                Parcelable parcelable = listView.onSaveInstanceState();
                                listView.setAdapter(listAdapter);
                                try {
                                    listView.onRestoreInstanceState(parcelable);
                                } catch (Throwable e) {
                                    // bad luck
                                }

                            }

                        })
                        .setNegativeButton(R.string.Cancel, null)
                        .show();

            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        CharSequence[] items = new CharSequence[menuActions.size()];
        for (int j = 0; j < items.length; j++) {
            items[j] = menuActions.get(j).title;
        }
        builder.setItems(items, this);
        builder.create().show();
        return true;
    }

    public void onClick(DialogInterface dialog, int which) {
        Runnable runnable = menuActions.get(which);
        if (runnable != null)
            runnable.run();
    }

    private void postMessage(final String body, final String ok) {
        Thread thr = new Thread(new Runnable() {

            public void run() {
                try {
                    final String ret = Utils.postJSON(activity, "http://api.juick.com/post", "body=" + URLEncoder.encode(body, "utf-8"));
                    activity.runOnUiThread(new Runnable() {

                        public void run() {
                            Toast.makeText(activity, (ret != null) ? ok : activity.getResources().getString(R.string.Error), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e("postMessage", e.toString());
                }
            }
        });
        thr.start();
    }
}
