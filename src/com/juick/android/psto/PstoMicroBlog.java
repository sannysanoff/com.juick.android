package com.juick.android.psto;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.*;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.JuickUser;
import com.juick.android.api.MessageID;
import com.juick.android.bnw.BNWMessage;
import com.juick.android.juick.MessagesSource;
import com.juick.android.juick.SavedMessagesSource;
import com.juickadvanced.R;

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
public class PstoMicroBlog implements MicroBlog {
    public static PstoMicroBlog instance;
    public static final String CODE = "psto";

    public PstoMicroBlog() {
        instance = this;
    }


    @Override
    public void addNavigationSources(ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity mainActivity) {
        final SharedPreferences sp = mainActivity.sp;
        if (sp.getBoolean("msrcPSTOSubs", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationPSTOSubs) {
                @Override
                public void action() {
                    final MainActivity.NavigationItem thiz = this;
                    runAuthorized(new Runnable() {
                        @Override
                        public void run() {
                            final Bundle args = new Bundle();
                            final String weblogin = sp.getString("psto.web_login", null);
                            PstoCompatibleMessageSource ms = new PstoCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "http://" + weblogin + ".psto.net/subs");
                            args.putSerializable("messagesSource", ms);
                            mainActivity.runDefaultFragmentWithBundle(args, thiz);
                        }
                    }, mainActivity);
                }
            });
        }
        if (sp.getBoolean("msrcPSTOPopular", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationPSTOPopular) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    PstoCompatibleMessageSource ms = new PstoCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "http://psto.net/top");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcPSTORecent", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationPSTORecent) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    PstoCompatibleMessageSource ms = new PstoCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "http://psto.net/recent");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcPSTOMy", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationPSTOMy) {
                @Override
                public void action() {
                    final MainActivity.NavigationItem thiz = this;
                    runAuthorized(new Runnable() {
                        @Override
                        public void run() {
                            final Bundle args = new Bundle();
                            final String weblogin = sp.getString("psto.web_login", null);
                            PstoCompatibleMessageSource ms = new PstoCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "http://" + weblogin + ".psto.net/");
                            args.putSerializable("messagesSource", ms);
                            mainActivity.runDefaultFragmentWithBundle(args, thiz);
                        }
                    }, mainActivity);
                }
            });
        }
    }

    private void runAuthorized(final Runnable runWithLogin, final MainActivity mainActivity) {
        Utils.URLAuth authorizer = Utils.getAuthorizer("http://psto.net/");
        authorizer.authorize(mainActivity, true, "http://psto.net/", new Utils.Function<Void, String>() {
            @Override
            public Void apply(final String s) {
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (s != null) {
                            runWithLogin.run();
                        } else {
                            mainActivity.restoreLastNavigationPosition();
                        }
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void decorateNewMessageActivity(NewMessageActivity newMessageActivity) {
        newMessageActivity.bTags.setVisibility(View.GONE);
        newMessageActivity.bLocation.setVisibility(View.GONE);
        newMessageActivity.bAttachment.setVisibility(View.GONE);
        newMessageActivity.bLocationHint.setVisibility(View.GONE);
        newMessageActivity.setTitle(R.string.Psto__New_message);
        newMessageActivity.setProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void getChildren(Activity context, MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new PstoCompatibleMessageSource(context, "","http://psto.net/").getChildren(mid, notifications, cont);
    }

    @Override
    public JuickMessage createMessage() {
        JuickMessage juickMessage = new PstoMessage();
        juickMessage.microBlogCode = CODE;
        return juickMessage;
    }


    @Override
    public void initialize() {
        Utils.authorizers.add(0, new PstoAuthorizer());
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
                return "PSTO:"+jmsg.User.UName;
            }

            @Override
            public String getURL() {
                return "http://psto.net/img/a/40/"+jmsg.User.UName+".png";
            }
        };
    }

    @Override
    public MessageID createKey(String keyString) {
        return PstoMessageID.fromString(keyString);
    }

    @Override
    public MessageMenu getMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        return new PstoMessageMenu(activity, messagesSource, listView, listAdapter);
    }

    @Override
    public void postReply(final Activity context_, final MessageID mid, final JuickMessage selectedReply, final String msg, String attachmentUri, String attachmentMime, final Utils.Function<Void, String> then) {
        final ThreadActivity context = (ThreadActivity)context_;
        new Thread("Post reply") {
            @Override
            public void run() {
                try {
                    PstoMessageID pstoMid = (PstoMessageID) mid;
                    final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    final String webLogin = sp.getString("psto.web_login", null);
                    if (webLogin == null) {
                        throw new IllegalArgumentException("No PSTO authorization available");
                    }
                    StringBuilder data = new StringBuilder();
                    data.append("text="+ URLEncoder.encode(msg, "utf-8"));
                    int replyTo = 0;
                    if (selectedReply != null && selectedReply.getRID() != 0) {
                        data.append("&to_user="+URLEncoder.encode(""+selectedReply.User.UID,"utf-8"));
                        data.append("&to_comment="+(replyTo = selectedReply.getRID()));
                    } else {
                        data.append("&to_user=");
                        data.append("&to_comment=");
                    }
                    final Utils.RESTResponse restResponse = Utils.postJSON(context, "http://"+webLogin+".psto.net/"+pstoMid.getId()+"/comment", data.toString());
                    final int finalReplyTo = replyTo;
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (restResponse.getErrorText() == null) {
                                PstoMessage newmsg = new PstoMessage();
                                JuickMessagesAdapter listAdapter = context.tf.listAdapter;
                                Object lastItem = listAdapter.getItem(listAdapter.getCount() - 1);
                                int lastRid = 0;
                                if (lastItem != null && lastItem instanceof PstoMessage) {
                                    lastRid = ((PstoMessage)lastItem).getRID();
                                }
                                newmsg.User = new JuickUser();
                                newmsg.User.UName = webLogin;
                                newmsg.Text = msg;
                                newmsg.Timestamp = new Date();
                                newmsg.setRID(lastRid+1);
                                newmsg.setReplyTo(finalReplyTo);
                                newmsg.microBlogCode = CODE;
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
        then.apply("Not implemented yet");
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String txt, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, final Utils.Function<Void, String> then) {
        try {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(newMessageActivity);
            String webLogin = sp.getString("psto.web_login", null);
            if (webLogin == null) {
                throw new IllegalArgumentException("No PSTO authorization available");
            }

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
                    } else {
                        text = txt.substring(start);
                        break;
                    }
                }
                start=i+1;
            }

            StringBuilder data = new StringBuilder();
            data.append("text="+ URLEncoder.encode(txt, "utf-8"));
            if (hastags)
                data.append("&tags="+URLEncoder.encode(tags.toString(),"utf-8"));
            data.append("&private=1");
            final Utils.RESTResponse restResponse = Utils.postJSON(newMessageActivity, "http://"+webLogin+".psto.net/post?", data.toString());
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
}
