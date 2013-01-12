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
import com.juickadvanced.data.juick.JuickMessage;
import android.support.v4.app.ListFragment;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 *
 * @author Ugnich Anton
 */
public class ThreadFragment extends ListFragment implements AdapterView.OnItemClickListener, View.OnTouchListener, XMPPMessageReceiver.MessageReceiverListener {

    public static int instanceCount;
    {
        instanceCount++;
    }

    private boolean paused;

    public interface ThreadExternalUpdater {

        void terminate();

        interface Listener {
            public void onNewMessages(ArrayList<JuickMessage> messages);
        }

        public void setListener(Listener listener);
        public void setPaused(boolean paused);

    }

    private ThreadFragmentListener parentActivity;
    public JuickMessagesAdapter listAdapter;
    private ScaleGestureDetector mScaleDetector = null;
    private ThreadExternalUpdater ws = null;
    private MessageID mid = null;

    Handler handler;
    private Object restoreData;
    boolean implicitlyCreated;
    Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter;
    MessagesLoadNotification notification;
    SharedPreferences sp;
    private boolean trackLastRead;
    ProgressBar large, small;
    View cached_copy_label;
    View list_container;

    class ThreadMessagesLoadNotification extends MessagesLoadNotification implements Utils.HasCachedCopyNotification {

        ThreadMessagesLoadNotification(Activity activity, Handler handler) {
            super(activity, handler);
        }

        @Override
        public void onCachedCopyObtained(ArrayList<JuickMessage> messages) {

            onObtainAllThread(new MessagesFragment.RetainedData(messages, null), true);
            System.out.println();
        }
    }

