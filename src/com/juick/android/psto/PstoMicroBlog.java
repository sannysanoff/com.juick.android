package com.juick.android.psto;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.*;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.psto.PstoMessage;
import com.juickadvanced.data.psto.PstoMessageID;

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

    public PstoMicroBlog() {
        instance = this;
    }


    @Override
    public void addNavigationSources(ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity mainActivity) {
        final SharedPreferences sp = mainActivity.sp;
        navigationItems.add(new MainActivity.NavigationItem(50001, R.string.navigationPSTOSubs, R.drawable.navicon_psto, "msrcPSTOSubs") {
            @Override
            public void action() {
                final MainActivity.NavigationItem thiz = this;
                runAuthorized(new Runnable() {
                    @Override
                    public void run() {
                        final Bundle args = new Bundle();
                        final String weblogin = sp.getString("psto.web_login", null);
                        PstoCompatibleMessagesSource ms = new PstoCompatibleMessagesSource(mainActivity, "home", mainActivity.getString(labelId), "http://" + weblogin + ".psto.net/subs");
                        args.putSerializable("messagesSource", ms);
                        mainActivity.runDefaultFragmentWithBundle(args, thiz);
                    }
                }, mainActivity);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(50002, R.string.navigationPSTOPopular, R.drawable.navicon_psto, "msrcPSTOPopular") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                PstoCompatibleMessagesSource ms = new PstoCompatibleMessagesSource(mainActivity, "top", mainActivity.getString(labelId), "http://psto.net/top");
                ms.setCanNext(false);
                args.putSerializable("messagesSource", ms);
                mainActivity.runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(50003, R.string.navigationPSTORecent, R.drawable.navicon_psto, "msrcPSTORecent") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                PstoCompatibleMessagesSource ms = new PstoCompatibleMessagesSource(mainActivity, "all", mainActivity.getString(labelId), "http://psto.net/recent");
                args.putSerializable("messagesSource", ms);
                mainActivity.runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(50004, R.string.navigationPSTOMy, R.drawable.navicon_psto, "msrcPSTOMy") {
            @Override
            public void action() {
                final MainActivity.NavigationItem thiz = this;
                runAuthorized(new Runnable() {
                    @Override
                    public void run() {
                        final Bundle args = new Bundle();
                        final String weblogin = sp.getString("psto.web_login", null);
                        PstoCompatibleMessagesSource ms = new PstoCompatibleMessagesSource(mainActivity, "my", mainActivity.getString(labelId), "http://" + weblogin + ".psto.net/");
                        args.putSerializable("messagesSource", ms);
                        mainActivity.runDefaultFragmentWithBundle(args, thiz);
                    }
                }, mainActivity);
            }
        });
    }

    private void runAuthorized(final Runnable runWithLogin, final MainActivity mainActivity) {
        Utils.URLAuth authorizer = Utils.getAuthorizer("http://psto.net/");
        authorizer.authorize(mainActivity, true, false, "http://psto.net/", new Utils.Function<Void, String>() {
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
        new PstoCompatibleMessagesSource(context, "dummy", "","http://psto.net/").getChildren(mid, notifications, cont);
    }

    @Override
    public JuickMessage createMessage() {
        JuickMessage juickMessage = new PstoMessage();
        juickMessage.microBlogCode = PstoMessageID.CODE;
        return juickMessage;
    }


    @Override
    public void initialize() {
        Utils.authorizers.add(0, new PstoAuthorizer());
    }

    @Override
    public String getCode() {
        return PstoMessageID.CODE;
    }

    @Override
    public String getMicroblogName(Context context) {
        return context.getString(R.string.PstoMicroblog);
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(final JuickMessage jmsg) {
        return new UserpicStorage.AvatarID() {
            @Override
            public String toString(int size) {
                return "PSTO:"+jmsg.User.UName;
            }

            @Override
            public String getURL(int size) {
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
    public OperationInProgress postReply(final Activity context_, final MessageID mid, final JuickMessage selectedReply, final String msg, String attachmentUri, String attachmentMime, final Utils.Function<Void, String> then) {
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
                                newmsg.microBlogCode = PstoMessageID.CODE;
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
        return new OperationInProgress() {
            @Override
            public void preliminarySuccess() {

            }
        };
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String txt, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, final Utils.Function<Void, String> then) {
        try {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(newMessageActivity);
            String webLogin = sp.getString("psto.web_login", null);
            if (webLogin == null) {
                throw new IllegalArgumentException("No PSTO authorization available");
            }

            StringBuilder tagsStr = new StringBuilder();
            String s = txt = txt.trim();
            if (s.startsWith("*")) {
                String tagline = s.split("\n")[0];
                String[] tags = tagline.split("\\*");
                for (String tag : tags) {
                    String thatTag = tag.trim();
                    if (thatTag.length() > 0) {
                        if (tagsStr.length() != 0) {
                            tagsStr.append(",");
                        }
                        tagsStr.append(thatTag);
                    }
                }
                int eol = txt.indexOf("\n");
                if (eol != -1) {
                    txt = txt.substring(eol+1);
                }
            }

            StringBuilder data = new StringBuilder();
            data.append("text="+ URLEncoder.encode(txt, "utf-8"));
            if (tagsStr.length() > 0)
                data.append("&tags="+URLEncoder.encode(tagsStr.toString(),"utf-8"));
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
