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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;
import com.juick.android.datasource.JuickCompatibleURLMessagesSource;
import com.juick.android.datasource.MessagesSource;
import com.juick.android.datasource.SavedMessagesSource;
import com.juickadvanced.R;
import com.juick.android.api.JuickMessage;
import com.juickadvanced.imaging.ExtractURLFromMessage;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * @author Ugnich Anton
 */
public class JuickMessageMenu implements OnItemLongClickListener, OnClickListener {

    Activity activity;
    JuickMessage listSelectedItem;
    ArrayList<String> urls;
    ListView listView;
    JuickMessagesAdapter listAdapter;
    private MessagesSource messagesSource;

    public JuickMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        this.activity = activity;
        this.listView = listView;
        this.listAdapter = listAdapter;
        this.messagesSource = messagesSource;
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

        ArrayList<ExtractURLFromMessage.FoundURL> foundURLs = ExtractURLFromMessage.extractUrls(listSelectedItem.Text);
        for (ExtractURLFromMessage.FoundURL foundURL : foundURLs) {
            urls.add(foundURL.getUrl());
        }
        int pos = 0;
        Matcher m = JuickMessagesAdapter.msgPattern.matcher(listSelectedItem.Text);
        while (m.find(pos)) {
            urls.add(listSelectedItem.Text.substring(m.start(), m.end()));
            pos = m.end();
        }

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
                i.putExtra("messagesSource", new JuickCompatibleURLMessagesSource("@" + listSelectedItem.User.UName, activity).putArg("user_id", ""+listSelectedItem.User.UID));
                activity.startActivity(i);
            }
        });
        if (messagesSource instanceof SavedMessagesSource) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Unsave_message)) {
                @Override
                public void run() {
                    confirmAction(R.string.ReallyUnsaveMessage, new Runnable() {
                        @Override
                        public void run() {
                            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
                            databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                @Override
                                public void withService(DatabaseService service) {
                                    service.unsaveMessage(listSelectedItem);
                                    for (int i = 0; i < listAdapter.getCount(); i++) {
                                        JuickMessage jm = listAdapter.getItem(i);
                                        if (jm.MID == listSelectedItem.MID) {
                                            listAdapter.remove(jm);
                                            listAdapter.notifyDataSetInvalidated();
                                            break;
                                        }
                                    }
                                    Toast.makeText(activity, activity.getString(R.string.Message_unsaved), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                }
            });
        } else {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Save_message)) {
                @Override
                public void run() {
                    confirmAction(R.string.ReallySaveMessage, new Runnable() {
                        @Override
                        public void run() {
                            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
                            databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                @Override
                                public void withService(DatabaseService service) {
                                    service.saveMessage(listSelectedItem);
                                    Toast.makeText(activity, activity.getString(R.string.Message_saved), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                }
            });
        }
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Subscribe_to) + " @" + UName) {
            @Override
            public void run() {
                confirmAction(R.string.ReallySubscribe, new Runnable() {
                    @Override
                    public void run() {
                        postMessage("S @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Subscribed));
                    }
                });
            }
        });
        if (UName.equalsIgnoreCase(Utils.getAccountName(activity))) {
            String midrid = ""+listSelectedItem.MID;
            if (listSelectedItem.RID > 0)
                midrid += "/"+listSelectedItem.RID;
            final String finalMidrid = midrid;
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.DeleteMessage) + " #" + midrid) {
                @Override
                public void run() {
                    confirmAction(R.string.ReallyDelete, new Runnable() {
                        @Override
                        public void run() {
                            postMessage("D #" + finalMidrid, activity.getResources().getString(R.string.Deleted));
                        }
                    });
                }
            });
        }
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Subscribe_to) + " #" + listSelectedItem.MID) {
            @Override
            public void run() {
                confirmAction(R.string.ReallySubscribePost, new Runnable() {
                    @Override
                    public void run() {
                        postMessage("S #" + listSelectedItem.MID, activity.getResources().getString(R.string.Subscribed));
                    }
                });
            }
        });
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Unsubscribe_from) + " #" + listSelectedItem.MID) {
            @Override
            public void run() {
                confirmAction(R.string.ReallyUnsubscribePost, new Runnable() {
                    @Override
                    public void run() {
                        postMessage("U #" + listSelectedItem.MID, activity.getResources().getString(R.string.Unsubscribed));
                    }
                });
            }
        });
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Blacklist) + " @" + UName) {
            @Override
            public void run() {
                confirmAction(R.string.ReallyBlacklist, new Runnable() {
                    @Override
                    public void run() {
                        postMessage("BL @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Added_to_BL));
                    }
                });
            }
        });
        if (listSelectedItem.RID == 0) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Recommend_message)) {
                @Override
                public void run() {
                    confirmAction(R.string.ReallyRecommend, new Runnable() {
                        @Override
                        public void run() {
                            postMessage("! #" + listSelectedItem.MID, activity.getResources().getString(R.string.Recommended));
                        }
                    });
                }
            });
        }
        boolean add = menuActions.add(new RunnableItem(activity.getResources().getString(R.string.TranslateToRussian)) {
            @Override
            public void run() {
                String body = listSelectedItem.Text;
                translateToRussian(body, new Utils.Function<Void, String[]>() {
                    @Override
                    public Void apply(String ss[]) {
                        StringBuilder sb = new StringBuilder();
                        int successes = 0;
                        for (String s : ss) {
                            try {
                                JSONObject json = new JSONObject(s);
                                JSONArray sentences = json.getJSONArray("sentences");
                                if (sentences != null && sentences.length() > 0) {
                                    s = sentences.getJSONObject(0).getString("trans");
                                    if (s == null) {
                                        sb.append("[error]");
                                    } else {
                                        successes++;
                                        sb.append(s);
                                        sb.append("\n");
                                    }
                                } else {
                                    sb.append("[error]");
                                }
                            } catch (JSONException e) {

                            }
                        }
                        if (successes > 0) {
                            listSelectedItem.Text = sb.toString();
                            listSelectedItem.translated = true;
                            Parcelable parcelable = listView.onSaveInstanceState();
                            listView.setAdapter(listAdapter);
                            try {
                                listView.onRestoreInstanceState(parcelable);
                            } catch (Throwable e) {
                                // bad luck
                            }
                        } else {
                            Toast.makeText(activity, "Error translating..", Toast.LENGTH_LONG).show();
                        }
                        return null;
                    }
                });
            }
        });

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
                confirmAction(R.string.ReallyFilterOut, new Runnable() {
                    @Override
                    public void run() {
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
                        JuickMessagesAdapter.filteredOutUsers = null;
                        Parcelable parcelable = listView.onSaveInstanceState();
                        listView.setAdapter(listAdapter);
                        try {
                            listView.onRestoreInstanceState(parcelable);
                        } catch (Throwable e) {
                            // bad luck
                        }
                    }
                });
            }

        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final CharSequence[] items = new CharSequence[menuActions.size()];
        for (int j = 0; j < items.length; j++) {
            items[j] = menuActions.get(j).title;
        }
        builder.setItems(items, this);
        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(activity);
                ColorDrawable divider = new ColorDrawable(colorTheme.getColor(ColorsTheme.ColorKey.COMMON_BACKGROUND, 0xFFFFFFFF));
                alertDialog.getListView().setDivider(divider);
                alertDialog.getListView().setDividerHeight(1);
            }
        });
        alertDialog.show();

        final ListAdapter adapter = alertDialog.getListView().getAdapter();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        float menuFontScale = 1;
        try {
            menuFontScale = Float.parseFloat(sp.getString("menuFontScale", "1.0"));
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        final boolean compressedMenu = sp.getBoolean("compressedMenu", false);
        final boolean singleLineMenu = sp.getBoolean("singleLineMenu", false);
        final float finalMenuFontScale = menuFontScale;
        final int screenHeight = activity.getWindow().getWindowManager().getDefaultDisplay().getHeight();
        alertDialog.getListView().setAdapter(new ListAdapter() {
            @Override
            public boolean areAllItemsEnabled() {
                return adapter.areAllItemsEnabled();
            }

            @Override
            public boolean isEnabled(int position) {
                return adapter.isEnabled(position);
            }

            @Override
            public void registerDataSetObserver(DataSetObserver observer) {
                adapter.registerDataSetObserver(observer);
            }

            @Override
            public void unregisterDataSetObserver(DataSetObserver observer) {
                adapter.unregisterDataSetObserver(observer);
            }

            @Override
            public int getCount() {
                return items.length;
            }

            @Override
            public Object getItem(int position) {
                return adapter.getItem(position);
            }

            @Override
            public long getItemId(int position) {
                return adapter.getItemId(position);
            }

            @Override
            public boolean hasStableIds() {
                return adapter.hasStableIds();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View retval = adapter.getView(position, null, parent);
                if (retval instanceof TextView) {
                    TextView tv = (TextView)retval;
                    if (compressedMenu) {
                        int minHeight = (int)((screenHeight * 0.7) / getCount());
                        tv.setMinHeight(minHeight);
                        tv.setMinimumHeight(minHeight);
                    }
                    if (singleLineMenu) {
                        tv.setSingleLine(true);
                        tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    }
                    tv.setTextSize(22 * finalMenuFontScale);
                }
                return retval;
            }

            @Override
            public int getItemViewType(int position) {
                return adapter.getItemViewType(position);
            }

            @Override
            public int getViewTypeCount() {
                return adapter.getViewTypeCount();
            }

            @Override
            public boolean isEmpty() {
                return adapter.isEmpty();
            }
        });
        return true;
    }

    public void confirmAction(int resId, final Runnable r) {
        confirmAction(resId, activity, false, r);
    }

    public static void confirmAction(int resId, Context context, boolean alwaysConfirm, final Runnable r) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (sp.getBoolean("confirmActions", true) || alwaysConfirm) {
            new AlertDialog.Builder(context)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(context.getResources().getString(resId))
                    .setPositiveButton(R.string.OK, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            r.run();
                        }

                    })
                    .setNegativeButton(R.string.Cancel, null)
                    .show();
        } else {
            r.run();
        }
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
                    final Utils.RESTResponse restResponse = Utils.postJSON(activity, "http://api.juick.com/post", "body=" + URLEncoder.encode(body, "utf-8"));
                    final String ret = restResponse.getResult();
                    activity.runOnUiThread(new Runnable() {

                        public void run() {
                            Toast.makeText(activity, (ret != null) ? ok : restResponse.getErrorText(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e("postMessage", e.toString());
                }
            }
        }, "postMessage");
        thr.start();
    }

    public void translateToRussian(final String body, final Utils.Function<Void, String[]> callback) {
        final ProgressDialog mDialog = new ProgressDialog(activity);
        mDialog.setMessage("Google Translate...");
        mDialog.setCancelable(true);
        mDialog.setCanceledOnTouchOutside(true);
        final ArrayList<HttpGet> actions = new ArrayList<HttpGet>();

        final String[] split = body.split("\n");
        final String[] results = new String[split.length];
        final int[] pieces = new int[]{0};
        for (int i = 0; i < split.length; i++) {
            final String s = split[i];
            if (s.trim().length() == 0) {
                results[i] = split[i];
                synchronized (pieces) {
                    pieces[0]++;
                    if (pieces[0] == results.length) {
                        mDialog.hide();
                        callback.apply(results);
                    }
                }
            } else {
                Uri.Builder builder = new Uri.Builder().scheme("http").authority("translate.google.com").path("translate_a/t");
                builder = builder.appendQueryParameter("client", "at");
                builder = builder.appendQueryParameter("v", "2.0");
                builder = builder.appendQueryParameter("sl", "auto");
                builder = builder.appendQueryParameter("tl", "ru");
                builder = builder.appendQueryParameter("hl", "en_US");
                builder = builder.appendQueryParameter("ie", "UTF-8");
                builder = builder.appendQueryParameter("oe", "UTF-8");
                builder = builder.appendQueryParameter("inputm", "2");
                builder = builder.appendQueryParameter("source", "edit");
                builder = builder.appendQueryParameter("text", s);
                final HttpClient client = AndroidHttpClient.newInstance("AndroidTranslate/2.4.2 2.3.6 (gzip)", activity);
                final HttpGet verb = new HttpGet(builder.build().toString());
                actions.add(verb);
                verb.setHeader("Accept-Charset", "UTF-8");
                final int finalI = i;
                final Thread thread = new Thread("google translate") {
                    @Override
                    public void run() {
                        final StringBuilder out = new StringBuilder("");
                        try {
                            final String retval = client.execute(verb, new BasicResponseHandler());
                            out.setLength(0);
                            out.append(retval);
                        } catch (IOException e) {
                            Log.e("com.juickadvanced", "Error calling google translate", e);
                        } finally {
                            synchronized (pieces) {
                                pieces[0]++;
                                results[finalI] = out.toString();
                                if (pieces[0] == results.length) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mDialog.hide();
                                            callback.apply(results);
                                        }
                                    });
                                }
                            }
                        }
                    }

                };
                thread.start();
            }

        }

        mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                for (HttpGet action : actions) {
                    action.abort();
                }
                synchronized (pieces) {
                    pieces[0] = -10000;
                }
                mDialog.hide();
            }
        });
        mDialog.show();
    }

    public void encodeURIComponent() {

    }
}
