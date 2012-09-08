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
import android.content.SharedPreferences;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.SupportActivity;
import android.view.*;
import android.widget.*;
import com.juick.android.api.JuickMessage;
import android.support.v4.app.ListFragment;
import com.juick.android.datasource.JuickCompatibleURLMessagesSource;
import com.juickadvanced.R;
import com.juick.android.api.JuickUser;

import java.util.ArrayList;

/**
 *
 * @author Ugnich Anton
 */
public class ThreadFragment extends ListFragment implements AdapterView.OnItemClickListener, View.OnTouchListener, WsClientListener, XMPPMessageReceiver.MessageReceiverListener {

    private ThreadFragmentListener parentActivity;
    private JuickMessagesAdapter listAdapter;
    private ScaleGestureDetector mScaleDetector = null;
    private WsClient ws = null;
    private int mid = 0;

    Handler handler;
    private Object restoreData;
    boolean implicitlyCreated;
    Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter;
    MessagesLoadNotification notification;
    SharedPreferences sp;
    private boolean trackLastRead;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(getActivity(), XMPPService.class);
        handler = new Handler();
        sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        trackLastRead = sp.getBoolean("lastReadMessages", false);
        if (Build.VERSION.SDK_INT >= 8) {
            if (sp.getBoolean("enableScaleByGesture", true)) {
                mScaleDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());
            }
        }
    }

    public ThreadFragment(Object restoreData) {
        this.restoreData = restoreData;
        implicitlyCreated = false;
    }

    public ThreadFragment() {
        implicitlyCreated = true;
    }




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view, null);
    }

    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        try {
            parentActivity = (ThreadFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement ThreadFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity.restyleChildrenOrWidget(view);
        Bundle args = getArguments();
        if (args != null) {
            mid = args.getInt("mid", 0);
        }
        if (mid == 0) {
            return;
        }

        getListView().setOnTouchListener(this);

        initAdapter();
        MessagesFragment.installDividerColor(getListView());
    }

    private void initWebSocket() {
        if (ws == null) {
            ws = new WsClient();
            ws.setListener(this);
        }
        final WsClient thisWs = ws;
        Thread wsthr = new Thread(new Runnable() {

            public void run() {
                if (thisWs == ws) {
                    if (ws.connect("api.juick.com", 8080, "/replies/" + mid, null) && ws != null) {
                        ws.readLoop();
                    }
                }
            }
        },"Websocket thread: mid="+mid);
        wsthr.start();
    }

    public void reload() {
        getView().findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
        getView().findViewById(android.R.id.list).setVisibility(View.GONE);
        restoreData = null;
        initAdapter();
    }

    public Object saveState() {
        MessagesFragment.RetainedData rd = new MessagesFragment.RetainedData(new ArrayList<JuickMessage>(), getListView().onSaveInstanceState());
        int count = listAdapter.getCount();
        for(int i=0; i<count; i++) {
            JuickMessage item = listAdapter.getItem(i);
            if (item.User != null)
                rd.messages.add(item);
        }
        return rd;
    }

    private void initAdapter() {
        listAdapter = new JuickMessagesAdapter(getActivity(), JuickMessagesAdapter.TYPE_THREAD, JuickMessagesAdapter.SUBTYPE_OTHER);
        if (implicitlyCreated || restoreData != null) {
            getView().findViewById(android.R.id.empty).setVisibility(View.GONE);
        }
        if (implicitlyCreated) {
            return;
        }
        getListView().setOnItemClickListener(this);
        getListView().setOnItemLongClickListener(new JuickMessageMenu(getActivity(), new JuickCompatibleURLMessagesSource(getActivity()), getListView(), listAdapter));

        notification = new MessagesLoadNotification(getActivity(), handler);
        final JuickCompatibleURLMessagesSource juickSource = new JuickCompatibleURLMessagesSource(getActivity());
        Thread thr = new Thread(new Runnable() {

            public void run() {
                final Utils.Function<Void, MessagesFragment.RetainedData> then = new Utils.Function<Void, MessagesFragment.RetainedData>() {
                    @Override
                    public Void apply(MessagesFragment.RetainedData retainedData) {
                        final ArrayList<JuickMessage> messages = retainedData.messages;
                        final Parcelable listPosition = retainedData.viewState;
                        if (isAdded()) {
                            if (messages.size() == 0) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        notification.statusText.setText("Download error: "+notification.lastError);
                                        notification.progressBar.setVisibility(View.GONE);
                                    }
                                });
                            }
                            getActivity().runOnUiThread(new Runnable() {

                                public void run() {
                                    Bundle args = getArguments();
                                    boolean scrollToBottom = args.getBoolean("scrollToBottom", false);
                                    if (scrollToBottom) {
                                        getListView().setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
                                        getListView().setStackFromBottom(true);
                                    }
                                    listAdapter.addAllMessages(messages);
                                    setListAdapter(listAdapter);
                                    getView().findViewById(android.R.id.list).setVisibility(View.VISIBLE);
                                    getView().findViewById(android.R.id.empty).setVisibility(View.GONE);

                                    if (messages.size() > 0) {
                                        initAdapterStageTwo();
                                    }
                                    if (listPosition != null) {
                                        try {
                                            getListView().onRestoreInstanceState(listPosition);
                                        } catch (Exception e) {
                                            /* tmp fix for
                                            java.lang.IllegalStateException: Content view not yet created
                                                    at+android.support.v4.app.ListFragment.ensureList(ListFragment.java:328)
                                                    at+android.support.v4.app.ListFragment.getListView(ListFragment.java:222)
                                             */

                                        }
                                    }
                                    if (messages.size() != 0) {
                                        Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(getActivity(), XMPPService.class);
                                        Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(getActivity(), DatabaseService.class);
                                        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                                            @Override
                                            public void withService(XMPPService service) {
                                                service.removeMessages(mid, false);
                                            }
                                        });
                                        databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                            @Override
                                            public void withService(DatabaseService service) {
                                                service.markAsRead(new DatabaseService.ReadMarker(mid, messages.size()-1, messages.get(0).Timestamp.getDate()));
                                            }

                                            @Override
                                            public void withoutService() {
                                            }
                                        });

                                    }

                                }
                            });
                        }
                        return null;
                    }
                };
                if (restoreData == null) {
                    juickSource.getChildren(mid, notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                        @Override
                        public Void apply(ArrayList<JuickMessage> messages) {
                            then.apply(new MessagesFragment.RetainedData(messages, null));
                            return null;
                        }
                    });
                } else {
                    then.apply((MessagesFragment.RetainedData)restoreData);
                    restoreData = null;
                }

            }
        },"Init adapter, mid="+mid);
        thr.start();
    }

    private void initAdapterStageTwo() {
        if (!isAdded()) {
            return;
        }
        String replies = getResources().getString(R.string.Replies) + " (" + Integer.toString(listAdapter.getCount() - 1) + ")";
        listAdapter.addDisabledItem(replies, 1);

        final JuickUser author = listAdapter.getItem(0).User;
        parentActivity.onThreadLoaded(author.UID, author.UName);
    }

    @Override
    public void onResume() {
        super.onResume();
        XMPPMessageReceiver.listeners.add(this);
        doneWebSocket();
        initWebSocket();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doneWebSocket();
                initWebSocket();
                handler.postDelayed(this, 60000);
            }
        }, 60000);
    }

    @Override
    public void onPause() {
        XMPPMessageReceiver.listeners.remove(this);
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        doneWebSocket();
        super.onDestroy();
    }

    private void doneWebSocket() {
        if (ws != null) {
            ws.disconnect();
            ws = null;
        }
    }

    public void onWebSocketTextFrame(final String jsonStr) {
        if (!isAdded()) {
            return;
        }
        if (jsonStr != null) {
            JuickCompatibleURLMessagesSource jcus = new JuickCompatibleURLMessagesSource(getActivity());
            final ArrayList<JuickMessage> messages = jcus.parseJSONpure("[" + jsonStr + "]");
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (sp.getBoolean("current_vibration_enabled", true))
                        ((Vibrator) getActivity().getSystemService(Activity.VIBRATOR_SERVICE)).vibrate(250);
                    if (listAdapter.getCount() > 0) {
                        listAdapter.addAllMessages(messages);
                    }
                    xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                        @Override
                        public void withService(XMPPService service) {
                            for (JuickMessage message : messages) {
                                service.removeMessages(message.MID, false);
                            }
                        }
                    });
                    try {
                        listAdapter.getItem(1).Text = getResources().getString(R.string.Replies) + " (" + Integer.toString(listAdapter.getCount() - 2) + ")";
                    } catch (Throwable ex) {
                        // web socket came earlier than body itself!
                    }
                }
            });
        }
    }

    public JuickMessage findReply(AdapterView<?> parent, int replyNo) {
        for(int q=0; q<parent.getCount(); q++) {
            Object itemAtPosition = parent.getItemAtPosition(q);
            if (itemAtPosition instanceof JuickMessage) {
                JuickMessage maybeReplied = (JuickMessage) itemAtPosition;
                if (maybeReplied.RID == replyNo) {
                    return  maybeReplied;
                }
            }
        }
        return null;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JuickMessage jmsg = (JuickMessage) parent.getItemAtPosition(position);
        if (jmsg.replyTo != 0) {
            JuickMessage reply = jmsg;
            LinearLayout ll = new LinearLayout(getActivity());
            ll.setOrientation(LinearLayout.VERTICAL);
            int totalCount = 0;
            while(reply != null) {
                totalCount += reply.Text.length();
                if (totalCount > 500 || ll.getChildCount() > 10) break;
                JuickMessagesAdapter.ParsedMessage parsedMessage = JuickMessagesAdapter.formatMessageText(getActivity(), reply, true);
                TextView child = new TextView(getActivity());
                ll.addView(child, 0);
                child.setText(parsedMessage.textContent);
                if (reply.replyTo < 1) break;
                reply = findReply(parent, reply.replyTo);
            }
            if (ll.getChildCount() != 0) {
                ll.setPressed(true);
                MainActivity.restyleChildrenOrWidget(ll);
                Toast result = new Toast(getActivity());
                result.setView(ll);
                result.setDuration(Toast.LENGTH_LONG);
                result.show();
            }
        }
        parentActivity.onReplySelected(jmsg.RID, jmsg.Text);
    }

    public boolean onTouch(View view, MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
        }
        return false;
    }

    public interface ThreadFragmentListener {

        public void onThreadLoaded(int uid, String nick);

        public void onReplySelected(int rid, String txt);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            listAdapter.setScale(detector.getScaleFactor());
            listAdapter.notifyDataSetChanged();
            return true;
        }
    }

    @Override
    public boolean onMessageReceived(XMPPService.IncomingMessage message) {
        if (message instanceof XMPPService.JuickThreadIncomingMessage) {
            XMPPService.JuickThreadIncomingMessage jtim = (XMPPService.JuickThreadIncomingMessage)message;
            if (jtim.getPureThread() == mid) {
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        service.removeMessages(mid, false);
                    }
                });
                //
                return true;
            }
        }
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }


}
