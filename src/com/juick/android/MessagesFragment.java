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
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.*;
import com.juick.android.api.JuickMessage;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import com.juick.android.datasource.JuickCompatibleURLMessagesSource;
import com.juick.android.datasource.MessagesSource;
import com.juickadvanced.R;
import org.apache.http.client.HttpClient;

import java.util.ArrayList;

/**
 * @author Ugnich Anton
 */
public class MessagesFragment extends ListFragment implements AdapterView.OnItemClickListener, AbsListView.OnScrollListener, View.OnTouchListener, View.OnClickListener {

    private JuickMessagesAdapter listAdapter;
    private View viewLoading;
    private boolean loading = true;
    private int page = 1;
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
    private int mLastMotionY;
    private boolean mBounceHack;
    private ScaleGestureDetector mScaleDetector = null;
    SharedPreferences sp;

    Handler handler;
    private Object restoreData;
    boolean implicitlyCreated;
    private int topMessageId = -1;
    boolean allMessages = false;

    Utils.ServiceGetter<DatabaseService> databaseGetter;
    boolean trackLastRead = false;


    public MessagesFragment(Object restoreData) {
        this.restoreData = restoreData;
        implicitlyCreated = false;
    }

    public MessagesFragment() {
        implicitlyCreated = true;
    }

    MessagesSource messagesSource;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        databaseGetter = new Utils.ServiceGetter<DatabaseService>(getActivity(), DatabaseService.class);
        handler = new Handler();
        sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        trackLastRead = sp.getBoolean("lastReadMessages", false);

        Bundle args = getArguments();

        if (args != null) {
            messagesSource = (MessagesSource)args.getSerializable("messagesSource");
        }

