package com.juick.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.juick.android.bnw.BnwAuthorizer;
import com.juick.android.bnw.BnwCompatibleMessagesSource;
import com.juick.android.facebook.FacebookAuthorizer;
import com.juick.android.facebook.FacebookFeedMessagesSource;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juick.android.juick.JuickMicroBlog;
import com.juick.android.point.PointAPIMessagesSource;
import com.juick.android.point.PointAuthorizer;
import com.juickadvanced.R;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.facebook.FacebookMessageID;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.point.PointMessageID;

/**
 * Created by san on 6/1/14.
 */
public class CombinedSubscriptionMessagesSource extends CombinedAllMessagesSource {

    public static final String COMBINED_SUBSCRIPTION_MESSAGES_SOURCE = "CombinedSubscriptionMessagesSource.";

    public CombinedSubscriptionMessagesSource(Context ctx) {
        super(ctx, "combined_subs");
    }

    @Override
    protected void initSources(Context ctx) {
        JuickAdvancedApplication.initAuthorizers(ctx);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (JuickAPIAuthorizer.getJuickAccountName(ctx) != null && prefs.getBoolean(COMBINED_SUBSCRIPTION_MESSAGES_SOURCE + JuickMessageID.CODE, true)) {
            nested.add(JuickMicroBlog.getSubscriptionsMessagesSource((MainActivity)ctx, R.string.navigationSubscriptions));
        }
        if (PointAuthorizer.csrfToken != null && prefs.getBoolean(COMBINED_SUBSCRIPTION_MESSAGES_SOURCE + PointMessageID.CODE, true)) {
            nested.add(new PointAPIMessagesSource(ctx, "home", "subs", "http://point.im/api/recent"));
        }
        if (BnwAuthorizer.myCookie != null && prefs.getBoolean(COMBINED_SUBSCRIPTION_MESSAGES_SOURCE + BnwMessageID.CODE, true)) {
            nested.add(new BnwCompatibleMessagesSource(ctx, "subs", "/feed", "home"));
        }
        if (FacebookAuthorizer.oauth != null && prefs.getBoolean(COMBINED_SUBSCRIPTION_MESSAGES_SOURCE + FacebookMessageID.CODE, true)) {
            nested.add(new FacebookFeedMessagesSource(ctx, "fb_feed", "feed"));
        }
    }

    @Override
    public CharSequence getTitle() {
        return "Combined/Subs";
    }

}
