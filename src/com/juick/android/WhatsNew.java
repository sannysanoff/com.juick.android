package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebView;
import android.widget.RadioButton;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.juick.android.ja.Network;
import com.juickadvanced.R;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/8/12
 * Time: 4:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class WhatsNew {

    public static final long REPORT_SEND_PERIOD = 2 * 24 * 60 * 60 * 1000L;


    class ReleaseFeatures {
        int textId;
        String sinceRelease;

        ReleaseFeatures(String sinceRelease, int textId) {
            this.sinceRelease = sinceRelease;
            this.textId = textId;
        }
    }

    ReleaseFeatures[] features = new ReleaseFeatures[] {
            new ReleaseFeatures("2012111401", R.string.rf_2012111401),
            new ReleaseFeatures("2012092002", R.string.rf_2012092001),
            new ReleaseFeatures("2012091402", R.string.rf_2012091402),
            new ReleaseFeatures("2012082601", R.string.rf_2012082601),
            new ReleaseFeatures("2012081901", R.string.rf_2012081901),
            new ReleaseFeatures("2012081701", R.string.rf_2012081701),
    };

    Activity context;

    public WhatsNew(Activity context) {
        this.context = context;
    }

    void maybeRunFeedbackAndMore() {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        final Runnable after = new Runnable() {
            @Override
            public void run() {
                String chosen = sp.getString("usage_statistics", "no");
                if (chosen.equals("no_hello")) {
                    // say hello
                    sp.edit().putString("usage_statistics","no").commit();
                    Utils.ServiceGetter<DatabaseService> databaseServiceServiceGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
                    databaseServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                        @Override
                        public void withService(final DatabaseService service) {
                            final JsonObject jo = new JsonObject();
                            String uniqueInstallationId = service.getUniqueInstallationId(context, "");
                            jo.addProperty("device_install_id", uniqueInstallationId);
                            new Thread() {
                                @Override
                                public void run() {
                                    Network.postJSONHome(service, "/E_RPusage_report_handler", jo.toString());
                                }

                            }.start();
                        }
                    });
                }
                // continue here
            }
        };
        String currentSetting = sp.getString("usage_statistics", "");
        if (currentSetting.length() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final View stat = context.getLayoutInflater().inflate(R.layout.enable_statistics, null);
            final AlertDialog alert = builder.setTitle(context.getString(R.string.UsageStatistics))
                    .setMessage(context.getString(R.string.EnableUsageStatistics))
                    .setCancelable(false)
                    .setView(stat)
                    .create();

            stat.findViewById(R.id.read_privacy_policy).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPrivacyPolicy();
                }
            });
            stat.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String option = getSendStatValueFromUI(stat);
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

    public static String getSendStatValueFromUI(View stat) {
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
            option  = "no";
        return option;
    }

    public void showPrivacyPolicy() {
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

    public void reportFeatures(final int sequence, final boolean cycle, final Runnable notCycle) {
        final WebView wv = new WebView(context);
        ReleaseFeatures feature = features[sequence];
        String content = context.getString(feature.textId);
        content = content.replace("#script#", wv.getContext().getString(R.string.NewFeatures_js));
        Utils.setupWebView(wv, content);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.NewFeatures)
                .setView(wv)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
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
        if (content.indexOf("#prefs.") != -1) {
            builder.setPositiveButton(context.getString(R.string.ApplySettings), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    applySettings(wv);
                    dialog.cancel();
                }
            });

        }
        if (sequence < features.length - 1 && cycle) {
            builder.setNeutralButton(context.getString(R.string.OlderFeatures), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    applySettings(wv);
                    reportFeatures(sequence+1, cycle, notCycle);
                }
            });
        }
        builder.show();
    }

    private void applySettings(WebView wv) {
        String tag = (String)wv.getTag();
        if (tag == null) {
            Toast.makeText(context, "Error getting checkbox values from embedded browser, sorry. Try using Settings screen", Toast.LENGTH_LONG).show();
        } else {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor edit = sp.edit();
            String[] split = tag.split(";");
            for (String s : split) {
                if (s.startsWith("prefs.")) {
                    s = s.substring(6);
                    String[] av = s.split("=");
                    if (av.length == 2) {
                        boolean def = false;
                        if (av[0].endsWith("!")) {
                            def = true;
                            av[0] = av[0].substring(0, av[0].length()-1);
                        }
                        boolean newValue = "true".equals(av[1]);
                        if (sp.getBoolean(av[0], def) != newValue) {
                            edit.putBoolean(av[0], newValue);
                        }
                    }
                }
            }
            edit.commit();
        }
    }

    public static class FeatureSaver {
        public void saveFeature(DatabaseService db) {}
    }

    public void reportFeature(final String feature_name, final String feature_value) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String chosen = sp.getString("usage_statistics", "no");
        if (chosen.startsWith("send")) {
            reportFeature(new FeatureSaver() {
                @Override
                public void saveFeature(DatabaseService db) {
                    db.reportFeature(feature_name, feature_value);
                }
            });
        }
    }

    public void reportFeature(final FeatureSaver saver) {
        Utils.ServiceGetter<DatabaseService> databaseServiceServiceGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
        try {
            databaseServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                @Override
                public void withService(DatabaseService service) {
                    service.runGenericWriteJob(new Utils.Function<Boolean, DatabaseService>() {
                        @Override
                        public Boolean apply(DatabaseService databaseService) {
                            saver.saveFeature(databaseService);
                            return true;
                        }
                    });
                }
            });
        } catch (Throwable e) {
            // during fatal destroy, this happens
        }
    }

    public void reportUsage() {
        final Utils.ServiceGetter<DatabaseService> databaseServiceServiceGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
        databaseServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
            @Override
            public void withService(final DatabaseService service) {
                final JsonObject jsonObject = service.prepareUsageReportValue();
                new Thread() {
                    @Override
                    public void run() {
                        String usageReport = jsonObject.toString();
                        if ("OK".equals(Network.postJSONHome(service, "/usage_report_handler", usageReport))) {
                            databaseServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                @Override
                                public void withService(DatabaseService service) {
                                    service.cleanupUsageData();
                                    service.handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                                            sp.edit().putLong("last_usage_sent", System.currentTimeMillis()).commit();
                                            MainActivity.usageReportThread = null;
                                        }
                                    });
                                }
                            });
                        } else {
                            //error
                            service.handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // try again in 3 hours
                                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                                    sp.edit().putLong("last_usage_sent", System.currentTimeMillis() - REPORT_SEND_PERIOD + 3 * 60 * 60 * 1000L).commit();
                                    MainActivity.usageReportThread = null;
                                }
                            });
                        }
                    }
                }.start();


            }
        });
    }

    public void showUsageReport() {
        Utils.ServiceGetter<DatabaseService> databaseServiceServiceGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
        databaseServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
            @Override
            public void withService(final DatabaseService service) {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                String chosen = sp.getString("usage_statistics", "no");
                String decodedUsageStatistics = decodeUsageStatistics(chosen);
                StringBuilder sb = new StringBuilder();
                sb.append(context.getString(R.string.statistics_mode));
                sb.append(decodedUsageStatistics);
                sb.append("<br><hr>");
                JsonObject jsonObject = service.prepareUsageReportValue();
                String unsafeString = jsonObject.toString();
                String safeString = unsafeString.replace("<","&lt;");
                sb.append(safeString);
                WebView wv = new WebView(service);
                String htmlContent = sb.toString();
                Utils.setupWebView(wv, context.getString(R.string.HTMLStart) + htmlContent);

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
    }

    private String decodeUsageStatistics(String chosen) {
        if (chosen.equals("no")) return context.getString(R.string.us_no);
        if (chosen.equals("no_hello")) return context.getString(R.string.us_no_hello_done);
        if (chosen.equals("send_wifi")) return context.getString(R.string.us_send_wifi);
        if (chosen.equals("send")) return context.getString(R.string.us_send);
        return "???";
    }


}