        if (messagesSource == null)
            messagesSource = new JuickCompatibleURLMessagesSource(getActivity());
        messagesSource.setContext(getActivity());

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
            if (sp.getBoolean("enableScaleByGesture", true)) {
                mScaleDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());
            }
        }
    }

    boolean prefetchMessages = false;
    @Override
    public void onResume() {
        prefetchMessages = sp.getBoolean("prefetchMessages", false);
        super.onResume();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        new WhatsNew(getActivity()).reportFeature("fragment_usage_"+messagesSource.getKind(), ""+totalRuntime);
    }

    long totalRuntime = 0;
    long startTime = 0;

    @Override
    public void onPause() {
        super.onPause();
        totalRuntime += System.currentTimeMillis() - startTime;
    }

    @Override
    public void onStart() {
        super.onStart();
        startTime = System.currentTimeMillis();
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view, null);
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

        ListView listView = getListView();
        installDividerColor(listView);


        listAdapter = new JuickMessagesAdapter(getActivity(), JuickMessagesAdapter.TYPE_MESSAGES, allMessages ? JuickMessagesAdapter.SUBTYPE_ALL : JuickMessagesAdapter.SUBTYPE_OTHER);
        listView.setOnTouchListener(this);
        listView.setOnScrollListener(this);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(new JuickMessageMenu(getActivity(), messagesSource, listView, listAdapter));
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
        init();
    }

    public static void installDividerColor(ListView listView) {
        ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(listView.getContext());
        ColorDrawable divider = new ColorDrawable(colorTheme.getColor(ColorsTheme.ColorKey.DIVIDER, 0xFF808080));
        listView.setDivider(divider);
        listView.setDividerHeight(1);
    }

    private void init() {
        if (implicitlyCreated) return;
        final MessagesLoadNotification messagesLoadNotification = new MessagesLoadNotification(getActivity(), handler);
        Thread thr = new Thread("Download messages (init)") {

            public void run() {
                final MessagesLoadNotification notification = messagesLoadNotification;
                final Utils.Function<Void, RetainedData> then = new Utils.Function<Void, RetainedData>() {
                    @Override
                    public Void apply(final RetainedData mespos) {
                        final ArrayList<JuickMessage> messages = mespos.messages;
                        final Parcelable listPosition = mespos.viewState;
                        if (isAdded()) {
                            if (messages.size() == 0) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        notification.statusText.setText("Download error: " + notification.lastError);
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
                                            if (messages.size() != 0) {
                                                listAdapter.clear();
                                                listAdapter.addAllMessages(messages);
                                                if (getListView().getFooterViewsCount() == 0) {
                                                    getListView().addFooterView(viewLoading, null, false);
                                                }
                                                topMessageId = messages.get(0).MID;
                                            } else {
                                                topMessageId = -1;
                                            }

                                            if (getListView().getHeaderViewsCount() == 0 && messagesSource.supportsBackwardRefresh()) {
                                                getListView().addHeaderView(mRefreshView, null, false);
                                                mRefreshViewHeight = mRefreshView.getMeasuredHeight();
                                            }

                                            if (getListAdapter() != listAdapter) {
                                                setListAdapter(listAdapter);
                                            }

                                            loading = false;
                                            resetHeader();
                                            getListView().invalidateViews();
                                            getListView().setRecyclerListener(new AbsListView.RecyclerListener() {
                                                @Override
                                                public void onMovedToScrapHeap(View view) {
                                                    listAdapter.recycleView(view);
                                                }
                                            });
                                            if (finalListPosition != null) {
                                                getListView().onRestoreInstanceState(finalListPosition);
                                            } else {
                                                setSelection(messagesSource.supportsBackwardRefresh() ? 1 : 0);
                                            }
                                        } catch (IllegalStateException e) {
                                            Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                            }
                        }
                        return null;
                    }
                };
                if (restoreData == null) {
                    messagesSource.getFirst(notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                        @Override
                        public Void apply(ArrayList<JuickMessage> juickMessages) {
                            return then.apply(new RetainedData(juickMessages, null));
                        }
                    });
                } else {
                    then.apply((RetainedData)restoreData);
                    restoreData = null;
                }
            }
        };
        thr.start();
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
        RetainedData rd = new RetainedData(new ArrayList<JuickMessage>(), getListView().onSaveInstanceState());
        int count = listAdapter.getCount();
        for (int i = 0; i < count; i++) {
            rd.messages.add(listAdapter.getItem(i));
        }
        return rd;
    }

    public class MoreMessagesLoadNotification implements Utils.DownloadProgressNotification, Utils.RetryNotification, Utils.DownloadErrorNotification {

        TextView progress;
        int retry = 0;
        String lastError = null;
        Activity activity;

        public MoreMessagesLoadNotification() {
            progress = (TextView) viewLoading.findViewById(R.id.progress_loading_more);
            progress.setText("");
            ColorsTheme.ColorTheme colorTheme = JuickMessagesAdapter.getColorTheme(activity);
            progress.setTextColor(colorTheme.getColor(ColorsTheme.ColorKey.COMMON_FOREGROUND, 0xFF000000));
            activity = getActivity();
        }

        @Override
        public void notifyDownloadError(final String error) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress.setText(error);
                }
            });
            lastError = error;
        }

        @Override
        public void notifyHttpClientObtained(HttpClient client) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void notifyDownloadProgress(int progressBytes) {
            String text = " " + progressBytes / 1024 + "K";
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
        }
    }

    private void loadMore() {
        loading = true;
        restoreData = null;
        page++;
        final JuickMessage jmsg = listAdapter.getItem(listAdapter.getCount() - 1);

        final MoreMessagesLoadNotification progressNotification = new MoreMessagesLoadNotification();
        Thread thr = new Thread("Download messages (more)") {

            public void run() {
                final Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    messagesSource.getNext(progressNotification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
                        @Override
                        public Void apply(final ArrayList<JuickMessage> messages) {
                            activity.runOnUiThread(new Runnable() {

                                public void run() {
                                    listAdapter.addAllMessages(messages);
                                    loading = false;
                                }
                            });
                            return null;  //To change body of implemented methods use File | Settings | File Templates.
                        }
                    });
                }
            }
        };
        thr.start();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JuickMessage jmsg = (JuickMessage) parent.getItemAtPosition(position);
        Intent i = new Intent(getActivity(), ThreadActivity.class);
        i.putExtra("mid", jmsg.MID);
        startActivity(i);
    }

    // Refresh
    public void onClick(View view) {
        mRefreshState = REFRESHING;
        prepareForRefresh();
        init();
    }

    @Override
    public void onStop() {
        if (topMessageId != -1) {
            messagesSource.rememberSavedPosition(topMessageId);
        }
        super.onStop();
    }

    int lastItemReported = 0;

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        int prefetchMessagesSize = prefetchMessages ? 20:0;
        if (visibleItemCount < totalItemCount && (firstVisibleItem + visibleItemCount >= totalItemCount - prefetchMessagesSize) && loading == false) {
            if (messagesSource.canNext()) {
                loadMore();
            }
        }
        try {
            JuickMessage jm;
            if (firstVisibleItem != 0) {
                ListAdapter listAdapter = getListAdapter();
                jm = (JuickMessage)listAdapter.getItem(firstVisibleItem-1);
                topMessageId = jm.MID;
                if (firstVisibleItem > 1 && trackLastRead) {
                    final int itemToReport = firstVisibleItem - 1;
                    if (lastItemReported < itemToReport) {
                        for(lastItemReported++; lastItemReported <= itemToReport; lastItemReported++) {
                            final int itemToSave = lastItemReported;
                            if (itemToSave - 1 < listAdapter.getCount()) {  // some async delete could happen
                                final JuickMessage item = (JuickMessage) listAdapter.getItem(itemToSave - 1);
                                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                    @Override
                                    public void withService(DatabaseService service) {
                                        service.markAsRead(new DatabaseService.ReadMarker(item.MID, item.replies, item.Timestamp.getTime()));
                                    }

                                });
                            }
                        }
                        lastItemReported--;
                    }
                }
            } else {
                jm = (JuickMessage)getListAdapter().getItem(firstVisibleItem);
                topMessageId = jm.MID+1;    // open/closed interval
            }
        } catch (Exception ex) {}

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
                setSelection(1);
                mBounceHack = true;
            } else if (mBounceHack && mCurrentScrollState == SCROLL_STATE_FLING) {
                setSelection(1);
            }
        }
    }

    public boolean onTouch(View view, MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
        }

        final int y = (int) event.getY();
        mBounceHack = false;

        switch (event.getAction()) {
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
            case MotionEvent.ACTION_DOWN:
                mLastMotionY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                applyHeaderPadding(event);
                break;
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

        MainActivity.restyleChildrenOrWidget(mRefreshView);
    }

    public void clearSavedPosition(Context context) {
        topMessageId = -1;
        messagesSource.resetSavedPosition();
    }


    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            listAdapter.setScale(detector.getScaleFactor());
            listAdapter.notifyDataSetChanged();
            return true;
        }
    }


}
