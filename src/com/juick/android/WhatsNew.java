package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;
import com.juickadvanced.R;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/8/12
 * Time: 4:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class WhatsNew {

    class ReleaseFeatures {
        int textId;
        String sinceRelease;

        ReleaseFeatures(String sinceRelease, int textId) {
            this.sinceRelease = sinceRelease;
            this.textId = textId;
        }
    }

    ReleaseFeatures[] features = new ReleaseFeatures[] {
            new ReleaseFeatures("2012090502", R.string.rf_2012090502),
            new ReleaseFeatures("2012082601", R.string.rf_2012082601),
            new ReleaseFeatures("2012081901", R.string.rf_2012081901),
            new ReleaseFeatures("2012081701", R.string.rf_2012081701),
    };

    Activity context;

    public WhatsNew(Activity context) {
        this.context = context;
    }

    void maybeRunFeedbackAndMore() {
        final Runnable after = new Runnable() {
            @Override
            public void run() {
                //
            }
        };
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String currentSetting = sp.getString("usage_statistics", "");
        if (currentSetting.length() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final View stat = context.getLayoutInflater().inflate(R.layout.enable_statistics, null);

            stat.findViewById(R.id.read_privacy_policy).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WebView wv = new WebView(context);
                    Utils.setupWebView(wv, context.getString(R.string.privacy_policy));

                    AlertDialog.Builder builder = new AlertDialog.Builder(context)
                            .setTitle(R.string.Privacy_Policy)
                            .setView(wv)
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .setCancelable(true);
                    builder.show();
                }
            });
            final AlertDialog alert = builder.setTitle(context.getString(R.string.UsageStatistics))
                    .setMessage(context.getString(R.string.EnableUsageStatistics))
                    .setCancelable(false)
                    .setView(stat)
                    .create();
            stat.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RadioButton us_send = (RadioButton)stat.findViewById(R.id.us_send);
                    RadioButton us_send_wifi = (RadioButton)stat.findViewById(R.id.us_send_wifi);
                    RadioButton us_no_hello = (RadioButton)stat.findViewById(R.id.us_no_hello);
                    RadioButton us_no = (RadioButton)stat.findViewById(R.id.us_no);
                    String option = "";
                    if (us_send.isChecked())
                        option  = "send";
                    if (us_send_wifi.isChecked())
                        option  = "send_wifi";
                    if (us_no_hello.isChecked())
                        option  = "no_hello";
                    if (us_no.isChecked())
                        option  = "us_no";
                    if (option.length() != 0) {
                        sp.edit().putString("usage_statistics", option).commit();
                        alert.dismiss();
                        after.run();
                    } else {
                        Toast.makeText(context, context.getString(R.string.ChooseFirstOption), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else {
            after.run();
        }
    }


    public void runAll() {
        Runnable after = new Runnable() {
            @Override
            public void run() {
                maybeRunFeedbackAndMore();
            }
        };
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String last_features_reported = sp.getString("last_features_reported", "");
        if (last_features_reported.length() == 0) {
            ReleaseFeatures feature = features[0];
            sp.edit().putString("last_features_reported", feature.sinceRelease).commit();
            reportFeatures(0, false, after);
            return;
        } else {
            for (int i = 0; i < features.length; i++) {
                ReleaseFeatures feature = features[i];
                if (feature.sinceRelease.compareTo(last_features_reported) > 0) {
                    sp.edit().putString("last_features_reported", feature.sinceRelease).commit();
                    reportFeatures(i, true, after);
                    return;
                }
            }
        }
        if (after != null)
            after.run();

    }

    public void reportFeatures(int sequence, boolean cycle, final Runnable notCycle) {
        WebView wv = new WebView(context);
        ReleaseFeatures feature = features[sequence];
        Utils.setupWebView(wv, context.getString(feature.textId));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.NewFeatures)
                .setView(wv)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        if (notCycle != null)
                            notCycle.run();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (notCycle != null)
                            notCycle.run();
                    }
                })
                .setCancelable(true);
        if (sequence < features.length - 1 && cycle) {
            builder.setPositiveButton("Есть еще...", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    runAll();
                }
            });
        }
        builder.show();
    }
}
