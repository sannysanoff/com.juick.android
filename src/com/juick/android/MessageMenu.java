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
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;
import com.juick.android.juick.*;
import com.juickadvanced.R;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.imaging.ExtractURLFromMessage;
import com.juickadvanced.protocol.JuickHttpAPI;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * @author Ugnich Anton
 */
public class MessageMenu implements OnItemLongClickListener, OnClickListener {

    protected Activity activity;
    protected JuickMessage listSelectedItem;
    ArrayList<String> urls;
    ListView listView;
    JuickMessagesAdapter listAdapter;
    private MessagesSource messagesSource;

    public MessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        this.activity = activity;
        this.listView = listView;
        this.listAdapter = listAdapter;
        this.messagesSource = messagesSource;
    }

    protected ArrayList<RunnableItem> menuActions = new ArrayList<RunnableItem>();

    protected static class RunnableItem implements Runnable {
        String title;

        protected RunnableItem(String title) {
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
        JuickMessage secondaryItem = null;
        float x = JuickAdvancedApplication.getLastTouchX();
        float y = JuickAdvancedApplication.getLastTouchY();
        if (listSelectedItem.contextPost != null) {
            secondaryItem = listSelectedItem.contextPost;
        }

        urls = new ArrayList<String>();
        if (listSelectedItem.Photo != null) {
            urls.add(listSelectedItem.Photo);
        }
        if (listSelectedItem.Video != null) {
            urls.add(listSelectedItem.Video);
        }

        collectURLs(listSelectedItem.Text, listSelectedItem.getMID());
        if (secondaryItem != null) {
            collectURLs(secondaryItem.Text, listSelectedItem.getMID());
        }


        if (urls.size() > 0) {
            HashSet<String> added = new HashSet<String>();
            for (final String url : urls) {
                // filter out crap
                if (url.endsWith("hqdefault.jpg") && url.contains("youtube")) continue;
                // deduplicate
                if (added.add(url)) {
                    menuActions.add(new RunnableItem(url) {
                        @Override
                        public void run() {
                            launchURL(listSelectedItem.getMID(), url);
                        }
                    });
                }
            }
        }

        collectMenuActions();

        // Censor - Submit a token for review
        TextView listSelectedTextView = (TextView) view.findViewById(R.id.text);
        int[] loc2 = new int[2];
        listSelectedTextView.getLocationOnScreen(loc2);
        int[] loc1 = new int[2];
        listView.getLocationOnScreen(loc1);
        y -= loc2[1] - loc1[1];
        x -= loc2[0] - loc1[0];
        int offset = getOffsetForPosition(listSelectedTextView, x, y);
        if (offset != -1 && false) {
            final String text = listSelectedTextView.getText().toString();
            final String word = Utils.getWordAtOffset(text, offset);
            if (word != null) {
                menuActions.add(new RunnableItem(activity.getResources().getString(R.string.CensorSubmitTokenForReview1)
                        + " \"" + word + "\" " + activity.getResources().getString(R.string.CensorSubmitTokenForReview2)) {
                    @Override
                    public void run() {
                        actionSubmitTokenForReview(word);
                    }
                });
            } else {
                Toast.makeText(activity, activity.getResources().getString(R.string.CensorSubmitWordSelectionError), Toast.LENGTH_LONG).show();
            }
        }
        runActions();
        return true;
    }

    private void collectMenuActions() {
        final String UName = listSelectedItem.User.UName;
        menuActions.add(new RunnableItem('@' + UName + " " + activity.getResources().getString(R.string.blog)) {
            @Override
            public void run() {
                actionUserBlog();
            }
        });
        if (messagesSource instanceof SavedMessagesSource) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Unsave_message)) {
                @Override
                public void run() {
                    actionUnsaveMessage();
                }
            });
        } else {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Save_message)) {
                @Override
                public void run() {
                    actionSaveMessage();
                }
            });
        }
        addMicroblogSpecificCommands(UName);
        if (UName.equalsIgnoreCase(JuickAPIAuthorizer.getJuickAccountName(activity.getApplicationContext()))) {
            maybeAddDeleteItem();
        }
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.TranslateToRussian)) {
            @Override
            public void run() {
                actionTranslateMessage();
            }
        });

        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Share)) {
            @Override
            public void run() {
                actionShareMessage();
            }
        });
        menuActions.add(new RunnableItem("Open in browser") {
            @Override
            public void run() {
                actionOpenMessageInBrowser();
            }
        });


        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.FilterOutUser) + " @" + UName) {
            @Override
            public void run() {
                actionFilterUser(UName);
            }

        });
    }

    protected void addMicroblogSpecificCommands(final String UName) {
        menuActions.add(new RunnableItem(UName + " " + activity.getResources().getString(R.string.UserCenter)) {
            @Override
            public void run() {
                actionUserCenter();
            }
        });
        if (listSelectedItem.getRID() == 0) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Subscribe_to) + " "+listSelectedItem.getDisplayMessageNo()) {
                @Override
                public void run() {
                    actionSubscribeMessage();
                }
            });
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Unsubscribe_from) + " "+listSelectedItem.getDisplayMessageNo()) {
                @Override
                public void run() {
                    actionUnsubscribeMessage();
                }
            });
        }
        if (listSelectedItem.getRID() == 0) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Recommend_message)) {
                @Override
                public void run() {
                    actionRecommendMessage();
                }
            });
        }

    }

    protected void maybeAddDeleteItem() {
        String midrid = ""+ getCurrentMIDInt();
        if (listSelectedItem.getRID() > 0)
            midrid += "/"+ listSelectedItem.getRID();
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.DeleteMessage) + " #" + midrid) {
            @Override
            public void run() {
                actionDeleteMessage();
            }
        });
    }

    private void launchURL(MessageID origMid, String url) {
        if (url.startsWith("#")) {
            MessageID newId = null;
            if (origMid instanceof JuickMessageID) {
                int mid = Integer.parseInt(url.substring(1));
                newId = new JuickMessageID(mid);
            }
            if (origMid instanceof PointMessageID) {
                newId = new PointMessageID("", url.substring(1), -1);
            }
            if (origMid instanceof BnwMessageID) {
                newId = new BnwMessageID(url.substring(1));
            }
            if (newId != null) {
                Intent intent = new Intent(activity, ThreadActivity.class);
                intent.putExtra("mid", newId);
                intent.putExtra("messagesSource", messagesSource);
                activity.startActivity(intent);
                activity.overridePendingTransition(R.anim.enter_slide_to_left, R.anim.leave_lower_and_dark);
            }
            //} else if (url.startsWith("@")) {
        } else {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(activity, "ERROR: "+e.toString(), Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void collectURLs(String source, MessageID mid) {
        if (urls == null)
            urls = new ArrayList<String>();
        ArrayList<ExtractURLFromMessage.FoundURL> foundURLs = ExtractURLFromMessage.extractUrls(source, mid);
        for (ExtractURLFromMessage.FoundURL foundURL : foundURLs) {
            urls.add(foundURL.url);
        }
        int pos = 0;
        Matcher m = JuickMessagesAdapter.getCrossReferenceMsgPattern(mid).matcher(source);
        while (m.find(pos)) {
            urls.add(source.substring(m.start(), m.end()));
            pos = m.end();
        }
    }

    protected void actionUserBlog() {
        Intent i = new Intent(activity, MessagesActivity.class);
        JuickCompatibleURLMessagesSource userblog = new JuickCompatibleURLMessagesSource("@" + listSelectedItem.User.UName, "userblog", activity);
        userblog.putArg("user_id", "" + listSelectedItem.User.UID);
        userblog.putArg("uname", "" + listSelectedItem.User.UName);
        i.putExtra("messagesSource", userblog);
        activity.startActivity(i);
    }

    protected void actionUserCenter() {
        Intent i = new Intent(activity, UserCenterActivity.class);
        i.putExtra("uname", listSelectedItem.User.UName);
        i.putExtra("microblogCode", listSelectedItem.getMID().getMicroBlogCode());
        i.putExtra("uid", listSelectedItem.User.UID);
        i.putExtra("messagesSource", messagesSource);
        i.putExtra("mid", listSelectedItem.getMID());
        activity.startActivity(i);
    }

    protected void actionSubmitTokenForReview(final String token) {
        Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
        databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
            @Override
            public void withService(DatabaseService service) {
                final ArrayList<DatabaseService.CensorCategory> censorCategories = service.getCensorCategories();
                CharSequence[] items = new CharSequence[censorCategories.size()];
                for (int i = 0; i < censorCategories.size(); i++) {
                    items[i] = censorCategories.get(i).name;
                }
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.CensorSubmitForReviewChooseSuggestedLevel)
                        .setItems(items, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final int categoryId = censorCategories.get(which).id;
                                confirmAction(R.string.CensorSubmitForReviewConfirmation, new Runnable() {
                                    @Override
                                    public void run() {
                                        Censor.getServerAdapter(activity).submitForReview(categoryId, token, new Utils.Function<Void, RESTResponse>() {
                                            @Override
                                            public Void apply(RESTResponse restResponse) {
                                                if (restResponse.getErrorText() != null) {
                                                    Toast.makeText(activity, "Censor suggest: " + restResponse.getErrorText(), Toast.LENGTH_LONG).show();
                                                } else {
                                                    Toast.makeText(activity, R.string.CensorSubmittedForReview, Toast.LENGTH_LONG).show();
                                                }
                                                return null;
                                            }
                                        });
                                    }
                                });
                            }
                        })
                        .show();
            }

            @Override
            public void withoutService() {
            }
        });
    }

    protected void actionFilterUser(final String UName) {
        confirmAction(R.string.ReallyFilterOut, new Runnable() {
            @Override
            public void run() {
                Set<String> filteredOutUzers = JuickMessagesAdapter.getFilteredOutUsers(activity);
                filteredOutUzers.add(UName);
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                sp.edit().putString("filteredOutUsers", Utils.set2string(filteredOutUzers)).commit();
                if (listAdapter != null) {
                    for (int i = 0; i < listAdapter.getCount(); i++) {
                        JuickMessage jm = listAdapter.getItem(i);
                        if (jm != null && jm.User != null && jm.User.UName != null && jm.User.UName.equals(UName)) {
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
            }
        });
    }

    private void actionShareMessage() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, listSelectedItem.toString());
        activity.startActivity(intent);
    }

    private void actionTranslateMessage() {
        String body = listSelectedItem.Text;
        translateToRussian(body, new Utils.Function<Void, String[]>() {
            @Override
            public Void apply(String ss[]) {
                StringBuilder sb = new StringBuilder();
                int successes = 0;
                for (String s : ss) {
                    if (s == null)
                        continue;
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
                                sb.append(". ");
                            }
                        } else {
                            sb.append("[error]");
                        }
                    } catch (JSONException e) {

                    }
                }
                if (successes > 0) {
                    listSelectedItem.Text = sb.toString();
                    listSelectedItem.parsedText = null;
                    listSelectedItem.translated = true;
                    //Parcelable parcelable = listView.onSaveInstanceState();
                    BaseAdapter adapter = (BaseAdapter)unwrapAdapter(listView.getAdapter());
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(activity, "Error translating..", Toast.LENGTH_LONG).show();
                }
                return null;
            }
        });
    }

    private ListAdapter unwrapAdapter(ListAdapter adapter) {
        if (adapter instanceof WrapperListAdapter) {
            return unwrapAdapter(((WrapperListAdapter)adapter).getWrappedAdapter());
        }
        return adapter;
    }

    protected void actionRecommendMessage() {
        confirmAction(R.string.ReallyRecommend, new Runnable() {
            @Override
            public void run() {
                postMessage("! #" + getCurrentMIDInt(), activity.getResources().getString(R.string.Recommended));
            }
        });
    }

    protected void actionBlacklistUser() {
        confirmAction(R.string.ReallyBlacklist, new Runnable() {
            @Override
            public void run() {
                postMessage("BL @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Added_to_BL));
            }
        });
    }

    private void actionUnsubscribeMessage() {
        confirmAction(R.string.ReallyUnsubscribePost, new Runnable() {
            @Override
            public void run() {
                postMessage("U #" + getCurrentMIDInt(), activity.getResources().getString(R.string.Unsubscribed));
                MainActivity.commandJAMService(activity, "unsubscribeMessage:"+getCurrentMIDString());
            }
        });
    }

    private void actionSubscribeMessage() {
        confirmAction(R.string.ReallySubscribePost, new Runnable() {
            @Override
            public void run() {
                postMessage("S #" + getCurrentMIDInt(), activity.getResources().getString(R.string.Subscribed));
                MainActivity.commandJAMService(activity, "subscribeMessage:"+getCurrentMIDString());
            }
        });
    }

    private void actionDeleteMessage() {
        String midrid = ""+ getCurrentMIDInt();
        if (listSelectedItem.getRID() > 0)
            midrid += "/"+ listSelectedItem.getRID();
        final String finalMidrid = midrid;
        confirmAction(R.string.ReallyDelete, new Runnable() {
            @Override
            public void run() {
                postMessage("D #" + finalMidrid, activity.getResources().getString(R.string.Deleted));
            }
        });
    }

    protected void actionSubscribeUser() {
        confirmAction(R.string.ReallySubscribe, new Runnable() {
            @Override
            public void run() {
                postMessage("S @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Subscribed));
            }
        });
    }

    protected void actionUnsubscribeUser() {
        confirmAction(R.string.ReallyUnsubscribe, new Runnable() {
            @Override
            public void run() {
                postMessage("U @" + listSelectedItem.User.UName, activity.getResources().getString(R.string.Unsubscribed));
            }
        });
    }

    private void actionSaveMessage() {
        if (listSelectedItem.getRID() != 0) {
            Toast.makeText(activity, activity.getString(R.string.CannotSaveComments), Toast.LENGTH_LONG).show();
        } else {
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
    }

    private void actionUnsaveMessage() {
        confirmAction(R.string.ReallyUnsaveMessage, new Runnable() {
            @Override
            public void run() {
                Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        service.unsaveMessage(listSelectedItem);
                        if (activity instanceof MainActivity) {
                            if (listAdapter != null) {
                                for (int i = 0; i < listAdapter.getCount(); i++) {
                                    JuickMessage jm = listAdapter.getItem(i);
                                    if (jm.getMID().equals(listSelectedItem.getMID())) {
                                        listAdapter.remove(jm);
                                        listAdapter.notifyDataSetInvalidated();
                                        break;
                                    }
                                }
                            }
                        }
                        Toast.makeText(activity, activity.getString(R.string.Message_unsaved), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    public boolean isDialogMode() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        return sp.getBoolean("dialogMessageMenu", false);
    }

    protected void runActions() {
        if (!isDialogMode()) {
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
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.Theme_Sherlock));
            View dialogView = activity.getLayoutInflater().inflate(R.layout.message_menu2, null);
            builder.setView(dialogView);
            builder.setCancelable(true);
            int width = activity.getWindowManager().getDefaultDisplay().getWidth();
            View scrollView = dialogView.findViewById(R.id.scrollView);
            scrollView.getLayoutParams().width = (int)(width * 0.90);
            final AlertDialog alertDialog = builder.create();
            alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    //MainActivity.restyleChildrenOrWidget(alertDialog.getWindow().getDecorView());
                }
            });
            TextView messageNo = (TextView)dialogView.findViewById(R.id.message_no);
            messageNo.setText(listSelectedItem.getDisplayMessageNo());
            Spinner openUrl = (Spinner)dialogView.findViewById(R.id.open_url);
            Button singleURL = (Button)dialogView.findViewById(R.id.single_url);
            if (urls != null && urls.size() == 1) {
                singleURL.setVisibility(View.VISIBLE);
                openUrl.setVisibility(View.GONE);
                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(urls.get(0));
                sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                singleURL.setText(sb);
                singleURL.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        launchURL(listSelectedItem.getMID(), urls.get(0));
                    }
                });
            } else if (urls != null && urls.size() > 0) {
                singleURL.setVisibility(View.GONE);
                openUrl.setVisibility(View.VISIBLE);
                openUrl.setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                if (position != 0) {
                                    alertDialog.dismiss();
                                    launchURL(listSelectedItem.getMID(), urls.get(position));
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                                //To change body of implemented methods use File | Settings | File Templates.
                            }
                        });
                urls.add(0, activity.getString(R.string.ClickToSelectURL));
                openUrl.setAdapter(new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return urls.size();
                    }

                    @Override
                    public Object getItem(int position) {
                        return position;
                    }

                    @Override
                    public long getItemId(int position) {
                        return position;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View rowView = activity.getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                        TextView textView = (TextView)rowView.findViewById(android.R.id.text1);
                        textView.setSingleLine(false);
                        textView.setMaxLines(5);
                        SpannableStringBuilder sb = new SpannableStringBuilder();
                        sb.append(urls.get(position));
                        if (position == 0) {
                            textView.setTextColor(0xFF808080);
                        } else {
                            sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        textView.setText(sb);
                        return rowView;
                    }
                });
            } else {
                openUrl.setVisibility(View.GONE);
                singleURL.setVisibility(View.GONE);
            }
            final String UName = listSelectedItem.User.UName;
            View recommendMessage = dialogView.findViewById(R.id.recommend_message);
            View deleteMessage = dialogView.findViewById(R.id.delete_message);
            View saveMessage = dialogView.findViewById(R.id.save_message);
            View unsaveMessage = dialogView.findViewById(R.id.unsave_message);
            //View subscribeUser = dialogView.findViewById(R.id.subscribe_user);
            View subscribeMessage = dialogView.findViewById(R.id.subscribe_message);
            //View unsubscribeUser = dialogView.findViewById(R.id.unsubscribe_user);
            View unsubscribeMessage = dialogView.findViewById(R.id.unsubscribe_message);
            View translateMessage = dialogView.findViewById(R.id.translate_message);
            View shareMessage = dialogView.findViewById(R.id.share_message);
            //View blacklistUser = dialogView.findViewById(R.id.blacklist_user);
            //View filterUser = dialogView.findViewById(R.id.filter_user);
            //View userBlog = dialogView.findViewById(R.id.user_blog);
            //View userStats = dialogView.findViewById(R.id.user_stats);
            View openMessageInBrowser = dialogView.findViewById(R.id.open_message_in_browser);
            Button userCenter = (Button)dialogView.findViewById(R.id.user_center);
            if (null == dialogView.findViewById(R.id.column_3)) {
                // only for portrait
                userCenter.setText("@"+listSelectedItem.User.UName+" "+userCenter.getText());
            }

            unsubscribeMessage.setEnabled (listSelectedItem.getRID() == 0);
            subscribeMessage.setEnabled (listSelectedItem.getRID() == 0);
            unsaveMessage.setEnabled(listSelectedItem.getRID() == 0);
            recommendMessage.setEnabled(listSelectedItem.getRID() == 0);

            if (UName.equalsIgnoreCase(JuickAPIAuthorizer.getJuickAccountName(activity.getApplicationContext()))) {
                recommendMessage.setVisibility(View.GONE);
            } else {
                deleteMessage.setVisibility(View.GONE);
            }
            if (messagesSource instanceof SavedMessagesSource) {
                saveMessage.setVisibility(View.GONE);
            } else {
                unsaveMessage.setVisibility(View.GONE);
            }
            recommendMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionRecommendMessage();
                }
            });
            deleteMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionDeleteMessage();
                }
            });
            saveMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionSaveMessage();
                }
            });
            unsaveMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionUnsaveMessage();
                }
            });
