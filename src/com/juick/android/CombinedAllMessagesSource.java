package com.juick.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.juick.android.bnw.BnwCompatibleMessagesSource;
import com.juick.android.juick.JuickAllMessagesSource;
import com.juick.android.juick.JuickMicroBlog;
import com.juick.android.juick.MessagesSource;
import com.juick.android.point.PointAPIMessagesSource;
import com.juick.android.point.PointWebCompatibleMessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.point.PointMessageID;

import java.util.ArrayList;

/**
 * Created by san on 6/1/14.
 */
public class CombinedAllMessagesSource extends MessagesSource {

    public static final String COMBINED_ALL_MESSAGE_SOURCE = "CombinedAllMessageSource.";
    ArrayList<MessagesSource> nested = new ArrayList<MessagesSource>();
    ArrayList<Boolean> finished = new ArrayList<Boolean>();
    ArrayList<ArrayList<JuickMessage>> queues = new ArrayList<ArrayList<JuickMessage>>();

    public void cleanCloneFromCache() {
        queues = null;
        finished = null;
        pureMessageSource = null;
        ArrayList<MessagesSource> newNested = new ArrayList<MessagesSource>();
        for (MessagesSource messagesSource : nested) {
            MessagesSource clone = messagesSource.clone();
            clone.cleanCloneFromCache();
            newNested.add(clone);
        }
        nested = newNested;
    }


    public CombinedAllMessagesSource(Context ctx, String kind) {
        super(ctx, kind);
        initSources(ctx);
        for(int i=0; i<nested.size(); i++) {
            finished.add(null);
            queues.add(new ArrayList<JuickMessage>());
        }
    }

    protected void initSources(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (prefs.getBoolean(COMBINED_ALL_MESSAGE_SOURCE + JuickMessageID.CODE, true)) {
            JuickAllMessagesSource allMS = new JuickAllMessagesSource(ctx);
            allMS.setCanPersistInPrefs(false);
            nested.add(allMS);
        }
        if (prefs.getBoolean(COMBINED_ALL_MESSAGE_SOURCE + PointMessageID.CODE, true)) {
            nested.add(new PointAPIMessagesSource(ctx, "all", ctx.getString(com.juickadvanced.R.string.navigationPointAll), "http://point.im/api/all"));
            //nested.add(new PointWebCompatibleMessagesSource(ctx, "all", "Point/All", "http://point.im/all?agree=1"));
        }
        if (prefs.getBoolean(COMBINED_ALL_MESSAGE_SOURCE + BnwMessageID.CODE, true)) {
            nested.add(new BnwCompatibleMessagesSource(ctx, ctx.getString(com.juickadvanced.R.string.navigationBNWAll), "/show", "all"));
        }
    }

    @Override
    public void setContext(Context ctx) {
        super.setContext(ctx);
        if (nested != null) {
            for (MessagesSource messagesSource : nested) {
                messagesSource.setContext(ctx);
            }
        }
    }

    public void updateQueues(final Utils.Notification notifications, final Utils.Function<Void, Void> cont) {
        for(int i=0; i<nested.size(); i++) {
            nested.get(i).setContext(getContext());
            Boolean maybeFinished = finished.get(i);
            if (maybeFinished != null && maybeFinished) continue;
            final ArrayList<JuickMessage> queuedMessages = queues.get(i);
            if (queuedMessages.size() < 10) {
                final int finalI = i;
                if (maybeFinished == null) {
                    nested.get(i).getFirst(notifications, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                        @Override
                        public Void apply(ArrayList<JuickMessage> juickMessages) {
                            queuedMessages.addAll(juickMessages);
                            finished.set(finalI, juickMessages.size() == 0);
                            updateQueues(notifications, cont);
                            return null;
                        }
                    });
                } else {
                    nested.get(i).getNext(notifications, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                        @Override
                        public Void apply(ArrayList<JuickMessage> juickMessages) {
                            queuedMessages.addAll(juickMessages);
                            finished.set(finalI, juickMessages.size() == 0);
                            updateQueues(notifications, cont);
                            return null;
                        }
                    });
                }
                return;
            }
        }
        cont.apply(null);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;
    }

    @Override
    public void getFirst(Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        getNext(notifications, cont);
    }

    @Override
    public void getNext(Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        updateQueues(notifications, new Utils.Function<Void, Void>() {
            @Override
            public Void apply(Void aVoid) {
                ArrayList<JuickMessage> retval = new ArrayList<JuickMessage>();
                for(int i=0; i<10; i++) {
                    JuickMessage ne = null;
                    int bestq = -1;
                    for(int q=0; q<queues.size(); q++) {
                        if (queues.get(q).size() > 0) {
                            if (ne == null) {
                                ne = queues.get(q).get(0);
                                bestq = q;
                            } else {
                                JuickMessage maybe = queues.get(q).get(0);
                                if (maybe.Timestamp.getTime() > ne.Timestamp.getTime()) {
                                    ne = maybe;
                                    bestq = q;
                                }
                            }
                        }
                    }
                    if (ne != null) {
                        retval.add(queues.get(bestq).remove(0));
                    } else {
                        break;
                    }
                }
                cont.apply(retval);
                return null;
            }
        });
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        for (MessagesSource messagesSource : nested) {
            if (messagesSource.getMicroBlog().getCode().equals(mid.getMicroBlogCode())) {
                messagesSource.setContext(getContext());
                messagesSource.getChildren(mid, notifications, cont);
                return;
            }
        }
    }

    @Override
    public CharSequence getTitle() {
        return "Combined/All";
    }

    @Override
    public MicroBlog getMicroBlog() {
        return null;
    }
}
