package com.juick.android;

import android.content.Context;
import com.juick.android.bnw.BnwAuthorizer;
import com.juick.android.bnw.BnwCompatibleMessagesSource;
import com.juick.android.facebook.FacebookAuthorizer;
import com.juick.android.facebook.FacebookFeedMessagesSource;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juick.android.juick.JuickMicroBlog;
import com.juick.android.point.PointAPIMessagesSource;
import com.juick.android.point.PointAuthorizer;
import com.juickadvanced.R;

/**
 * Created by san on 6/1/14.
 */
public class CombinedSubscriptionMessagesSource extends CombinedAllMessagesSource {

    public CombinedSubscriptionMessagesSource(Context ctx) {
        super(ctx, "combined_subs");
    }

    @Override
    protected void initSources(Context ctx) {
        JuickAdvancedApplication.initAuthorizers(ctx);
        if (JuickAPIAuthorizer.getJuickAccountName(ctx) != null) {
            nested.add(JuickMicroBlog.getSubscriptionsMessagesSource((MainActivity)ctx, R.string.navigationSubscriptions));
        }
        if (PointAuthorizer.csrfToken != null) {
            nested.add(new PointAPIMessagesSource(ctx, "home", "subs", "http://point.im/api/recent"));
        }
        if (BnwAuthorizer.myCookie != null) {
            nested.add(new BnwCompatibleMessagesSource(ctx, "subs", "/feed", "home"));
        }
        if (FacebookAuthorizer.oauth != null) {
            nested.add(new FacebookFeedMessagesSource(ctx, "fb_feed", "feed"));
        }
    }

    @Override
    public CharSequence getTitle() {
        return "Combined/Subs";
    }

}
