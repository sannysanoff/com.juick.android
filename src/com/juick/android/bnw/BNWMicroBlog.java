package com.juick.android.bnw;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.*;
import com.juickadvanced.data.bnw.BNWMessage;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:10 AM
 * To change this template use File | Settings | File Templates.
 */
public class BNWMicroBlog implements MicroBlog {

    public static BNWMicroBlog instance;

    public BNWMicroBlog() {
        instance = this;
    }

    @Override
    public void initialize() {
        Utils.authorizers.add(0, new BnwAuthorizer());
    }

    @Override
    public String getCode() {
        return BnwMessageID.CODE;
    }

    @Override
    public String getMicroblogName(Context context) {
        return context.getString(R.string.BNWMicroblog);
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(final JuickMessage jmsg) {
        return new UserpicStorage.AvatarID() {
            @Override
            public String toString(int size) {
                return "BNW:"+jmsg.User.UName;
            }

            @Override
            public String getURL(int size) {
                return "http://bnw.im/u/" + jmsg.User.UName + "/avatar/thumb";
            }
        };
    }

    @Override
    public MessageID createKey(String keyString) {
        return BnwMessageID.fromString(keyString);
    }

    @Override
    public MessageMenu getMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        return new BNWMessageMenu(activity, messagesSource, listView, listAdapter);
    }

    @Override
    public void postReply(final Activity context, final MessageID mid, final JuickMessage selectedReply, final String msg, String attachmentUri, String attachmentMime, final Utils.Function<Void, String> then) {
        new Thread("postReply") {
            @Override
            public void run() {
                BnwMessageID bmid = (BnwMessageID)mid;
                String thrid = bmid.getId();
                if (selectedReply != null) {
                    thrid += "/"+((BNWMessage)selectedReply).getRIDString();
                }
                try {
                    String encode = URLEncoder.encode(msg, "utf-8");
                    final Utils.RESTResponse restResponse = Utils.postJSON(context, "http://ipv4.bnw.im/api/comment?", "message="+thrid + "&text=" + encode);
                    context.runOnUiThread(new Runnable() {

                        public void run() {
                            try {
                                if (restResponse.getErrorText() != null) {
                                    then.apply(restResponse.getErrorText());
                                } else {
                                    JSONObject retjson = new JSONObject(restResponse.getResult());

                                    if (retjson.getBoolean("ok")) {
                                        Toast.makeText(context, retjson.getString("desc"), Toast.LENGTH_SHORT).show();
                                        then.apply(null);

                                    } else {
                                        Toast.makeText(context, retjson.getString("desc"), Toast.LENGTH_SHORT).show();
                                        then.apply(retjson.getString("desc"));
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("postComment", e.toString());
                            }
                        }
                    });
                } catch (final UnsupportedEncodingException e) {
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            then.apply(e.toString());
                        }
                    });
                }
            }
        }.start();
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String txt, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, final Utils.Function<Void, String> then) {
        try {
            int start = 0;
            int i = 0;
            StringBuilder tags = new StringBuilder();
            StringBuilder clubs = new StringBuilder();
            String text = "";
            boolean hastags = false;
            boolean hasclubs = false;
            while (start<txt.length()) {
                i=txt.indexOf(" ",start);
                if (i==-1)
                    i=txt.length();
                String word=txt.substring(start,i);
                if (i!=start) {
                    if (word.startsWith("*")) {
                        if (hastags)
                            tags.append(",");
                        else
                            hastags = true;
                        tags.append(word.substring(1));
                    } else if (word.startsWith("!")) {
                        if (hasclubs)
                            clubs.append(",");
                        else
                            hasclubs = true;
                        clubs.append(word.substring(1));
                    } else {
                        text = txt.substring(start);
                        break;
                    }
                }
                start=i+1;
            }

            StringBuilder data = new StringBuilder();
            data.append("text="+URLEncoder.encode(text, "utf-8"));
            if (hastags)
                data.append("&tags="+URLEncoder.encode(tags.toString(),"utf-8"));
            if (hasclubs)
                data.append("&clubs="+URLEncoder.encode(clubs.toString(),"utf-8"));
            final Utils.RESTResponse restResponse = Utils.postJSON(newMessageActivity, "http://ipv4.bnw.im/api/post?", data.toString());
            newMessageActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (restResponse.getResult() != null) {
                            JSONObject json = new JSONObject(restResponse.getResult());
                            then.apply(json.getBoolean("ok") ? null : "error from server");
                            return;
                        } else {
                            then.apply(restResponse.getErrorText());
                            return;
                        }
                    } catch (JSONException e) {
                        then.apply(e.toString());
                    }
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
        return 10;
    }

    @Override
    public void addNavigationSources(ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity mainActivity) {
        SharedPreferences sp = mainActivity.sp;
        if (sp.getBoolean("msrcBNWFeed", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationBNWFeed) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    BnwCompatibleMessagesSource ms = new BnwCompatibleMessagesSource(mainActivity, mainActivity.getString(labelId), "/feed");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcBNWAll", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationBNWAll) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    BnwCompatibleMessagesSource ms = new BnwCompatibleMessagesSource(mainActivity, mainActivity.getString(labelId), "/show");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcBNWHot", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationBNWHot) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    BnwCompatibleMessagesSource ms = new BnwCompatibleMessagesSource(mainActivity, mainActivity.getString(labelId), "/today");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
    }

    @Override
    public void decorateNewMessageActivity(NewMessageActivity newMessageActivity) {
        newMessageActivity.bTags.setVisibility(View.GONE);
        newMessageActivity.bLocation.setVisibility(View.GONE);
        newMessageActivity.bAttachment.setVisibility(View.GONE);
        newMessageActivity.bLocationHint.setVisibility(View.GONE);
        newMessageActivity.setTitle(R.string.BnW__New_message);
        newMessageActivity.setProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void getChildren(Activity context, MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new BnwCompatibleMessagesSource(context,"","/").getChildren(mid, notifications, cont);

    }

    @Override
    public JuickMessage createMessage() {
        JuickMessage juickMessage = new BNWMessage();
        juickMessage.microBlogCode = BnwMessageID.CODE;
        return juickMessage;
    }

}
