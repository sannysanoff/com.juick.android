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
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import com.juickadvanced.R;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * @author Ugnich Anton
 */
public class MessagesFragment extends ListFragment implements AdapterView.OnItemClickListener, AbsListView.OnScrollListener, View.OnTouchListener, View.OnClickListener {

    private JuickMessagesAdapter listAdapter;
    private View viewLoading;
    private String apiurl;
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
    private boolean mSaveLastMessagesPosition;
    private ScaleGestureDetector mScaleDetector = null;

    Handler handler;
    private Object restoreData;
    boolean implicitlyCreated;


    public MessagesFragment(Object restoreData) {
        this.restoreData = restoreData;
        implicitlyCreated = false;
    }

    public MessagesFragment() {
        implicitlyCreated = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();
        boolean home = false;
        int uid = 0;
        String uname = null;
        String search = null;
        String tag = null;
        int place_id = 0;
        boolean popular = false;
        boolean media = false;
        boolean myBlog = true;

        Bundle args = getArguments();
        if (args != null) {
            home = args.getBoolean("home", false);
            uid = args.getInt("uid", 0);
            uname = args.getString("uname");
            search = args.getString("search");
            tag = args.getString("tag");
            place_id = args.getInt("place_id", 0);
            popular = args.getBoolean("popular", false);
            media = args.getBoolean("media", false);
            myBlog = args.getBoolean("myBlog", false);
        }

        if (home) {
            apiurl = "http://api.juick.com/home?1=1";
        } else {
            apiurl = "http://api.juick.com/messages?1=1";
            if (uid > 0 && uname != null) {
                apiurl += "&user_id=" + uid;
            } else if (myBlog) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String myUserId = sp.getString("myUserId", "12234567788");
                apiurl += "&user_id=" + myUserId;
            } else if (search != null) {
                try {
                    apiurl += "&search=" + URLEncoder.encode(search, "utf-8");
                } catch (Exception e) {
                    Log.e("ApiURL", e.toString());
                }
            } else if (tag != null) {
                try {
                    apiurl += "&tag=" + URLEncoder.encode(tag, "utf-8");
                } catch (Exception e) {
                    Log.e("ApiURL", e.toString());
                }
                if (uid == -1) {
                    apiurl += "&user_id=-1";
                }
            } else if (place_id > 0) {
                apiurl += "&place_id=" + place_id;
            } else if (popular) {
                apiurl += "&popular=1";
            } else if (media) {
                apiurl += "&media=all";
            } else {
                // just "last messages"

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (sp.getBoolean("persistLastMessagesPosition", false)) {
                    int lastMessagesSavedPosition = sp.getInt("lastMessagesSavedPosition", -1);
                    if (lastMessagesSavedPosition != -1) {
                        apiurl += "&before_mid=" + lastMessagesSavedPosition;
                    }
                    mSaveLastMessagesPosition = true;
                }
            }
        }

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
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (sp.getBoolean("enableScaleByGesture", true)) {
                mScaleDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }


    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
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

        mRefreshView = (RelativeLayout) li.inflate(R.layout.pull_to_refresh_header, null);
        mRefreshViewText = (TextView) mRefreshView.findViewById(R.id.pull_to_refresh_text);
        mRefreshViewImage = (ImageView) mRefreshView.findViewById(R.id.pull_to_refresh_image);
        mRefreshViewProgress = (ProgressBar) mRefreshView.findViewById(R.id.pull_to_refresh_progress);
        mRefreshViewImage.setMinimumHeight(50);
        mRefreshView.setOnClickListener(this);
        mRefreshOriginalTopPadding = mRefreshView.getPaddingTop();
        mRefreshState = TAP_TO_REFRESH;


