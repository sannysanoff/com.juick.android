package com.juick.android.bnw;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.*;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
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
    public static final String CODE = "bnw";

    public BNWMicroBlog() {
        instance = this;
    }

    @Override
    public void initialize() {
        Utils.authorizers.add(0, new BnwAuthorizer());
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(final JuickMessage jmsg) {
        return new UserpicStorage.AvatarID() {
            @Override
            public String toString() {
                return "BNW:"+jmsg.User.UName;
            }

            @Override
            public String getURL() {
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
                String thrid = bmid.id;
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
    public void postNewMessage(NewMessageActivity newMessageActivity, String txt, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, Utils.Function<Void, String> then) {
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
            Utils.RESTResponse restResponse = Utils.postJSON(newMessageActivity, "http://ipv4.bnw.im/api/post?", data.toString());
            if (restResponse.getResult() != null) {
                JSONObject json = new JSONObject(restResponse.getResult());
                then.apply(json.getBoolean("ok") ? null : "error from server");
                return;
            } else {
                then.apply(restResponse.getErrorText());
                return;
            }
        } catch (Exception e) {
            then.apply(e.toString());
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
                    BnwCompatibleMessageSource ms = new BnwCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "/feed");
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
                    BnwCompatibleMessageSource ms = new BnwCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "/show");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcBNW", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationBNWHot) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    BnwCompatibleMessageSource ms = new BnwCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "/today");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
    }
}
