package com.juick.android.point;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.*;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.point.PointMessage;
import com.juickadvanced.data.point.PointMessageID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/10/12
 * Time: 1:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class PointMicroBlog implements MicroBlog {

    public static PointMicroBlog instance;
    public PointAuthorizer authorizer;

    public PointMicroBlog() {
        instance = this;
    }

    @Override
    public void addNavigationSources(ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity mainActivity) {
        final SharedPreferences sp = mainActivity.sp;
        navigationItems.add(new MainActivity.NavigationItem(70001, R.string.navigationPointSubs, R.drawable.navicon_point, "msrcPointSubs") {
            @Override
            public void action() {
                final MainActivity.NavigationItem thiz = this;
                runAuthorized(new Utils.Function<Void, String>() {
                    @Override
                    public Void apply(String arg) {
                        final Bundle args = new Bundle();
                        PointAPIMessagesSource ms = new PointAPIMessagesSource(mainActivity, "home", mainActivity.getString(labelId), "http://point.im/api/recent");
                        args.putSerializable("messagesSource", ms);
                        mainActivity.runDefaultFragmentWithBundle(args, thiz);
                        return null;
                    }
                }, mainActivity);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(70002, R.string.navigationPointAll, R.drawable.navicon_point, "msrcPointAll") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                PointWebCompatibleMessagesSource ms = new PointWebCompatibleMessagesSource(mainActivity, "all", mainActivity.getString(labelId), "http://point.im/all?agree=1");
                //PointAPIMessagesSource ms = new PointAPIMessagesSource(mainActivity, "all", mainActivity.getString(labelId), "http://point.im/api/all");
                args.putSerializable("messagesSource", ms);
                mainActivity.runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(70003, R.string.navigationPointMine, R.drawable.navicon_point, "msrcPointMine") {
            @Override
            public void action() {
                final MainActivity.NavigationItem thiz = this;
                runAuthorized(new Utils.Function<Void, String>() {
                    @Override
                    public Void apply(String s) {
                        final Bundle args = new Bundle();
                        final String weblogin = sp.getString("point.web_login", null);
                        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mainActivity);
                        String login = sp.getString("point.api_login", "xx");
                        PointAPIMessagesSource ms = new PointAPIMessagesSource(mainActivity, "home", mainActivity.getString(labelId), "http://point.im/api/blog/"+login);
                        //PointWebCompatibleMessagesSource ms = new PointWebCompatibleMessagesSource(mainActivity, "mine", mainActivity.getString(labelId), "http://" + weblogin + ".point.im/blog");
                        args.putSerializable("messagesSource", ms);
                        mainActivity.runDefaultFragmentWithBundle(args, thiz);
                        return null;
                    }
                }, mainActivity);
            }
        });
    }

    public void runAuthorized(final Utils.Function<Void, String> runWithLogin, final Activity mainActivity) {
        PointAuthorizer authorizer = (PointAuthorizer) Utils.getAuthorizer("http://point.im/");
        authorizer.authorize(mainActivity, true, false, "http://point.im/required", new Utils.Function<Void, String>() {
            @Override
            public Void apply(final String s) {
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (s != null) {
                            runWithLogin.apply(s);
                        } else {
                            if (mainActivity instanceof MainActivity) {
                                ((MainActivity) mainActivity).restoreLastNavigationPosition();
                            }
                        }
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void launchTagsForNewPost(final NewMessageActivity newMessageActivity) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(newMessageActivity);
        String username = sp.getString("point.web_password", null);
        if (username != null) {
            Intent i = new Intent(newMessageActivity, TagsActivity.class);
            i.setAction(Intent.ACTION_PICK);
            i.putExtra("user", username);
            i.putExtra("microblog", PointMessageID.CODE);
            i.putExtra("multi", true);
            newMessageActivity.startActivityForResult(i, NewMessageActivity.ACTIVITY_TAGS);
        }

    }


    @Override
    public void decorateNewMessageActivity(NewMessageActivity newMessageActivity) {
//        newMessageActivity.bTags.setVisibility(View.GONE);
        newMessageActivity.bLocation.setVisibility(View.GONE);
        newMessageActivity.bAttachment.setVisibility(View.GONE);
        newMessageActivity.bLocationHint.setVisibility(View.GONE);
        newMessageActivity.setTitle(R.string.Point__New_message);
        newMessageActivity.setProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void getChildren(Activity context, MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new PointWebCompatibleMessagesSource(context, "dummy", "", "http://point.im/").getChildren(mid, notifications, cont);
    }

    @Override
    public JuickMessage createMessage() {
        JuickMessage juickMessage = new PointMessage();
        juickMessage.microBlogCode = PointMessageID.CODE;
        return juickMessage;
    }


    @Override
    public void initialize() {
        authorizer = new PointAuthorizer();
        Utils.authorizers.add(0, authorizer);
    }

    @Override
    public String getCode() {
        return PointMessageID.CODE;
    }

    @Override
    public String getMicroblogName(Context context) {
        return context.getString(R.string.PointMicroblog);
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(final JuickMessage jmsg) {
        return new UserpicStorage.AvatarID() {
            @Override
            public String toString(int size) {
                return "POINT:" + jmsg.User.UName;
            }

            @Override
            public String getURL(int size) {
                return "http://i.point.im/a/40/" + jmsg.User.UName + ".jpg";
            }
        };
    }

    @Override
    public MessageID createKey(String keyString) {
        return PointMessageID.fromString(keyString);
    }

    @Override
    public MessageMenu getMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        return new PointMessageMenu(activity, messagesSource, listView, listAdapter);
    }

    @Override
    public String getPostNote(NewMessageActivity newMessageActivity) {
        return newMessageActivity.getString(R.string.Point__PostNote);
    }

    @Override
    public OperationInProgress postReply(final Activity context_, final MessageID mid, final JuickMessage threadStarter, final JuickMessage selectedReply, final String msg, String attachmentUri, String attachmentMime, final Utils.Function<Void, String> then) {
        final ThreadActivity context = (ThreadActivity) context_;
        PointMicroBlog.instance.runAuthorized(new Utils.Function<Void, String>() {
            @Override
            public Void apply(String s) {
                new Thread("Post reply") {
                    @Override
                    public void run() {
                        try {
                            PointMessageID pointMid = (PointMessageID) mid;
                            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                            final String apiLogin = sp.getString("point.api_login", null);
                            if (apiLogin == null) {
                                throw new IllegalArgumentException("No Point authorization available");
                            }
                            StringBuilder data = new StringBuilder();
                            data.append("text=" + URLEncoder.encode(msg, "utf-8"));
                            data.append("&csrf_token=" + PointAuthorizer.csrfToken);
                            int replyTo = 0;
                            if (selectedReply != null && selectedReply.getRID() != 0) {
                                data.append("&comment_id=" + (replyTo = selectedReply.getRID()));
                            }
                            final RESTResponse restResponse = Utils.postJSON(context, "http://point.im/api/post/" + pointMid.getId(), data.toString());
                            final int finalReplyTo = replyTo;
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (restResponse.getErrorText() == null) {
                                        PointMessage newmsg = new PointMessage();
                                        JuickMessagesAdapter listAdapter = context.tf.listAdapter;
                                        Object lastItem = listAdapter.getItem(listAdapter.getCount() - 1);
                                        int lastRid = 0;
                                        if (lastItem != null && lastItem instanceof PointMessage) {
                                            lastRid = ((PointMessage) lastItem).getRID();
                                        }
                                        newmsg.User = new JuickUser();
                                        newmsg.User.UName = apiLogin;
                                        newmsg.Text = msg;
                                        newmsg.Timestamp = new Date();
                                        newmsg.setRID(lastRid + 1);
                                        newmsg.setReplyTo(finalReplyTo);
                                        newmsg.microBlogCode = PointMessageID.CODE;
                                        newmsg.setMID(mid);
                                        ArrayList<JuickMessage> messages = new ArrayList<JuickMessage>();
                                        messages.add(newmsg);
                                        listAdapter.addAllMessages(messages);
                                        listAdapter.notifyDataSetChanged();

                                    }
                                    then.apply(restResponse.getErrorText());
                                }
                            });
                        } catch (final Exception e) {
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show();
                                    then.apply(e.toString());
                                }
                            });
                        }
                    }
                }.start();
                return null;
            }
        }, context);
        return new OperationInProgress() {
            @Override
            public void preliminarySuccess() {

            }
        };
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String txt, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, final Utils.Function<Void, String> then) {
        try {

            StringBuilder data = new StringBuilder();
            String s = txt = txt.trim();
            if (s.startsWith("*")) {
                String tagline = s.split("\n")[0];
                String[] tags = tagline.split("\\*");
                for (String tag : tags) {
                    String thatTag = tag.trim();
                    if (thatTag.length() > 0) {
                        if (data.length() > 0) data.append("&");
                        data.append("tag=" + Uri.encode(thatTag));
                    }
                }
                int eol = txt.indexOf("\n");
                if (eol != -1) {
                    txt = txt.substring(eol + 1);
                }
            }

            if (data.length() > 0) data.append("&");
            data.append("text=" + Uri.encode(txt));

            final RESTResponse restResponse = Utils.postJSON(newMessageActivity, "http://point.im/api/post", data.toString());
            newMessageActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    then.apply(restResponse.getErrorText());
                }
            });


        } catch (final Exception e) {
            newMessageActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    then.apply(e.toString());
                }
            });
        }
    }

    @Override
    public ThreadFragment.ThreadExternalUpdater getThreadExternalUpdater(Activity activity, MessageID mid) {
        return null;
    }

    @Override
    public int getPiority() {
        return 20;
    }

    public JSONArray getUserTags(View view, String uidS) {
        Context context = view.getContext();
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        final String weblogin = sp.getString("point.web_login", null);
        String url = "http://" + weblogin.toLowerCase() + ".point.im/tags";
        File globalTagsCache = new File(view.getContext().getCacheDir(), "tags-point-" + uidS + ".json");
        String cachedString = null;
        if (globalTagsCache.exists() && globalTagsCache.lastModified() > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) {
            cachedString = XMPPService.readFile(globalTagsCache);
        }
        final String html = cachedString != null ? cachedString : Utils.getJSON(context, url, null).getResult();
        if (html != null && cachedString == null) {
            XMPPService.writeStringToFile(globalTagsCache, html);
        }
        if (html != null) {
            JSONArray json = new JSONArray();
            Document doc = Jsoup.parse(html);
            Elements as = doc.select("div[id=content] > a[class=tag]");
            for (Element a : as) {
                String[] nameAndCount = a.attr("title").split("[: ]");
                if (nameAndCount.length == 3) {
                    try {
                        JSONObject value = new JSONObject();
                        value.put("tag", nameAndCount[0]);
                        value.put("messages", Integer.parseInt(nameAndCount[2]));
                        json.put(value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            return json;
        }
        return null;
    }
}