    MessagesSource parentMessagesSource;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(getActivity(), XMPPService.class);
        handler = new Handler();
        parentMessagesSource = (MessagesSource) getArguments().getSerializable("messagesSource");
        if (parentMessagesSource != null)
            parentMessagesSource.setContext(getActivity());
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
            mid = (MessageID)args.getSerializable("mid");
        }
        if (mid == null) {
            return;
        }
        large = (ProgressBar)view.findViewById(R.id.progress_bar);
        small = (ProgressBar) view.findViewById(R.id.progress_bar_small);
        cached_copy_label = view.findViewById(R.id.cached_copy_label);

        getListView().setOnTouchListener(this);

        initAdapter();
        MessagesFragment.installDividerColor(getListView());
    }


    private void initWebSocket() {
        if (ws != null) {
            ws.terminate();
            ws = null;

        }
        if (ws == null) {
            ws = MainActivity.getMicroBlog(mid.getMicroBlogCode()).getThreadExternalUpdater(getActivity(), mid);
            if (ws != null) {
                ws.setListener(new ThreadExternalUpdater.Listener() {
                    @Override
                    public void onNewMessages(ArrayList<JuickMessage> messages) {
                        if (!isAdded()) {
                            return;
                        }
                        onWebSocketMessages(messages);
                    }
                });
            }
        }
    }

    public void reload() {
        getView().findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
        large.setVisibility(View.VISIBLE);
        small.setVisibility(View.GONE);
        setCachedViewIndicators(false);
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
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Object itemAtPosition = parent.getItemAtPosition(position);
                if (itemAtPosition instanceof JuickMessage && parentMessagesSource != null) {
                    JuickMessage msg = (JuickMessage)itemAtPosition;
                    if (msg.getMID() != null) {
                        MessageMenu messageMenu = MainActivity.getMicroBlog(msg).getMessageMenu(getActivity(), parentMessagesSource, getListView(), listAdapter);
                        messageMenu.onItemLongClick(parent, view, position, id);
                    }

                }
                return true;
            }
        });
        notification = new ThreadMessagesLoadNotification(getActivity(), handler);
        Thread thr = new Thread(new Runnable() {

            public void run() {
                final Utils.Function<Void, MessagesFragment.RetainedData> then = new Utils.Function<Void, MessagesFragment.RetainedData>() {
                    @Override
                    public Void apply(MessagesFragment.RetainedData retainedData) {
                        onObtainAllThread(retainedData, false);
                        return null;
                    }
                };
                if (restoreData == null) {
                    if (parentMessagesSource != null) {
                        parentMessagesSource.getChildren(mid, notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                            @Override
                            public Void apply(ArrayList<JuickMessage> messages) {
                                then.apply(new MessagesFragment.RetainedData(messages, null));
                                return null;
                            }
                        });
                    }
                } else {
                    then.apply((MessagesFragment.RetainedData)restoreData);
                    restoreData = null;
                }

            }
        },"Init adapter, mid="+mid);
        thr.start();
    }

    /**
     * unsafe
     */
    private void onObtainAllThread(final MessagesFragment.RetainedData retainedData, final boolean cached) {
        final ArrayList<JuickMessage> messages = retainedData.messages;
        if (isAdded()) {
            getActivity().runOnUiThread(new Runnable() {

                public void run() {
                    try {
                        getListView();
                    } catch (Exception e) {
                        handler.postDelayed(this, 50);  // bugs in fragment manager
                        return;
                    }
                    if (notification.lastError != null && !cached) {
                        notification.statusText.setText(notification.lastError);
                        notification.progressBar.setVisibility(View.GONE);
                    } else {
                        Bundle args = getArguments();
                        Parcelable listPosition = retainedData.viewState;
                        boolean disableScrollToEnd = false;
                        if (listPosition == null && listAdapter.getCount() > 0) {
                            // probably transition from cached data to live data
                            listPosition = getListView().onSaveInstanceState();
                            int addMarkOnComment = listAdapter.getCount() - 2 + 1; // totalRecs-body-separator, 0-based (0=first comment)
                            if (messages.size() > addMarkOnComment)
                                messages.get(addMarkOnComment).continuationInformation = getString(R.string.UnreadPeriodStart);
                            disableScrollToEnd = true;
                            listAdapter.clear();
                        }
                        boolean scrollToBottom = args != null && args.getBoolean("scrollToBottom", false);
                        if (scrollToBottom && !disableScrollToEnd) {
                            getListView().setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
                            getListView().setStackFromBottom(true);
                        } else {
                            getListView().setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
                            getListView().setStackFromBottom(false);
                        }
                        listAdapter.addAllMessages(messages);
                        setListAdapter(listAdapter);
                        if (cached) {
                            getListView().setSelection(getListView().getAdapter().getCount()-1);
                        }
                        getView().findViewById(android.R.id.list).setVisibility(View.VISIBLE);

                        initAdapterStageTwo(cached);
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
                        if (listAdapter.getCount() != 0) {   // could be filtered out!
                            Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(getActivity(), XMPPService.class);
                            Activity activity = getActivity();
                            if (activity != null) {
                                Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
                                if (!XMPPIncomingMessagesActivity.editMode) {
                                    xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                                        @Override
                                        public void withService(XMPPService service) {
                                            service.removeMessages(mid, false);
                                        }
                                    });
                                }
                                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                    @Override
                                    public void withService(DatabaseService service) {
                                        service.markAsRead(new DatabaseService.ReadMarker(mid, messages.size() - 1, messages.get(0).Timestamp.getDate()));
                                    }

                                    @Override
                                    public void withoutService() {
                                    }
                                });
                            }

                        }
                        if (cached) {
                            large.setVisibility(View.GONE);
                            small.setVisibility(View.VISIBLE);
                            setCachedViewIndicators(true);
                            getView().findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
                        } else {
                            setCachedViewIndicators(false);
                            getView().findViewById(android.R.id.empty).setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    }

    private void setCachedViewIndicators(boolean cached) {
        cached_copy_label.setVisibility(cached ? View.VISIBLE : View.GONE);
        View container = getView().findViewById(R.id.list_container);
        if (cached) {
            container.setPadding(2, 2, 2, 2);
            container.setBackgroundColor(0xFFA0A000);
        } else {
            container.setPadding(0, 0, 0, 0);
            container.setBackgroundColor(0);
        }
    }

    private void initAdapterStageTwo(boolean cached) {
        if (!isAdded()) {
            return;
        }
        String replies = getResources().getString(R.string.Replies) + " (" + Integer.toString(listAdapter.getCount() - 1) + ")";
        listAdapter.addDisabledItem(replies, 1);
        if (!cached) {
            if (listAdapter.getCount() > 0)
                parentActivity.onThreadLoaded(listAdapter.getItem(0));
        }

        getListView().setRecyclerListener(new AbsListView.RecyclerListener() {
            @Override
            public void onMovedToScrapHeap(View view) {
                listAdapter.recycleView(view);
            }
        });

    }

    @Override
    public void onResume() {
        paused = false;
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
        paused = true;
        if (ws != null)
            ws.setPaused(true);
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
            ws.terminate();
            ws = null;
        }
    }

    private void onWebSocketMessages(final ArrayList<JuickMessage> messages) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                // don't scroll
                ListView listView = null;
                try {
                    listView = getListView();
                } catch (Exception e) {
                    // view not yet created
                    return;
                }
                listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
                listView.setStackFromBottom(false);
                //
                if (sp.getBoolean("current_vibration_enabled", true)) {
                    if (!paused) {
                        ((Vibrator) getActivity().getSystemService(Activity.VIBRATOR_SERVICE)).vibrate(250);
                    }
                }
                if (listAdapter.getCount() > 0) {
                    listAdapter.addAllMessages(messages);
                }
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        for (JuickMessage message : messages) {
                            service.removeMessages(message.getMID(), false);
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

    public JuickMessage findReply(AdapterView<?> parent, int replyNo) {
        for(int q=0; q<parent.getCount(); q++) {
            Object itemAtPosition = parent.getItemAtPosition(q);
            if (itemAtPosition instanceof JuickMessage) {
                JuickMessage maybeReplied = (JuickMessage) itemAtPosition;
                if (maybeReplied.getRID() == replyNo) {
                    return  maybeReplied;
                }
            }
        }
        return null;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JuickMessage jmsg = (JuickMessage) parent.getItemAtPosition(position);
        parentActivity.onReplySelected(jmsg);
    }

    public boolean onTouch(View view, MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
        }
        return false;
    }

    public interface ThreadFragmentListener {

        public void onThreadLoaded(JuickMessage message);

        public void onReplySelected(JuickMessage reply);
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
            if (jtim.getMID() == mid) {
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

    public void showThread(JuickMessage jmsg) {
        if (jmsg.getReplyTo() != 0) {
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
                if (reply.getReplyTo() < 1) break;
                reply = findReply(getListView(), reply.getReplyTo());
            }
            if (ll.getChildCount() != 0) {
                int xy[] = new int[2];
                getListView().getLocationOnScreen(xy);
                int windowHeight = getActivity().getWindow().getWindowManager().getDefaultDisplay().getHeight();
                int listBottom = getListView().getHeight() + xy[1];
                int bottomSize = windowHeight - listBottom;
                ll.setPressed(true);
                MainActivity.restyleChildrenOrWidget(ll);
                Toast result = new Toast(getActivity());
                result.setView(ll);
                result.setGravity(Gravity.BOTTOM|Gravity.LEFT, 0, bottomSize);
                result.setDuration(Toast.LENGTH_LONG);
                result.show();
            }
        }
    }


    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }
}
