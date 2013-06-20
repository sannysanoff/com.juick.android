/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 * Copyright (C) 2011 Johan Nilsson <https://github.com/johannilsson/android-pulltorefresh>
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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.*;
import com.juick.android.ja.JAUnansweredMessagesSource;
import com.juick.android.ja.Network;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import org.acra.ACRA;
import org.apache.http.client.HttpClient;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Ugnich Anton
 */
public class MessagesFragment extends ListFragment implements AdapterView.OnItemClickListener, AbsListView.OnScrollListener, View.OnTouchListener, View.OnClickListener {

    public static int instanceCount;

    {
        instanceCount++;
    }

    public JuickMessagesAdapter listAdapter;
    private View viewLoading;
    private boolean loading = true;
    // Pull to refresh
    private static final int TAP_TO_REFRESH = 1;
    private static final int PULL_TO_REFRESH = 2;
    private static final int RELEASE_TO_REFRESH = 3;
    private static final int REFRESHING = 4;
    private RelativeLayout mRefreshView;
    private TextView mRefreshViewText;
    private ImageView mRefreshViewImage;
    private ProgressBar mRefreshViewProgress;
    private int mCurrentScrollState;
    private int mRefreshState;
    private RotateAnimation mFlipAnimation;
    private RotateAnimation mReverseFlipAnimation;
    private int mRefreshViewHeight;
    private int mRefreshOriginalTopPadding;
    private float mLastMotionY;
    private float mLastMotionX;
    private float mInitialMotionX;
    private int mActivePointerId;
    private int mScrollState = SCROLL_STATE_IDLE;
    private static final int INVALID_POINTER = -1;
    public static final int SCROLL_STATE_DRAGGING = 10;
    public static final int SCROLL_STATE_SETTLING = 11;


    private boolean mBounceHack;
    private ScaleGestureDetector mScaleDetector = null;
    private GestureDetector gestureDetector = null;
    SharedPreferences sp;

    Handler handler;
    private Object restoreData;
    boolean implicitlyCreated;
    MessageID topMessageId = null;
    boolean allMessages = false;

    Utils.ServiceGetter<DatabaseService> databaseGetter;
    boolean trackLastRead = false;
    private JuickFragmentActivity parent;
    private Runnable doOnClick;
    private long doOnClickActualTime;


    /**
     * this mode launches long click menu upon touch_up (it must happen inside proper time frame).
     * this was required to fix some strange problem (which is gone now) when dlg that appeared got clicked immediately under same finger
     */
    private boolean alternativeLongClick;
    private JuickMessage preventClickOn;
    private int mTouchSlop;
    private float rightScrollBound;

    public MessagesFragment(Object restoreData, JuickFragmentActivity parent) {
        this.restoreData = restoreData;
        implicitlyCreated = false;
        this.parent = parent;
    }

    public MessagesFragment() {
        implicitlyCreated = true;
    }


    MessagesSource messagesSource;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ViewConfiguration configuration = ViewConfiguration.get(getActivity());
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        databaseGetter = new Utils.ServiceGetter<DatabaseService>(getActivity(), DatabaseService.class);
        handler = new Handler();
        sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        trackLastRead = sp.getBoolean("lastReadMessages", false);
        alternativeLongClick = sp.getBoolean("alternativeLongClick", false);

        Bundle args = getArguments();

        if (args != null) {
            messagesSource = (MessagesSource) args.getSerializable("messagesSource");
        }

        if (messagesSource == null)
            messagesSource = new JuickCompatibleURLMessagesSource(getActivity(), "dummy");
        if (messagesSource.getContext() == null)
            messagesSource.setContext(JuickAdvancedApplication.instance);
        mFlipAnimation = new RotateAnimation(0, -180,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mFlipAnimation.setInterpolator(new LinearInterpolator());
        mFlipAnimation.setDuration(250);
        mFlipAnimation.setFillAfter(true);
        mReverseFlipAnimation = new RotateAnimation(-180, 0,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mReverseFlipAnimation.setInterpolator(new LinearInterpolator());
        mReverseFlipAnimation.setDuration(250);
        mReverseFlipAnimation.setFillAfter(true);

        if (Build.VERSION.SDK_INT >= 8) {
            mScaleDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());
        }

    }

    boolean prefetchMessages = false;

