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
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.SupportActivity;
import android.text.Editable;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import com.juickadvanced.data.juick.JuickMessage;
import android.support.v4.app.ListFragment;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;

import java.util.ArrayList;
import java.util.Date;

/**
 * @author Ugnich Anton
 */
public class ThreadFragment extends ListFragment implements AdapterView.OnItemClickListener, View.OnTouchListener, XMPPMessageReceiver.MessageReceiverListener {

    public static int instanceCount;

    {
        instanceCount++;
    }

    private boolean paused;
    private Runnable doOnClick;
    private long doOnClickActualTime;
    private MyImageView navMenu;
    /**
     * @see MessagesFragment#alternativeLongClick
     */
    private boolean alternativeLongClick;
    private JuickMessage prefetched;    // this is partially obtained thread originating in 'pending' replies screen
    private Toast shownThreadToast;
    private boolean navigationMenuShown;
    private JuickFragmentActivity parent;

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
        prefetched = (JuickMessage) getArguments().getSerializable("prefetched");
        if (parentMessagesSource != null)
            parentMessagesSource.setContext(getActivity());
        sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        trackLastRead = sp.getBoolean("lastReadMessages", false);
        if (Build.VERSION.SDK_INT >= 8) {
            mScaleDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());
        }
    }

    public ThreadFragment(Object restoreData, JuickFragmentActivity parent) {
        this.restoreData = restoreData;
        this.parent = parent;
        implicitlyCreated = false;
    }

    public ThreadFragment() {
        implicitlyCreated = true;
    }

    FlyingItem[] flyingItems;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.fragment_view, null);
        return inflate;
    }

    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        try {
            parentActivity = (ThreadFragmentListener) activity;
            navMenu = (MyImageView) activity.findViewById(R.id.navmenu);
            if (navMenu != null) {
                navMenu.setVisibility(View.GONE);
                initNavMenuTranslationX = navMenu.initialTranslationX;
                FlyingItem top = new FlyingItem(activity.getWindow().getDecorView(), R.id.navbar_top);
                FlyingItem bottom = new FlyingItem(activity.getWindow().getDecorView(), R.id.navbar_bottom);
                flyingItems = new FlyingItem[] {top, bottom};
            }
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
            mid = (MessageID) args.getSerializable("mid");
        }
        if (mid == null) {
            return;
        }
        large = (ProgressBar) view.findViewById(R.id.progress_bar);
        small = (ProgressBar) view.findViewById(R.id.progress_bar_small);
        cached_copy_label = view.findViewById(R.id.cached_copy_label);

        getListView().setOnTouchListener(this);

        initAdapter();
        MessagesFragment.installDividerColor(getListView());

        if (parent != null)
            parent.imagePreviewHelper = listAdapter.imagePreviewHelper = new ImagePreviewHelper((ViewGroup)getView().findViewById(R.id.imagepreview_container), getActivity());
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
        for (int i = 0; i < count; i++) {
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
            public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                Object itemAtPosition = parent.getItemAtPosition(position);
                if (itemAtPosition instanceof JuickMessage && parentMessagesSource != null) {
                    final JuickMessage msg = (JuickMessage) itemAtPosition;
                    if (msg.getMID() != null) {
                        doOnClickActualTime = System.currentTimeMillis();
                        doOnClick = new Runnable() {
                            @Override
                            public void run() {
                                MessageMenu messageMenu = MainActivity.getMicroBlog(msg).getMessageMenu(getActivity(), parentMessagesSource, getListView(), listAdapter);
                                messageMenu.onItemLongClick(parent, view, position, id);
                            }
                        };
                        if (alternativeLongClick) {
                            getListView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        } else {
                            doOnClick.run();
                            doOnClick = null;
                            return true;
                        }

                    }

                }
                return false;
            }
        });
        notification = new ThreadMessagesLoadNotification(getActivity(), handler);
        navMenu.setVisibility(View.GONE);
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
                        if (prefetched != null) {
                            ArrayList<JuickMessage> messages = new ArrayList<JuickMessage>();
                            messages.add(prefetched.contextPost);
                            if (prefetched.contextReply != null) {
                                messages.add(prefetched.contextReply);
                            }
                            messages.add(prefetched);
                            prefetched.contextPost = null;      // don't confuse later
                            prefetched.contextReply = null;
                            prefetched = null;      // to enable proper reload
                            then.apply(new MessagesFragment.RetainedData(messages, null));
                        } else {
                            parentMessagesSource.getChildren(mid, notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                                @Override
                                public Void apply(ArrayList<JuickMessage> messages) {
                                    preprocessMessages(messages);
                                    then.apply(new MessagesFragment.RetainedData(messages, null));
                                    return null;
                                }
                            });
                        }
                    }
                } else {
                    then.apply((MessagesFragment.RetainedData) restoreData);
                    restoreData = null;
                }

            }
        }, "Init adapter, mid=" + mid);
        thr.start();
    }

    /**
     * calculate delta times
     * @param messages
     */
    private void preprocessMessages(ArrayList<JuickMessage> messages) {
        if (messages == null || messages.size() == 0) return;
        Date prevTime = messages.get(0).Timestamp;
        for (int i = 1; i < messages.size(); i++) {
            JuickMessage message = messages.get(i);
            message.deltaTime = message.Timestamp.getTime() - prevTime.getTime();
            if (message.Timestamp.getDate() != prevTime.getDate()) {
                message.deltaTime = Long.MIN_VALUE;
            }
            prevTime = message.Timestamp;
        }
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
                            getListView().setSelection(getListView().getAdapter().getCount() - 1);
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
                                if (!XMPPIncomingMessagesActivity.editMode) {
                                    xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                                        @Override
                                        public void withService(XMPPService service) {
                                            service.removeMessages(mid, false);
                                        }
                                    });
                                }
                                Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
                                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                    @Override
                                    public void withService(DatabaseService service) {
                                        service.markAsRead(new DatabaseService.ReadMarker(mid, messages.size() - 1, messages.get(0).Timestamp.getDate()));
                                        service.saveRecentlyOpenedThread(messages.get(0));
                                    }

                                    @Override
                                    public void withoutService() {
                                    }
                                });
                            }
                            if (listAdapter.getCount() > 14) {
                                resetMainMenuButton(true);
                                navMenu.setVisibility(View.VISIBLE);
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
                    if (getActivity() instanceof ThreadActivity) {
                        ThreadActivity ta = (ThreadActivity)getActivity();
                        Editable text = ta.etMessage.getText();
                        for (JuickMessage message : messages) {
                            if (message.Text.contains(text)) {
                                if (ta.replyInProgress != null) {
                                    ta.replyInProgress.preliminarySuccess();
                                }
                            }
                        }
                    }
                }
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        for (JuickMessage message : messages) {
                            service.removeMessages(message.getMID(), false);
                        }
                    }
                });
                Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(getActivity(), DatabaseService.class);
                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        service.maybeSaveMessages(messages);
                        super.withService(service);
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
        for (int q = 0; q < parent.getCount(); q++) {
            Object itemAtPosition = parent.getItemAtPosition(q);
            if (itemAtPosition instanceof JuickMessage) {
                JuickMessage maybeReplied = (JuickMessage) itemAtPosition;
                if (maybeReplied.getRID() == replyNo) {
                    return maybeReplied;
                }
            }
        }
        return null;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (doOnClick != null) {
            if (System.currentTimeMillis() < doOnClickActualTime + 1000) {
                doOnClick.run();
            }
            doOnClick = null;
            return;
        }
        JuickMessage jmsg = (JuickMessage) parent.getItemAtPosition(position);
        parentActivity.onReplySelected(jmsg);
    }

    boolean touchOnNavMenu = false;
    boolean ignoreMove = false;
    float touchOriginX = -1;
    float touchOriginY = -1;
    float initNavMenuTranslationX;

    public boolean onTouch(View view, MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
        }
        try {
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            event.getPointerCoords(0, pc);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (navMenu != null && navMenu.getVisibility() == View.VISIBLE) {
                        int[] listViewLocation = new int[2];
                        int[] imageLocation = new int[2];
                        getListView().getLocationOnScreen(listViewLocation);
                        navMenu.getLocationOnScreen(imageLocation);
                        imageLocation[0] += navMenu.initialTranslationX;
                        float touchX = pc.x + listViewLocation[0] - imageLocation[0];
                        float touchY = pc.y + listViewLocation[1] - imageLocation[1];
                        System.out.println("TOUCH: ACTION_DOWN: x=" + pc.x + " y=" + pc.y);
                        if (touchX > -20 && touchX < navMenu.getWidth()
                                && touchY > 0 && touchY < navMenu.getHeight()*1.5) {  // extra Y pixels due to picture not balanced
                            touchOriginX = pc.x;
                            touchOriginY = pc.y;
                            System.out.println("TOUCH: OK TOUCH NAVMENU");
                            return true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!ignoreMove && !isNavigationMenuShown())
                        resetMainMenuButton(false);
                    if (touchOriginX > 0 || ignoreMove) {
                        touchOriginX = -1;
                        ignoreMove = false;
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (ignoreMove || navMenu == null)
                        return true;
                    event.getPointerCoords(0, pc);
                    double travelledDistance = Math.sqrt(Math.pow(touchOriginX - pc.x, 2) + Math.pow(touchOriginY - pc.y, 2));
                    boolean inZone = false;
                    if (!isNavigationMenuShown()) {
                        if (touchOriginX >= 0) {
                            // detect angle where finger moves
                            if (travelledDistance < 10) {   // grace period
                                inZone = true;
                            } else {
                                float dx = Math.abs(touchOriginX - pc.x);
                                float dy = Math.abs(touchOriginY - pc.y);
                                if (dx > dy) {
                                    // movement in 45 degree zone
                                    if (touchOriginX > pc.x) {
                                        // towards left
                                        inZone = true;
                                        double neededDistance = 1.5 / 2.54 * getResources().getDisplayMetrics().xdpi;
                                        if (travelledDistance > neededDistance) {
                                            // moved 1.5 centimeters
                                            System.out.println("TOUCH: OPEN MENU");
                                            ignoreMove = true;
                                            openNavigationMenu(pc.x-touchOriginX+initNavMenuTranslationX);
                                            touchOriginX = -1;
                                        }
                                    }
                                } else {
                                    System.out.println("TOUCH: LEAVING ZONE: dx=" + dx + " dy=" + dy);
                                }
                            }
                            if (inZone && !ignoreMove) {
                                TranslateAnimation immediate = new TranslateAnimation(
                                        Animation.ABSOLUTE, pc.x-touchOriginX+initNavMenuTranslationX, Animation.ABSOLUTE, pc.x-touchOriginX+initNavMenuTranslationX,
                                        Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0);
                                immediate.setDuration(5);
                                immediate.setFillAfter(true);
                                immediate.setFillBefore(true);
                                immediate.setFillEnabled(true);
                                navMenu.startAnimation(immediate);
                            }
                        }
                        if (!inZone) {
                            resetMainMenuButton(false);
                            if (touchOriginX >= 0) {
                                System.out.println("TOUCH: ACTION_MOVE: x=" + pc.x + " y=" + pc.y);
                                System.out.println("TOUCH: LEFT ZONE");
                                touchOriginX = -1;
                            }
                        }
                        if (inZone) {
                            return true;
                        }
                        if (doOnClick != null || ignoreMove) {
                            return true;
                        }
                    }
                    break;
            }
        } catch (NoClassDefFoundError err) {
            // so be it.
        }
        return false;
    }

    private void resetMainMenuButton(boolean animate) {
        if (navMenu != null) {
            TranslateAnimation immediate = new TranslateAnimation(
                    Animation.ABSOLUTE, animate ? initNavMenuTranslationX + 100 : initNavMenuTranslationX, Animation.ABSOLUTE, initNavMenuTranslationX,
                    Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0);
            immediate.setDuration(500);
            immediate.setFillEnabled(true);
            immediate.setFillBefore(true);
            immediate.setFillAfter(true);
            navMenu.startAnimation(immediate);
        }
        //navMenu.startAnimation(immediate);
    }

    class FlyingItem implements View.OnClickListener {
        MyImageView widget;
        float designedX;
        float designedY;
        TranslateAnimation ani;
        final int id;
        public Rect originalHitRect = new Rect();

        FlyingItem(View view, final int id) {
            this.id = id;
            widget = (MyImageView) view.findViewById(id);
            designedX = widget.initialTranslationX;
            designedY = widget.initialTranslationY;
            setVisibility(View.GONE);
            widget.setOnClickListener(this);
        }

        private void setVisibility(int gone) {
            widget.setVisibility(gone);
        }


        public void onClick(View v) {
            onNavigationMenuItemPressed(id);
        }
    }

    private void onNavigationMenuItemPressed(int id) {
        if (id == R.id.navbar_top) {
            getListView().setSelection(0);
        }
        if (id == R.id.navbar_bottom) {
            getListView().setSelection(getListView().getAdapter().getCount() - 1);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                closeNavigationMenu();
            }
        }, 200);
    }

    private void closeNavigationMenu() {
        navigationMenuShown = false;
        for (int i = 0; i < flyingItems.length; i++) {
            final FlyingItem item = flyingItems[i];
            item.setVisibility(View.VISIBLE);
            item.ani = new TranslateAnimation(
                    Animation.ABSOLUTE, 0, Animation.ABSOLUTE, -item.designedX + item.widget.getWidth()*1.5f,
                    Animation.ABSOLUTE, 0, Animation.ABSOLUTE, -item.designedY);
            item.ani.setDuration(500);
            item.ani.setInterpolator(new AccelerateInterpolator(1));

            item.ani.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    item.setVisibility(View.GONE);
                    item.widget.clearAnimation();
                    item.widget.disableReposition = false;
                    item.widget.layout(
                            item.originalHitRect.left,
                            item.originalHitRect.top,
                            item.originalHitRect.right,
                            item.originalHitRect.bottom);
                    if (item == flyingItems[0]) {
                        TranslateAnimation aniIn = new TranslateAnimation(
                                Animation.ABSOLUTE, 600, Animation.ABSOLUTE, initNavMenuTranslationX,
                                Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0);
                        aniIn.setInterpolator(new DecelerateInterpolator(1));
                        item.ani.setDuration(500);
                        aniIn.setFillAfter(true);
                        navMenu.startAnimation(aniIn);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
            item.ani.setFillAfter(true);
            item.widget.startAnimation(item.ani);
        }
    }

    private void openNavigationMenu(float currentTranslation) {
        try {
            navigationMenuShown = true;
            for (final FlyingItem item : flyingItems) {
                item.setVisibility(View.VISIBLE);
                item.ani = new TranslateAnimation(
                        Animation.ABSOLUTE, 300, Animation.ABSOLUTE, item.designedX,
                        Animation.ABSOLUTE, 0, Animation.ABSOLUTE, item.designedY);

                item.ani.setInterpolator(new OvershootInterpolator(2));
                item.ani.setDuration(500);
                item.ani.setFillAfter(true);
                item.ani.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        // this code is very ugly because it's all android 2.3 animations.
                        item.widget.getHitRect(item.originalHitRect);
                        item.widget.clearAnimation();
                        item.widget.layout(
                                item.originalHitRect.left + (int)item.widget.initialTranslationX,
                                item.originalHitRect.top + (int)item.widget.initialTranslationY,
                                item.originalHitRect.right + (int)item.widget.initialTranslationX,
                                item.originalHitRect.bottom + (int)item.widget.initialTranslationY);
                        item.widget.disableReposition = true;
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
                item.widget.startAnimation(item.ani);
            }
            TranslateAnimation aniOut = new TranslateAnimation(
                    Animation.ABSOLUTE, currentTranslation, Animation.ABSOLUTE, -1.2f * getActivity().getWindowManager().getDefaultDisplay().getWidth(),
                    Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0);
            aniOut.setInterpolator(new DecelerateInterpolator(1));
            aniOut.setDuration(700);
            aniOut.setFillAfter(true);
            navMenu.startAnimation(aniOut);

            getListView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public interface ThreadFragmentListener {

        public void onThreadLoaded(JuickMessage message);

        public void onReplySelected(JuickMessage reply);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            listAdapter.setScale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY(), getListView());
            listAdapter.notifyDataSetChanged();
            return true;
        }
    }

    @Override
    public boolean onMessageReceived(XMPPService.IncomingMessage message) {
        if (message instanceof XMPPService.JuickThreadIncomingMessage) {
            XMPPService.JuickThreadIncomingMessage jtim = (XMPPService.JuickThreadIncomingMessage) message;
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

    public void hideThread() {
        if (shownThreadToast != null) {
            shownThreadToast.cancel();
            shownThreadToast = null;
        }

    }
    public void showThread(JuickMessage jmsg, boolean keepShow) {
        if (jmsg.getReplyTo() != 0) {
            JuickMessage reply = jmsg;
            LinearLayout ll = new LinearLayout(getActivity());
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            int totalCount = 0;
            while (reply != null) {
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
                if (!keepShow || shownThreadToast.getView().getParent() == null) { // new or already hidden
                    shownThreadToast = new Toast(getActivity());
                    shownThreadToast.setView(ll);
                    shownThreadToast.setGravity(Gravity.BOTTOM | Gravity.LEFT, 0, bottomSize);
                    shownThreadToast.setDuration(Toast.LENGTH_LONG);
                }
                shownThreadToast.show();
            }
        }
    }


    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }

    public boolean onBackPressed() {
        if (isNavigationMenuShown()) {
            closeNavigationMenu();
            return true;
        }
        return false;  //To change body of created methods use File | Settings | File Templates.
    }

    private boolean isNavigationMenuShown() {
        return navigationMenuShown;
    }

    public void scrollMessages(int delta) {
        String scollMode = sp.getString("keyScrollMode", "page");
        ListView lv = getListView();
        if (lv.getChildCount() == 1 && scollMode.equals("message")) scollMode = "page";
        if (scollMode.equals("message")) {
            int firstVisiblePosition = lv.getFirstVisiblePosition();
            if (delta == +1) {
                if (firstVisiblePosition == 0) {
                    firstVisiblePosition++; // list separator
                }
                lv.setSelection(firstVisiblePosition + 1);
            } else {
                if (firstVisiblePosition != 0) {
                    lv.setSelection(firstVisiblePosition - 1);
                }
            }
        }
        if (scollMode.equals("page")) {
            if (delta == +1) {
                lv.smoothScrollBy((int)(lv.getHeight() * 0.93), 200);
            } else {
                lv.smoothScrollBy(-(int)(lv.getHeight() * 0.93), 200);
            }
        }
    }




}