        listAdapter = new JuickMessagesAdapter(getActivity(), 0);
        if (mSaveLastMessagesPosition) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (sp.getInt("lastMessagesSavedPosition", -1) > 0)
                listAdapter.setContinuationAdapter(true);
        }

        getListView().setOnTouchListener(this);
        getListView().setOnScrollListener(this);
        getListView().setOnItemClickListener(this);
        getListView().setOnItemLongClickListener(new JuickMessageMenu(getActivity(), getListView(), listAdapter));

        init();
    }

    private void init() {
        if (implicitlyCreated) return;
        final MessagesLoadNotification messagesLoadNotification = new MessagesLoadNotification(getActivity(), handler);
        Thread thr = new Thread(new Runnable() {

            public void run() {
                final MessagesLoadNotification notification = messagesLoadNotification;
                final ArrayList<JuickMessage> messages;
                Parcelable listPosition = null;
                if (restoreData == null) {
                    final String jsonStr = Utils.getJSON(getActivity(), apiurl, notification);
                    messages = listAdapter.parseJSONpure(jsonStr);
                } else {
                    messages = ((RetainedData) restoreData).messages;
                    listPosition = ((RetainedData) restoreData).viewState;
                }
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
                                        if (messages.size() == 20 && getListView().getFooterViewsCount() == 0) {
                                            getListView().addFooterView(viewLoading, null, false);
                                        }
                                    }

                                    if (getListView().getHeaderViewsCount() == 0) {
                                        getListView().addHeaderView(mRefreshView, null, false);
                                        //measureView(mRefreshView);
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
                                        setSelection(1);
                                    }
                                } catch (IllegalStateException e) {
                                    Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }
            }
        });
        thr.start();
    }

    static class RetainedData {
        ArrayList<JuickMessage> messages;
        Parcelable viewState;
    }

    public Object saveState() {
        RetainedData rd = new RetainedData();
        rd.messages = new ArrayList<JuickMessage>();
        int count = listAdapter.getCount();
        for (int i = 0; i < count; i++) {
            rd.messages.add(listAdapter.getItem(i));
        }
        rd.viewState = getListView().onSaveInstanceState();
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
        Thread thr = new Thread(new Runnable() {

            public void run() {
                URLParser apiURL = new URLParser(apiurl);
                apiURL.getArgsMap().put("before_mid", "" + jmsg.MID);
                apiURL.getArgsMap().put("page", "" + page);
                final String jsonStr = Utils.getJSON(getActivity(), apiURL.getFullURL(), progressNotification);
                if (isAdded()) {
//                    if (jsonStr == null) {
//                        getActivity().runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(getActivity(), "More messages: "+progressNotification.lastError, Toast.LENGTH_LONG).show();
//                            }
//                        });
//                    }
                    final ArrayList<JuickMessage> messages = listAdapter.parseJSONpure(jsonStr);
                    getActivity().runOnUiThread(new Runnable() {

                        public void run() {
                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                            sp.edit().putInt("lastMessagesSavedPosition", jmsg.MID).commit();
                            listAdapter.addAllMessages(messages);
//                            if (messages.size() != 20) {
//                                MessagesFragment.this.getListView().removeFooterView(viewLoading);
//                            }
                            loading = false;
                        }
                    });
                }
            }
        });
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
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (visibleItemCount < totalItemCount && (firstVisibleItem + visibleItemCount == totalItemCount) && loading == false) {
            loadMore();
        }

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
    /*
    private void measureView(View child) {
    ViewGroup.LayoutParams p = child.getLayoutParams();
    if (p == null) {
    p = new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.FILL_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    
    int childWidthSpec = ViewGroup.getChildMeasureSpec(0,
    0 + 0, p.width);
    int lpHeight = p.height;
    int childHeightSpec;
    if (lpHeight > 0) {
    childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
    } else {
    childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    }
    child.measure(childWidthSpec, childHeightSpec);
    }
     */

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

        if (mSaveLastMessagesPosition) {
            clearSavedPosition(getActivity());
            listAdapter.setContinuationAdapter(false);
        }
    }

    public static void clearSavedPosition(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("lastMessagesSavedPosition").commit();
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