    @Override
    public void onResume() {
        prefetchMessages = sp.getBoolean("prefetchMessages", false);
        startTime = System.currentTimeMillis();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    long startTime = 0;

    @Override
    public void onPause() {
        super.onPause();
        long usedTime = System.currentTimeMillis() - startTime;
        new WhatsNew(getActivity()).increaseUsage(this.getListView().getContext(), "activity_time_" + messagesSource.getKind(), usedTime);
    }

    @Override
    public void onStart() {
        super.onStart();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.fragment_view, null);
        MainActivity.restyleChildrenOrWidget(inflate);
        return inflate;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LayoutInflater li = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        viewLoading = li.inflate(R.layout.listitem_loading, null);
        if (!messagesSource.canNext()) {
            viewLoading.findViewById(R.id.loadingg).setVisibility(View.GONE);
            viewLoading.findViewById(R.id.end_of_messages).setVisibility(View.VISIBLE);
            viewLoading.findViewById(R.id.progress_bar).setVisibility(View.GONE);
            viewLoading.findViewById(R.id.progress_loading_more).setVisibility(View.GONE);
        }

        mRefreshView = (RelativeLayout) li.inflate(R.layout.pull_to_refresh_header, null);
        mRefreshViewText = (TextView) mRefreshView.findViewById(R.id.pull_to_refresh_text);
        mRefreshViewImage = (ImageView) mRefreshView.findViewById(R.id.pull_to_refresh_image);
        mRefreshViewProgress = (ProgressBar) mRefreshView.findViewById(R.id.pull_to_refresh_progress);
        mRefreshViewImage.setMinimumHeight(50);
        mRefreshView.setOnClickListener(this);
        mRefreshOriginalTopPadding = mRefreshView.getPaddingTop();
        mRefreshState = TAP_TO_REFRESH;

        final ListView listView = getListView();
        listView.setBackgroundDrawable(null);
        listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        installDividerColor(listView);
        MainActivity.restyleChildrenOrWidget(listView);


        listAdapter = new JuickMessagesAdapter(getActivity(), JuickMessagesAdapter.TYPE_MESSAGES, allMessages ? JuickMessagesAdapter.SUBTYPE_ALL : JuickMessagesAdapter.SUBTYPE_OTHER);

        listAdapter.setOnForgetListener(new Utils.Function<Void,JuickMessage>() {
            @Override
            public Void apply(final JuickMessage jm) {
                Network.executeJAHTTPS(getActivity(), null, "https://ja.ip.rt.ru:8444/api/pending?command=ignore&mid=" + ((JuickMessageID) jm.getMID()).getMid() + "&rid=" + jm.getRID(), new Utils.Function<Void, Utils.RESTResponse>() {
                    @Override
                    public Void apply(final Utils.RESTResponse response) {
                        if (response.getErrorText() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), response.getErrorText(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    listAdapter.remove(jm);
                                    //To change body of implemented methods use File | Settings | File Templates.
                                    if (listAdapter.getCount() == 0) {
                                        if ((getActivity() instanceof MainActivity)) {
                                            ((MainActivity)getActivity()).doReload();
                                        }
                                    }
                                }
                            });
                        }
                        return null;
                    }
                });
                return null;
            }
        });
        listView.setOnTouchListener(this);
        listView.setOnScrollListener(this);
        listView.setOnItemClickListener(this);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                if (view instanceof ImageGallery) {
                    return false;   // no need that! (possibly, make this condition work only if not scrolled meanwhile)
                }
                final Object itemAtPosition = parent.getItemAtPosition(position);
                if (itemAtPosition instanceof JuickMessage) {
                    doOnClickActualTime = System.currentTimeMillis();
                    doOnClick = new Runnable() {
                        @Override
                        public void run() {
                            JuickMessage msg = (JuickMessage) itemAtPosition;
                            MessageMenu messageMenu = MainActivity.getMicroBlog(msg).getMessageMenu(getActivity(), messagesSource, listView, listAdapter);
                            messageMenu.onItemLongClick(parent, view, position, id);
                        }
                    };
                    if (alternativeLongClick) {
                        listView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } else {
                        doOnClick.run();
                        doOnClick = null;
                        return true;
                    }
                }
                return false;
            }
        });
        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                System.out.println();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                System.out.println();
            }
        });
        init(false);
        if (parent != null) {
            parent.onFragmentCreated();
        }
    }

    //    private MessageMenu openMessageMenu(ListView listView) {