//            subscribeUser.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    alertDialog.dismiss();
//                    actionSubscribeUser();
//                }
//            });
            subscribeMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionSubscribeMessage();
                }
            });
//            unsubscribeUser.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    alertDialog.dismiss();
//                    actionUnsubscribeUser();
//                }
//            });
            unsubscribeMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionUnsubscribeMessage();
                }
            });
            translateMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionTranslateMessage();
                }
            });
            shareMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionShareMessage();
                }
            });
//            blacklistUser.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    alertDialog.dismiss();
//                    actionBlacklistUser();
//                }
//            });
//            filterUser.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    alertDialog.dismiss();
//                    actionFilterUser(UName);
//                }
//            });
//            userBlog.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    alertDialog.dismiss();
//                    actionUserBlog();
//                }
//            });
//            userStats.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    alertDialog.dismiss();
//                    actionUserStats();
//                }
//            });
            openMessageInBrowser.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionOpenMessageInBrowser();
                }
            });
            userCenter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    actionUserCenter();
                }
            });
            completeInitDialogMode(alertDialog, dialogView);
            alertDialog.show();
        }
    }

    private int getCurrentMIDInt() {
        return ((JuickMessageID)listSelectedItem.getMID()).getMid();
    }

    private String getCurrentMIDString() {
        return listSelectedItem.getMID().toString();
    }

    protected void completeInitDialogMode(AlertDialog alertDialog, View dialogView) {

    }

    protected void actionOpenMessageInBrowser() {
        int rid = listSelectedItem.getRID();
        MessageID mid = listSelectedItem.getMID();
        if (mid instanceof JuickMessageID) {
            String author = null;
            if (listAdapter != null) {
                if (rid == 0) {
                    author = listSelectedItem.User.UName;
                } else {
                    if (activity instanceof ThreadActivity) {
                        JuickMessage item = listAdapter.getItem(0);
                        author = item.User.UName;
                    }
                }
            }
            String url = "http://www.juick.com/";
            if (author != null) {
                url += author+"/";
            }
            url += ((JuickMessageID) mid).getMid();
            if (rid != 0) {
                url += "#"+rid;
            }
            launchURL(mid, url);
        }
        if (mid instanceof PointMessageID) {
            String url = "http://point.im/"+((PointMessageID) mid).getId();
            launchURL(mid, url);
        }
        if (mid instanceof BnwMessageID) {
            String url = "http://bnw.im/p/"+((BnwMessageID) mid).getId();
            launchURL(mid, url);
        }
    }

    private void actionUserStats() {

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


    protected void postMessage(final String body, final String ok) {
        Thread thr = new Thread(new Runnable() {

            public void run() {
                try {
                    final RESTResponse restResponse = Utils.postJSON(activity, JuickHttpAPI.getAPIURL() + "post", "body=" + URLEncoder.encode(body, "utf-8"));
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

        final String[] split = body.split("\\.");
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
                final HttpClient client = new DefaultHttpClient();
                // AndroidHttpClient.newInstance("AndroidTranslate/2.4.2 2.3.6 (gzip)", activity);
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
                            if (s.length() > 0)
                                out.append("[error: "+e.toString()+"]");
                            Log.e("com.juickadvanced", "Error calling google translate", e);
                        } finally {
                            client.getConnectionManager().shutdown();
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

    protected void saveFilteredOutUser(String fromUser) {
        Set<String> filteredOutUzers = JuickMessagesAdapter.getFilteredOutUsers(activity);
        filteredOutUzers.add(fromUser);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        sp.edit().putString("filteredOutUsers", Utils.set2string(filteredOutUzers)).commit();
        JuickMessagesAdapter.filteredOutUsers = null;
    }

    public int getOffsetForPosition(TextView textView, float x, float y) {
        if (textView.getLayout() == null) {
            return -1;
        }
        final int line = getLineAtCoordinate(textView, y);
        final int offset = getOffsetAtCoordinate(textView, line, x);
        return offset;
    }

    private int getOffsetAtCoordinate(TextView textView2, int line, float x) {
        x = convertToLocalHorizontalCoordinate(textView2, x);
        return textView2.getLayout().getOffsetForHorizontal(line, x);
    }

    private float convertToLocalHorizontalCoordinate(TextView textView2, float x) {
        x -= textView2.getTotalPaddingLeft();
        // Clamp the position to inside of the view.
        x = Math.max(0.0f, x);
        x = Math.min(textView2.getWidth() - textView2.getTotalPaddingRight() - 1, x);
        x += textView2.getScrollX();
        return x;
    }

    private int getLineAtCoordinate(TextView textView2, float y) {
        y -= textView2.getTotalPaddingTop();
        // Clamp the position to inside of the view.
        y = Math.max(0.0f, y);
        y = Math.min(textView2.getHeight() - textView2.getTotalPaddingBottom() - 1, y);
        y += textView2.getScrollY();
        return textView2.getLayout().getLineForVertical((int) y);
    }

}