//        return messagesSource.getMicroBlog().getMessageMenu(getActivity(), messagesSource, listView, listAdapter);
//    }
//
    public static void installDividerColor(ListView listView) {
        ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(listView.getContext());
        ColorDrawable divider = new ColorDrawable(colorTheme.getColor(ColorsTheme.ColorKey.DIVIDER, 0xFF808080));
        listView.setDivider(divider);
        listView.setDividerHeight(1);
    }

    private void init(final boolean moveToTop) {
        if (implicitlyCreated) return;

        parent.imagePreviewHelper = listAdapter.imagePreviewHelper = new ImagePreviewHelper((ViewGroup) getView().findViewById(R.id.imagepreview_container), parent);

        final MessageListBackingData savedMainList = JuickAdvancedApplication.instance.getSavedList(getActivity());
        final ListView lv = getListView();
        boolean canUseMainList = getActivity() instanceof MainActivity; //
        if (savedMainList != null && canUseMainList) {
            messagesSource = savedMainList.messagesSource;
            initListWithMessages(savedMainList.messages);
            int selectItem = 0;
            ListAdapter wrappedAdapter = lv.getAdapter();
            for (int i = 0; i < wrappedAdapter.getCount(); i++) {
                Object ai = wrappedAdapter.getItem(i);
                if (ai != null && ai instanceof JuickMessage) {
                    if (((JuickMessage) ai).getMID().equals(savedMainList.topMessageId)) {
                        selectItem = i;
                    }
                }
            }
            lv.setSelectionFromTop(selectItem, savedMainList.topMessageScrollPos);
            JuickAdvancedApplication.instance.setSavedList(null, false);
        } else {
            final MessagesLoadNotification messagesLoadNotification = new MessagesLoadNotification(getActivity(), handler);
            Thread thr = new Thread("Download messages (init)") {

                public void run() {
                    final MessagesLoadNotification notification = messagesLoadNotification;
                    final Utils.Function<Void, RetainedData> then = new Utils.Function<Void, RetainedData>() {
                        @Override
                        public Void apply(final RetainedData mespos) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    notification.statusText.setText("Filter and format..");
                                }
                            });
                            Log.w("com.juick.advanced", "getFirst: before filter");
                            final ArrayList<JuickMessage> messages = filterMessages(mespos.messages);
                            Log.w("com.juick.advanced", "getFirst: after filter");
                            if (!JuickMessagesAdapter.dontKeepParsed(parent)) {
                                for (JuickMessage juickMessage : messages) {
                                    juickMessage.parsedText = JuickMessagesAdapter.formatMessageText(parent, juickMessage, false);
                                }
                            }
                            final Parcelable listPosition = mespos.viewState;
                            if (isAdded()) {
                                if (messages.size() == 0) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (notification.lastError == null) {
                                                notification.statusText.setText(parent.getString(R.string.EmptyList));
                                            } else {
                                                notification.statusText.setText("Error obtaining messages: " + notification.lastError);

                                            }
                                            notification.progressBar.setVisibility(View.GONE);
                                        }
                                    });
                                }
                                final Activity activity = getActivity();
                                if (activity != null) {
                                    final Parcelable finalListPosition = listPosition;
                                    activity.runOnUiThread(new Runnable() {

                                        public void run() {
                                            try {
                                                if (isAdded()) {
                                                    initListWithMessages(messages);
                                                    if (moveToTop) {
                                                        lv.setSelection(0);
                                                    } else {
                                                        if (finalListPosition != null) {
                                                            lv.onRestoreInstanceState(finalListPosition);
                                                        } else {
                                                            setSelection(messagesSource.supportsBackwardRefresh() ? 1 : 0);
                                                        }
                                                    }
                                                    Log.w("com.juick.advanced", "getFirst: end.");
                                                    handler.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            onListLoaded();
                                                        }
                                                    }, 10);
                                                }
                                            } catch (IllegalStateException e) {
                                                Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    });
                                }
                            } else {
                                Log.w("com.juick.advanced", "getFirst: not added!");
                            }
                            return null;
                        }
                    };
                    if (restoreData == null) {
                        if (getActivity() != null)
                            messagesSource.setContext(getActivity());
                        messagesSource.getFirst(notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                            @Override
                            public Void apply(ArrayList<JuickMessage> juickMessages) {
                                return then.apply(new RetainedData(juickMessages, null));
                            }
                        });
                    } else {
                        then.apply((RetainedData) restoreData);
                        restoreData = null;
                    }
                }
            };
            thr.start();
        }
    }

    protected void onListLoaded() {
//        if (messagesSource instanceof JAUnansweredMessagesSource && !sp.getBoolean("MessagesFragmentSlideInfoDisplayed", false)) {
//            sp.edit().putBoolean("MessagesFragmentSlideInfoDisplayed", true).commit();
//            new AlertDialog.Builder(getActivity())
//                    .setTitle(getActivity().getString(R.string.SlideToDeleteItems))
//                    .setMessage(getActivity().getString(R.string.UnneededItems_))
//                    .setIcon(R.drawable.mobile_gesture)
//                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
//                        @Override
//                        public void onCancel(DialogInterface dialog) {
//                            //To change body of implemented methods use File | Settings | File Templates.
//                        }
//                    }).show();
//        }
    }

    private void initListWithMessages(ArrayList<JuickMessage> messages) {
        if (messages.size() != 0) {
            Log.w("com.juick.advanced", "getFirst: in ui thread!");
            listAdapter.clear();
            listAdapter.addAllMessages(messages);
            Log.w("com.juick.advanced", "getFirst: added all");
            if (getListView().getFooterViewsCount() == 0) {
                getListView().addFooterView(viewLoading, null, false);
                Log.w("com.juick.advanced", "getFirst: added footer");
            }
            topMessageId = messages.get(0).getMID();
        } else {
            topMessageId = null;
        }

        if (getListView().getHeaderViewsCount() == 0 && messagesSource.supportsBackwardRefresh()) {
            getListView().addHeaderView(mRefreshView, null, false);
            mRefreshViewHeight = mRefreshView.getMeasuredHeight();
        }

        if (getListAdapter() != listAdapter) {
            setListAdapter(listAdapter);
            Log.w("com.juick.advanced", "getFirst: adapter set");
        }

        loading = false;
        resetHeader();
        Log.w("com.juick.advanced", "getFirst: header reset");
        getListView().invalidateViews();
        Log.w("com.juick.advanced", "getFirst: invalidated views");
        getListView().setRecyclerListener(new AbsListView.RecyclerListener() {
            @Override
            public void onMovedToScrapHeap(View view) {
                listAdapter.recycleView(view);
            }
        });
    }

    public void test() {
        JuickMessage item = listAdapter.getItem(2);
        listAdapter.remove(item);

    }

    public MessageListBackingData getMessageListBackingData() {
        MessageListBackingData mlbd = new MessageListBackingData();
        mlbd.messagesSource = messagesSource;
        mlbd.topMessageId = topMessageId;
        ListView lv = null;
        try {
            lv = getListView();
            ListAdapter adapter = lv.getAdapter();
            int firstVisiblePosition = lv.getFirstVisiblePosition();
            JuickMessage jm = (JuickMessage) adapter.getItem(firstVisiblePosition);
            mlbd.topMessageId = jm.getMID();
            for (int i = 0; i < lv.getChildCount(); i++) {
                View thatView = lv.getChildAt(i);
                int positionForView = lv.getPositionForView(thatView);
                if (positionForView == firstVisiblePosition) {
                    mlbd.topMessageScrollPos = thatView.getTop();
                    break;
                }
            }
        } catch (Exception e) {
            // various conditions
        }
        if (lv == null) {
            return null;
        }
        int firstVisiblePosition = Math.max(0, lv.getFirstVisiblePosition() - 120);
        mlbd.messages = new ArrayList<JuickMessage>();
        for (int i = firstVisiblePosition; i < listAdapter.getCount(); i++) {
            mlbd.messages.add(listAdapter.getItem(i));
        }
        return mlbd;
    }

    public void scrollMessages(int delta) {
        String scollMode = sp.getString("keyScrollMode", "page");
        ListView lv = getListView();
        if (lv.getChildCount() == 1 && scollMode.equals("message")) scollMode = "page";
        if (scollMode.equals("message")) {
            int firstVisiblePosition = lv.getFirstVisiblePosition();
            if (delta == +1) {
                lv.setSelection(firstVisiblePosition + 1);
            } else {
                if (firstVisiblePosition != 0) {
                    lv.setSelection(firstVisiblePosition - 1);
                }
            }
        }
        if (scollMode.equals("page")) {
            if (delta == +1) {
                lv.smoothScrollBy((int) (lv.getHeight() * 0.93), 200);
            } else {
                lv.smoothScrollBy(-(int) (lv.getHeight() * 0.93), 200);
            }
        }
    }

    boolean navigationOpenMode = false;

    public Boolean maybeInterceptTouchEventFromActivity(MotionEvent event) {
        if (rightScrollBound == 0) {
            Log.w("JAGP","rightScrollBound == 0");
            return null;
        }
        int action = event.getAction();
        int actionMasked = event.getActionMasked();
        final View frag = getActivity().findViewById(R.id.messagesfragment);
        if (action == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            if (mScrollState == SCROLL_STATE_IDLE && frag.getAnimation() == null) {
                if (event.getPointerCount() == 1) {
                    Log.w("JAGP","action_down 1");
                    navigationOpenMode = frag.getLeft() > 0;
                    mLastMotionX = mInitialMotionX = event.getX();
                    mLastMotionY = event.getY();
                    currentScrollX = 0;
                    mActivePointerId = MotionEventCompat.getPointerId(event, 0);
                    mIsBeingDragged = false;
                    mIsUnableToDrag = false;
                } else {
                    Log.w("JAGP","action_down 2");
                    if (mIsBeingDragged) {
                        if (!navigationOpenMode) {
                            Log.w("JAGP","action_down 3");
                            setScrollState(SCROLL_STATE_IDLE);
                            scrollToX(0, 500);
                        }
                    }
                    mIsUnableToDrag = true;
                }
            } else {
                Log.w("JAGP","!(mScrollState == SCROLL_STATE_IDLE && frag.getAnimation() == null)");
            }
        }
        if (action == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_POINTER_UP) {
            if (mIsUnableToDrag) return null;
            if (mIsBeingDragged) {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    return null;
                }
                int pointerIndex = MotionEventCompat.findPointerIndex(event, activePointerId);
                if (pointerIndex == event.getActionIndex()) {
                    if (!navigationOpenMode) {
                        if (Math.abs(lastToXDelta) < rightScrollBound/2) {
                            Log.w("JAGP","action_up 1");
                            setScrollState(SCROLL_STATE_SETTLING);
                            scrollToX(0, 500);
                        } else {
                            Log.w("JAGP","action_up 2");
                            setScrollState(SCROLL_STATE_SETTLING);
                            scrollToX((int) -rightScrollBound, 200);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.w("JAGP","action_up 3");
                                    ((MainActivity) getActivity()).openNavigationMenu(false);
                                    lastToXDelta = 0;
                                    frag.clearAnimation();
                                }
                            }, 200);
                        }
                    } else {
                        Log.w("JAGP","action_up 4");
                        mIsBeingDragged = false;
                        setScrollState(SCROLL_STATE_SETTLING);
                        scrollToX((int)rightScrollBound, 200);
                        mActivePointerId = INVALID_POINTER;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.w("JAGP","action_up 5");
                                ((MainActivity) getActivity()).closeNavigationMenu(false);
                                lastToXDelta = 0;
                                frag.clearAnimation();
                            }
                        }, 200);

                    }
                    return null;
                }
            }
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mIsUnableToDrag) return null;
            if (mScrollState == SCROLL_STATE_SETTLING) return null;

            /*
             * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
             * whether the user has moved far enough from his original down touch.
             */

            /*
            * Locally do absolute value. mLastMotionY is set to the y value
            * of the down event.
            */
            MotionEvent ev = event;
            final int activePointerId = mActivePointerId;
            if (activePointerId == INVALID_POINTER) {
                // If we don't have a valid id, the touch down wasn't on content.
                return null;
            }

            int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
            float x = MotionEventCompat.getX(ev, pointerIndex);
            float dx = x - mLastMotionX;
            float xDiff = Math.abs(dx);
            float yDiff = Math.abs(MotionEventCompat.getY(ev, pointerIndex) - mLastMotionY);

            if (!mIsBeingDragged) {
                if (isListAnyPressed()) {
                    //Log.w("JAGP","action_move 1");
                    mIsUnableToDrag = true;
                } else if (xDiff > mTouchSlop && xDiff > yDiff) {
                    mIsBeingDragged = true;
                    setScrollState(SCROLL_STATE_DRAGGING);
                    mLastMotionX = x;
                    //Log.w("JAGP","action_move 2");
                    return null;
                } else {
                    if (yDiff > mTouchSlop) {
                        // The finger has moved enough in the vertical
                        // direction to be counted as a drag...  abort
                        // any attempt to drag horizontally, to work correctly
                        // with children that have scrolling containers.
                        //Log.w("JAGP","action_move 3");
                        mIsUnableToDrag = true;
                    }
                }
            }


            if (mIsBeingDragged) {
                //Log.w("JAGP","action_move 4");
                // Scroll to follow the motion event
                final int activePointerIndex = MotionEventCompat.findPointerIndex(
                        ev, mActivePointerId);
                x = MotionEventCompat.getX(ev, activePointerIndex);
                final float deltaX = mLastMotionX - x;
                mLastMotionX = x;
                float oldScrollX = getScrollX();
                float scrollX = oldScrollX + deltaX;
                final int width = getListView().getWidth();
                final int widthWithMargin = width;

                final float leftBound = -widthWithMargin;
                if (navigationOpenMode) {
                    if (scrollX < 0) {
                        scrollX = 0;
                    } else if (scrollX > rightScrollBound) {    // prevent too much to left
                        scrollX = rightScrollBound;
                    }
                } else {
                    if (scrollX > 0) {
                        scrollX = 0;
                    } else if (scrollX > rightScrollBound) {
                        scrollX = rightScrollBound;
                    }
                }
                // Don't lose the rounded component
                mLastMotionX += scrollX - (int) scrollX;
                scrollToX((int) scrollX, 1);
                return null;
            }


        }
        return null;
    }

    public static class RetainedData {
        ArrayList<JuickMessage> messages;
        Parcelable viewState;

        RetainedData(ArrayList<JuickMessage> messages, Parcelable viewState) {
            this.messages = messages;
            this.viewState = viewState;
        }
    }

    public Object saveState() {
        try {
            ListView listView = getListView();
            RetainedData rd = new RetainedData(new ArrayList<JuickMessage>(), listView.onSaveInstanceState());
            int count = listAdapter.getCount();
            for (int i = 0; i < count; i++) {
                rd.messages.add(listAdapter.getItem(i));
            }
            return rd;
        } catch (IllegalStateException e) {
            // view not yet created
            return null;
        }
    }

    public class MoreMessagesLoadNotification implements Utils.DownloadProgressNotification, Utils.RetryNotification, Utils.DownloadErrorNotification {

        TextView progress;
        int retry = 0;
        String lastError = null;
        Activity activity;
        private int progressBytes;
        public final TextView loadingg;
        ProgressBar progressBar;

        public MoreMessagesLoadNotification() {
            progress = (TextView) viewLoading.findViewById(R.id.progress_loading_more);
            progressBar = (ProgressBar) viewLoading.findViewById(R.id.progress_bar);
            loadingg = (TextView) viewLoading.findViewById(R.id.loadingg);
            progress.setText("");
            ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(activity);
            progress.setTextColor(colorTheme.getColor(ColorsTheme.ColorKey.COMMON_FOREGROUND, 0xFF000000));
            activity = getActivity();
            loadingg.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void notifyDownloadError(final String error) {
            lastError = error;
        }

        @Override
        public void notifyHttpClientObtained(HttpClient client) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void notifyDownloadProgress(int progressBytes) {
            this.progressBytes = progressBytes;
            updateProgressText();
        }

        private void updateProgressText() {
            String text = " " + this.progressBytes / 1024 + "K";
            if (retry > 0) {
                text += " (retry " + (retry + 1) + ")";
            }
            final String finalText = text;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress.setText(finalText);
                }
            });
        }

        @Override
        public void notifyRetryIsInProgress(int retry) {
            this.retry = retry;
            updateProgressText();
        }
    }

    private void loadMore() {

        if (getView() == null) {
            // not ready yet (or finished)
            return;
        }
        loading = true;
        restoreData = null;

        final MoreMessagesLoadNotification progressNotification = new MoreMessagesLoadNotification();
        Thread thr = new Thread("Download messages (more)") {

            public void run() {
                final Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    try {
                        messagesSource.getNext(progressNotification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                            @Override
                            public Void apply(final ArrayList<JuickMessage> messages) {
                                final ArrayList<JuickMessage> messagesFiltered = filterMessages(messages);
                                if (!JuickMessagesAdapter.dontKeepParsed(parent)) {
                                    for (JuickMessage juickMessage : messagesFiltered) {
                                        juickMessage.parsedText = JuickMessagesAdapter.formatMessageText(activity, juickMessage, false);
                                    }
                                }
                                activity.runOnUiThread(new Runnable() {

                                    public void run() {
                                        progressNotification.loadingg.setVisibility(View.GONE);
                                        progressNotification.progressBar.setVisibility(View.GONE);
                                        if (messages.size() == 0) {
                                            progressNotification.progress.setText(progressNotification.lastError);
                                        }
                                        if (getView() == null) return;  // already closed?
                                        MyListView parent = (MyListView) getListView();

                                        if (getListView().getAdapter().getCount() - (recentFirstVisibleItem + recentVisibleItemCount) > 3) {
                                            parent.blockLayoutRequests = true;  // a nafig nam layout, at least 3 items below?
                                        }
                                        try {
                                            listAdapter.addAllMessages(messagesFiltered);
                                        } finally {
                                            parent.blockLayoutRequests = false;
                                        }
                                        loading = false;
                                    }
                                });
                                return null;  //To change body of implemented methods use File | Settings | File Templates.
                            }
                        });
                    } catch (OutOfMemoryError e) {
                        messagesSource.setCanNext(false);
                        ACRA.getErrorReporter().handleException(new RuntimeException("OOM: " + XMPPControlActivity.getMemoryStatusString(), e));
                        progressNotification.notifyDownloadError("OUT OF MEMORY");
                    }
                }
            }
        };
        thr.start();
    }

    void log(String str) {
        Log.w("com.juickadvanced", "MessagesFragment: " + str);
    }

    static BitSet russians = new BitSet();

    static {
        String russian = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
        russian = russian + russian.toLowerCase();
        for (int i = 0; i < russian.length(); i++) {
            int code = (int) russian.charAt(i);
            russians.set(code);
        }
    }

    static Pattern httpURL = Pattern.compile("http(\\S*)");


    private ArrayList<JuickMessage> filterMessages(ArrayList<JuickMessage> messages) {
        log("filterMessages start");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(JuickAdvancedApplication.instance.getApplicationContext());
        boolean filterNonRussian = sp.getBoolean("filterNonRussian", false);
        Set<String> filteredOutUsers1 = JuickMessagesAdapter.getFilteredOutUsers(parent);
        log("filterMessages got filtered out users");
        for (Iterator<JuickMessage> iterator = messages.iterator(); iterator.hasNext(); ) {
            JuickMessage message = iterator.next();
            if (filteredOutUsers1.contains(message.User.UName)) {
                iterator.remove();
                continue;
            }
            if (message.getRID() == 0) {
                // don't check comments
                if (XMPPService.getAnyJuboMessageFilter() != null) {
                    if (!XMPPService.getAnyJuboMessageFilter().allowMessage(message)) {
                        iterator.remove();
                        continue;
                    }
                }
                if (XMPPService.getAnyJuickBlacklist() != null) {
                    if (!XMPPService.getAnyJuickBlacklist().allowMessage(message)) {
                        iterator.remove();
                        continue;
                    }
                }
            }
            if (filterNonRussian) {
                String text = message.Text;
                int nRussian = 0;
                while (true) {
                    boolean replaced = false;
                    Matcher matcher = httpURL.matcher(text);
                    if (matcher.find()) {
                        try {
                            text = matcher.replaceAll("");
                            replaced = true;
                        } catch (Exception e) {
                            //
                        }
                    }
                    if (!replaced) break;
                }
                final int limit = text.length();
                for (int i = 0; i < limit; i++) {
                    int charCode = (int) text.charAt(i);
                    if (russians.get(charCode)) {
                        nRussian++;
                        break;
                    }
                }
                if (!text.contains("No description")) {
                    if (nRussian == 0 && limit > 30) {
                        iterator.remove();
                        continue;
                    }
                }
            }
        }
        log("filterMessages end");
        return messages;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JuickMessage jmsg = (JuickMessage) parent.getItemAtPosition(position);
        if (jmsg == preventClickOn)
            return;
        if (doOnClick != null) {
            if (System.currentTimeMillis() < doOnClickActualTime + 1000) {
                doOnClick.run();
            }
            doOnClick = null;
            return;
        }
        Intent i = new Intent(getActivity(), ThreadActivity.class);
        i.putExtra("mid", jmsg.getMID());
        i.putExtra("messagesSource", messagesSource);
        if (jmsg.contextPost != null && messagesSource instanceof JAUnansweredMessagesSource) {
            i.putExtra("prefetched", jmsg);
        }
        startActivity(i);
    }

    // Refresh
    public void onClick(View view) {
        mRefreshState = REFRESHING;
        prepareForRefresh();
        init(true);
    }

    @Override
    public void onStop() {
        if (topMessageId != null) {
            messagesSource.rememberSavedPosition(topMessageId);
        }
        super.onStop();
    }

    int lastItemReported = 0;

    int recentFirstVisibleItem;
    int recentVisibleItemCount;

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        recentFirstVisibleItem = firstVisibleItem;
        recentVisibleItemCount = visibleItemCount;
        int prefetchMessagesSize = prefetchMessages ? 20 : 0;
        if (visibleItemCount < totalItemCount && (firstVisibleItem + visibleItemCount >= totalItemCount - prefetchMessagesSize) && loading == false) {
            if (messagesSource.canNext()) {
                loadMore();
            }
        }
        try {
            JuickMessage jm;
            if (firstVisibleItem != 0) {
                ListAdapter listAdapter = getListAdapter();
                jm = (JuickMessage) listAdapter.getItem(firstVisibleItem - 1);
                topMessageId = jm.getMID();
                if (firstVisibleItem > 1 && trackLastRead) {
                    final int itemToReport = firstVisibleItem - 1;
                    if (lastItemReported < itemToReport) {
                        for (lastItemReported++; lastItemReported <= itemToReport; lastItemReported++) {
                            final int itemToSave = lastItemReported;
                            if (itemToSave - 1 < listAdapter.getCount()) {  // some async delete could happen
                                final JuickMessage item = (JuickMessage) listAdapter.getItem(itemToSave - 1);
                                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                    @Override
                                    public void withService(DatabaseService service) {
                                        service.markAsRead(new DatabaseService.ReadMarker(item.getMID(), item.replies, item.Timestamp.getTime()));
                                    }

                                });
                            }
                        }
                        lastItemReported--;
                    }
                }
            } else {
                if (getListAdapter() != null) {
                    jm = (JuickMessage) getListAdapter().getItem(firstVisibleItem);
                    if (topMessageId instanceof JuickMessageID) {
                        ((JuickMessageID) topMessageId).getNextMid(); // open/closed interval
                    } else {
                        topMessageId = jm.getMID(); // dunno here
                    }
                }
            }
        } catch (Exception ex) {
        }

        if (messagesSource.supportsBackwardRefresh()) {
            // When the refresh view is completely visible, change the text to say
            // "Release to refresh..." and flip the arrow drawable.
            if (mCurrentScrollState == SCROLL_STATE_TOUCH_SCROLL
                    && mRefreshState != REFRESHING) {
                if (firstVisibleItem == 0) {
                    mRefreshViewImage.setVisibility(View.VISIBLE);
                    if ((mRefreshView.getBottom() >= mRefreshViewHeight + 20
                            || mRefreshView.getTop() >= 0)
                            && mRefreshState != RELEASE_TO_REFRESH) {
                        mRefreshViewText.setText(R.string.pull_to_refresh_release_label);
                        mRefreshViewImage.clearAnimation();
                        mRefreshViewImage.startAnimation(mFlipAnimation);
                        mRefreshState = RELEASE_TO_REFRESH;
                    } else if (mRefreshView.getBottom() < mRefreshViewHeight + 20
                            && mRefreshState != PULL_TO_REFRESH) {
                        mRefreshViewText.setText(R.string.pull_to_refresh_pull_label);
                        if (mRefreshState != TAP_TO_REFRESH) {
                            mRefreshViewImage.clearAnimation();
                            mRefreshViewImage.startAnimation(mReverseFlipAnimation);
                        }
                        mRefreshState = PULL_TO_REFRESH;
                    }
                } else {
                    mRefreshViewImage.setVisibility(View.GONE);
                    resetHeader();
                }
            } else if (mCurrentScrollState == SCROLL_STATE_FLING
                    && firstVisibleItem == 0
                    && mRefreshState != REFRESHING) {
                try {
                    setSelection(1);
                } catch (Exception e) {
                    // Content view is not yet created
                }
                mBounceHack = true;
            } else if (mBounceHack && mCurrentScrollState == SCROLL_STATE_FLING) {
                setSelection(1);
            }
        }
    }

    boolean mIsBeingDragged;
    boolean mIsUnableToDrag;

    public boolean onTouch(View view, MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
        }
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }

        final int y = (int) event.getY();
        mBounceHack = false;

        int action = event.getAction();

        // Nothing more to do here if we have decided whether or not we
        // are dragging.
        if (action != MotionEvent.ACTION_DOWN) {
            if (mIsBeingDragged) {
                return true;
            }
            if (mIsUnableToDrag) {
                return false;
            }
        }

        switch (action) {
            case MotionEvent.ACTION_UP:
                if (!getListView().isVerticalScrollBarEnabled()) {
                    getListView().setVerticalScrollBarEnabled(true);
                }
                if (messagesSource.supportsBackwardRefresh()) {
                    if (getListView().getFirstVisiblePosition() == 0 && mRefreshState != REFRESHING) {
                        if ((mRefreshView.getBottom() >= mRefreshViewHeight
                                || mRefreshView.getTop() >= 0)
                                && mRefreshState == RELEASE_TO_REFRESH) {
                            // Initiate the refresh
                            onClick(getListView());
                        } else if (mRefreshView.getBottom() < mRefreshViewHeight
                                || mRefreshView.getTop() <= 0) {
                            // Abort refresh and scroll down below the refresh view
                            resetHeader();
                            setSelection(1);
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE: {

                if (doOnClick != null) {
                    return true;
                }
                applyHeaderPadding(event);
                break;
            }
        }
        if (parent.onListTouchEvent(view, event)) return true;
        return false;
    }

    int currentScrollX = 0;

    Integer lastToXDelta;
    public void scrollToX(int scrollX, long duration) {
        currentScrollX = scrollX;
        final View frag = getActivity().findViewById(R.id.messagesfragment);
        TranslateAnimation ta = new TranslateAnimation(lastToXDelta != null ? lastToXDelta : 0, -scrollX, 0, 0);
        lastToXDelta = -scrollX;
        ta.setFillEnabled(true);
        ta.setDuration(duration);
        ta.setFillAfter(true);
        ta.setFillBefore(true);
        if (duration > 2) {
            ta.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mScrollState == SCROLL_STATE_SETTLING) {
                        setScrollState(SCROLL_STATE_IDLE);
                        frag.clearAnimation();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
        }
        frag.startAnimation(ta);
    }

    public int getScrollX() {
        return currentScrollX;
    }

    Runnable reenabler = new Runnable() {
        @Override
        public void run() {
            setListActionsEnabled(true);
        }
    };

    private void setScrollState(int newState) {
        if (mScrollState == newState) {
            return;
        }
        if (newState != SCROLL_STATE_IDLE) {
            handler.removeCallbacks(reenabler);
            setListActionsEnabled(false);
        } else {
            handler.postDelayed(reenabler, 500);
        }
        mScrollState = newState;
    }

    private void setListActionsEnabled(boolean enabled) {
        int limit = getListView().getChildCount();
        for(int i=0; i<limit; i++) {
            View childAt = getListView().getChildAt(i);
            if (childAt instanceof PressableLinearLayout) {
                PressableLinearLayout pc = (PressableLinearLayout)childAt;
                pc.setOverrideHasFocusable(enabled ? null : true);
            }
        }
    }

    private boolean isListAnyPressed() {
        int limit = getListView().getChildCount();
        for(int i=0; i<limit; i++) {
            View childAt = getListView().getChildAt(i);
            if (childAt instanceof PressableLinearLayout) {
                PressableLinearLayout pc = (PressableLinearLayout)childAt;
                if (pc.isPressed()) return true;
            }
        }
        return false;
    }

    private void applyHeaderPadding(MotionEvent ev) {
        // getHistorySize has been available since API 1
        int pointerCount = ev.getHistorySize();

        for (int p = 0; p < pointerCount; p++) {
            if (mRefreshState == RELEASE_TO_REFRESH) {
                if (getListView().isVerticalFadingEdgeEnabled()) {
                    getListView().setVerticalScrollBarEnabled(false);
                }

                int historicalY = (int) ev.getHistoricalY(p);

                // Calculate the padding to apply, we divide by 1.7 to
                // simulate a more resistant effect during pull.
                int topPadding = (int) (((historicalY - mLastMotionY)
                        - mRefreshViewHeight) / 1.7);

                mRefreshView.setPadding(
                        mRefreshView.getPaddingLeft(),
                        topPadding,
                        mRefreshView.getPaddingRight(),
                        mRefreshView.getPaddingBottom());
            }
        }
    }

    /**
     * Sets the header padding back to original size.
     */
    private void resetHeaderPadding() {
        mRefreshView.setPadding(
                mRefreshView.getPaddingLeft(),
                mRefreshOriginalTopPadding,
                mRefreshView.getPaddingRight(),
                mRefreshView.getPaddingBottom());
    }

    /**
     * Resets the header to the original state.
     */
    private void resetHeader() {
        if (mRefreshState != TAP_TO_REFRESH) {
            mRefreshState = TAP_TO_REFRESH;

            resetHeaderPadding();

            // Set refresh view text to the pull label
            mRefreshViewText.setText(R.string.pull_to_refresh_tap_label);
            // Replace refresh drawable with arrow drawable
            mRefreshViewImage.setImageResource(R.drawable.ic_pulltorefresh_arrow);
            // Clear the full rotation animation
            mRefreshViewImage.clearAnimation();
            // Hide progress bar and arrow.
            mRefreshViewImage.setVisibility(View.GONE);
            mRefreshViewProgress.setVisibility(View.GONE);
        }
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        mCurrentScrollState = scrollState;

        if (mCurrentScrollState == SCROLL_STATE_IDLE) {
            mBounceHack = false;
        }
    }

    public void prepareForRefresh() {
        resetHeaderPadding();

        mRefreshViewImage.setVisibility(View.GONE);
        // We need this hack, otherwise it will keep the previous drawable.
        mRefreshViewImage.setImageDrawable(null);
        mRefreshViewProgress.setVisibility(View.VISIBLE);

        // Set refresh view text to the refreshing label
        mRefreshViewText.setText(R.string.pull_to_refresh_refreshing_label);

        mRefreshState = REFRESHING;

        clearSavedPosition(getActivity());

        MainActivity.restyleChildrenOrWidget(mRefreshView, true);
    }

    public void clearSavedPosition(Context context) {
        topMessageId = null;
        messagesSource.resetSavedPosition();
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
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }

    public void setRightScrollBound(float rightScrollBound) {
        this.rightScrollBound = rightScrollBound;
    }
}
