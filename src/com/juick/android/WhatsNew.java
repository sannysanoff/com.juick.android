package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.gson.*;
import com.juick.android.ja.Network;
import com.juickadvanced.R;
import com.juickadvanced.RESTResponse;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.juick.android.Utils.JA_API_URL;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/8/12
 * Time: 4:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class WhatsNew {

    ReleaseFeatures[] features = new ReleaseFeatures[]{
            new ReleaseFeatures("2016082102", R.string.rf_2016082102),
            new ReleaseFeatures("2014081601", R.string.rf_2014081601),
            new ReleaseFeatures("2014081101", R.string.rf_2014081101),
            new ReleaseFeatures("2013100101", R.string.rf_2013100101),
            new ReleaseFeatures("2013062001", R.string.rf_2013062001),
            new ReleaseFeatures("2012121903", R.string.rf_2012121903),
            new ReleaseFeatures("2012092002", R.string.rf_2012092001),
            new ReleaseFeatures("2012091402", R.string.rf_2012091402),
            new ReleaseFeatures("2012082601", R.string.rf_2012082601),
            new ReleaseFeatures("2012081901", R.string.rf_2012081901),
            new ReleaseFeatures("2012081701", R.string.rf_2012081701),
    };

    public static final long REPORT_SEND_PERIOD = 2 * 24 * 60 * 60 * 1000L;
    public static String updateURL;
    public static String updateDescription;
    static File updatesDir;
    public final static long UPDATE_CHECK_INTERVAL = 60 * 60 * 1000L;
    public final static long NEWS_CHECK_INTERVAL = 60 * 60 * 1000L;

    public static File getUpdateAPK() {
        return new File(Environment.getExternalStorageDirectory(), "juick-advanced-update.apk");
    }

    static class News {
        String code;
        String body = "";
    }

    public static void checkForNews(final IRunningActivity runningActivity, final Utils.Function<Void, ArrayList<News>> notRunning) {
        final Activity activity = runningActivity.getActivity();
        final File last_news_check = getLastNewsCheck(activity);
        if (last_news_check.exists()) {
            if (System.currentTimeMillis() - last_news_check.lastModified() < NEWS_CHECK_INTERVAL) {
                return;
            }
        }
        new Thread() {
            @Override
            public void run() {
                boolean found = false;
                String reasonNotFound = "other reason";
                final RESTResponse json = Utils.getJSON(activity, JA_API_URL+ "/get_last_news", null);
                try {
                    last_news_check.createNewFile();
                } catch (IOException e) {
                    //
                }
                last_news_check.setLastModified(System.currentTimeMillis());
                if (json.getResult() != null) {
                    final ArrayList<News> newsList = new ArrayList<News>();
                    News news = new News();
                    final String[] lines = json.getResult().split("\n");
                    for(int i=0; i<lines.length; i++) {
                        final String ln = lines[i];
                        if (ln.startsWith("#")) {
                            if (news.body != null && news.body.replace("\n"," ").trim().length() > 0) {
                                newsList.add(news);
                                news = new News();
                            }
                            news.code = ln.substring(1).trim();
                            news.body = "";
                        } else {
                            news.body = news.body + ln + "\n";
                        }
                    }
                    if (news.body != null && news.body.replace("\n"," ").trim().length() > 0) {
                        newsList.add(news);
                    }
                    runningActivity.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (notRunning != null) notRunning.apply(newsList);
                        }
                    });
                }
            }
        }.start();
    }

    public static void checkForUpdates(final IRunningActivity runningActivity, final Utils.Function<Void, String> notRunning, final boolean force) {
        final Activity activity = runningActivity.getActivity();
        final File last_check = getLastCheck(activity);
        if (last_check.exists()) {
            if (System.currentTimeMillis() - last_check.lastModified() < UPDATE_CHECK_INTERVAL) {
                if (notRunning != null) notRunning.apply("not checking: too often");
                return;
            }
        }
        final int currentVersionCode;
        try {
            currentVersionCode = activity.getApplicationContext().getPackageManager().getPackageInfo(activity.getApplicationContext().getPackageName(), 0).versionCode;
        } catch (Exception ex) {
            Crashlytics.logException(ex);
            if (notRunning != null) notRunning.apply(ex.toString());
            return;
        }
        final SharedPreferences sp = activity.getSharedPreferences("versions", Context.MODE_PRIVATE);
        final String reportedKey = "reported_version_" + currentVersionCode;
        boolean reported = sp.getBoolean(reportedKey, false);
        if (!reported) {
            new Thread() {
                @Override
                public void run() {
                    RESTResponse json = Utils.getJSON(activity, JA_API_URL+"/notify_updated?version=" + currentVersionCode, null);
                    if (json.getErrorText() == null) {
                        JuickAdvancedApplication.foreverHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                sp.edit().putBoolean(reportedKey, true).commit();
                            }
                        });
                    }
                    super.run();    //To change body of overridden methods use File | Settings | File Templates.
                }
            }.start();
        }
        boolean enableBetaChannelCheck = sp.getBoolean("enableBetaChannelCheck", true);
        if (enableBetaChannelCheck || force) {
            new Thread() {
                @Override
                public void run() {
                    boolean found = false;
                    String reasonNotFound = "other reason";
                    try {
                        RESTResponse json = Utils.getJSON(activity, JA_API_URL+ "/get_last_version?force=" + force, null);
                        try {
                            last_check.createNewFile();
                        } catch (IOException e) {
                            //
                        }
                        last_check.setLastModified(System.currentTimeMillis());
                        if (json.getResult() != null) {
                            Matcher matcher = Pattern.compile("com.juickadvanced-(\\d+).apk").matcher(json.getResult());
                            if (matcher.find()) {
                                String version = matcher.group(1);
                                try {
                                    int updateVersionCode = Integer.parseInt(version);
                                    if (updateVersionCode > currentVersionCode) {
                                        if (!new File(updatesDir, "ignore-" + version).exists() || force) {
                                            try {
                                                JsonObject jsonElement = (JsonObject) new Gson().fromJson(json.getResult(), JsonElement.class);
                                                JsonPrimitive url = (JsonPrimitive) jsonElement.get("url");
                                                updateURL = url.getAsString();
                                                JsonPrimitive desc = (JsonPrimitive) jsonElement.get("description");
                                                updateDescription = desc.getAsString();
                                                MainActivity.updateAvailable = version;
                                                found = true;
                                                runningActivity.getHandler().post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        setUpdateVisible(runningActivity);
                                                    }
                                                });
                                            } catch (Exception e) {
                                                reasonNotFound = "Program bug with json? reported to author.";
                                                Crashlytics.logException(new RuntimeException("bad json? "+json.getResult(), e));
                                            }
                                        }
                                    } else {
                                        reasonNotFound = "server version: " + updateVersionCode + " current version: " + currentVersionCode;
                                        File file = getUpdateAPK();
                                        if (file.exists()) {
                                            file.delete();
                                        }
                                    }
                                } catch (Exception e) {
                                    reasonNotFound = e.toString();
                                    MainActivity.handleException(e);
                                }
                            } else {
                                reasonNotFound = "bad response from server";
                            }
                        } else {
                            reasonNotFound = json.getErrorText();
                        }
                    } finally {
                        if (!found) {
                            final String finalReasonNotFound = reasonNotFound;
                            runningActivity.getHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    if (notRunning != null) notRunning.apply(finalReasonNotFound);
                                }
                            });
                        }
                    }
                }
            }.start();
        } else {
            notRunning.apply("beta check not enabled");
        }
    }

    public static File getLastCheck(Activity activity) {
        updatesDir = new File(activity.getFilesDir(), "updates");
        updatesDir.mkdirs();
        return new File(updatesDir, "last_check");
    }

    public static File getLastNewsCheck(Activity activity) {
        updatesDir = new File(activity.getFilesDir(), "updates");
        updatesDir.mkdirs();
        return new File(updatesDir, "last_news_check");
    }

    public static void setUpdateVisible(final IRunningActivity runningActivity) {
        final Activity activity = runningActivity.getActivity();
        if (MainActivity.updateAvailable != null) {
            if (runningActivity.isRunning()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle("New Beta available");
                View view = activity.getLayoutInflater().inflate(R.layout.update_dialog, null);
                TextView changes = (TextView) view.findViewById(R.id.changes);
                changes.setMovementMethod(LinkMovementMethod.getInstance());
                changes.setText(updateDescription);
                TextView versionCode = (TextView) view.findViewById(R.id.versionCode);
                versionCode.setText(MainActivity.updateAvailable);

                builder.setView(view);
                builder.setPositiveButton(activity.getString(R.string.DownloadAndInstall), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        Toast.makeText(activity, activity.getString(R.string.DownloadWillStartNow), Toast.LENGTH_LONG).show();
                        try {
                            final DownloadManager mgr = (DownloadManager) runningActivity.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
                            final Uri parse = Uri.parse(updateURL);
                            final DownloadManager.Request req = new DownloadManager.Request(parse);

                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                    .mkdirs();
                            String name = new File(parse.getPath()).getName();
                            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE)
                                    .setAllowedOverRoaming(true)
                                    .setTitle(name)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                                    .setDescription("Juick Advanced update");
                            mgr.enqueue(req);
                        } catch (Exception e) {
                            Toast.makeText(activity, "ERROR:" + e.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                builder.setNeutralButton(activity.getString(R.string.AskLater), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File last_check = new File(updatesDir, "last_check");
                        last_check.delete();
                        try {
                            last_check.createNewFile();
                        } catch (IOException e) {
                            //
                        }
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(activity.getString(R.string.Skip), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            new File(updatesDir, "ignore-" + MainActivity.updateAvailable).createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        }

    }

    public void increaseUsage(Context ctx, String key, long usedTime) {
        final SharedPreferences sp = ctx.getSharedPreferences("activity_time", Context.MODE_PRIVATE);
        long soFar = sp.getLong(key, 0);
        soFar += usedTime;
        sp.edit().putLong(key, soFar).commit();
    }

    public static void collectUsage(Context ctx, JsonObject jo) {
        //To change body of created methods use File | Settings | File Templates.
        final SharedPreferences sp = ctx.getSharedPreferences("activity_time", Context.MODE_PRIVATE);
        final Map<String, ?> all = sp.getAll();
        for (Map.Entry<String, ?> stringEntry : all.entrySet()) {
            jo.addProperty(stringEntry.getKey(), stringEntry.getValue().toString());
        }
    }


    class ReleaseFeatures {
        int textId;
        String sinceRelease;

        ReleaseFeatures(String sinceRelease, int textId) {
            this.sinceRelease = sinceRelease;
            this.textId = textId;
        }
    }

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
                    sp.edit().putString("usage_statistics", "no").commit();
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
                                    Network.postJSONHome(service, "/usage_report_handler", jo.toString());
                                }

                            }.start();
                        }
                    });
                }
                if (!sp.getBoolean("propmpted_all_options", false)) {
                    sp.edit().putBoolean("propmpted_all_options", true).commit();
                    // continue here
                    new AlertDialog.Builder(context)
                            .setTitle("Total Advance")
                            .setMessage(context.getString(R.string.MakeTotalAdvance))
                            .setPositiveButton(context.getString(R.string.MagicButton), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    toggleAllOptions(context);
                                    dialog.cancel();
                                    Toast.makeText(context, context.getString(R.string.NowTotalAdvance), Toast.LENGTH_LONG).show();
                                }
                            })
                            .setCancelable(false)
                            .setNegativeButton(R.string.No, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            }).show();
                }
            }
        };
        String currentSetting = sp.getString("usage_statistics", "");
        if (currentSetting.length() == 0) {
            Runnable loop = new Runnable() {
                Runnable thiz = this;

                @Override
                public void run() {
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
                            showPrivacyPolicy(thiz);
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
                }
            };
            loop.run();
        } else {
            after.run();
        }
    }

    private void toggleAllOptions(Activity context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit()
                .putString("image.loadMode", "japroxy")
                .putBoolean("dialogMessageMenu", true)
                .putBoolean("persistLastMessagesPosition", true)
                .putBoolean("confirmActions", true)
                .putBoolean("previewReplies", true)
                .putBoolean("warnRepliesToBody", true)
                .putBoolean("capitalizeReplies", true)
                .putBoolean("showNumbers", true)
                .putBoolean("showUserpics", true)
                .putBoolean("compactComments", true)
                .putBoolean("http_compression", true)
                .putBoolean("image.indirect", true)
                .putBoolean("enableMessageDB", true)
                .putBoolean("enableScaleByGesture", false)

                .putBoolean("msrcTopMessages", true)
                .putBoolean("msrcWithPhotos", true)
                .putBoolean("msrcUnread", true)
                .putBoolean("msrcSaved", true)
                .putBoolean("msrcRecentOpen", true)
                .putBoolean("msrcRecentComment", true)
                .putBoolean("msrcAllCombined", true)
                .putBoolean("msrcSubsCombined", true)
                .putBoolean("msrcBNWFeed", true)
                .putBoolean("msrcBNWAll", true)
                .putBoolean("msrcBNWHot", true)
                .putBoolean("msrcFacebookFeed", true)
                .putBoolean("msrcGlavSU", true)
                .putBoolean("msrcUnanswered", true)
                .putBoolean("msrcTopMessages", true)
                .putBoolean("msrcMyBlog", true)
                .putBoolean("msrcSrachiki", true)
                .putBoolean("msrcPrivate", true)
                .putBoolean("msrcDiscuss", true)
                .putBoolean("msrcJubo", true)
                .putBoolean("msrcPointSubs", true)
                .putBoolean("msrcPointAll", true)
                .putBoolean("msrcPointMine", true)
                .commit();
    }

    public static String getSendStatValueFromUI(View stat) {
        RadioButton us_send = (RadioButton) stat.findViewById(R.id.us_send);
        RadioButton us_send_wifi = (RadioButton) stat.findViewById(R.id.us_send_wifi);
        RadioButton us_no_hello = (RadioButton) stat.findViewById(R.id.us_no_hello);
        RadioButton us_no = (RadioButton) stat.findViewById(R.id.us_no);
        String option = "";
        if (us_send.isChecked())
            option = "send";
        if (us_send_wifi.isChecked())
            option = "send_wifi";
        if (us_no_hello.isChecked())
            option = "no_hello";
        if (us_no.isChecked())
            option = "no";
        return option;
    }

    public void showPrivacyPolicy(final Runnable then) {
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
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (then != null)
                            then.run();
                    }
                })
                .setCancelable(true);
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
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
                    reportFeatures(sequence + 1, cycle, notCycle);
                }
            });
        }
        builder.show();
    }

    private void applySettings(WebView wv) {
        String tag = (String) wv.getTag();
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
                            av[0] = av[0].substring(0, av[0].length() - 1);
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
        public void saveFeature(DatabaseService db) {
        }
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
                String safeString = unsafeString.replace("<", "&lt;");
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

    private static void doLocalInstall(final Context context, String maybeLocalURI) {
        try {
            File file;
            if (maybeLocalURI != null)
                file = new File(new URI(maybeLocalURI).getPath());
            else
                file = new File(Environment.getExternalStorageDirectory(), "juick-advanced-update.apk");
            Intent promptInstall = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            promptInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(promptInstall);
        } catch (URISyntaxException e) {
            Toast.makeText(context, "Failed launch update install.", Toast.LENGTH_LONG).show();
        }
    }

    private static void launchApplicationSettingsForUnknownSources(final Context context, final String maybeLocalURI) {
        if (context instanceof MainActivity) {
            ((MainActivity) context).installerOnResume = new Runnable() {
                @Override
                public void run() {
                    installUpdate((MainActivity) context, maybeLocalURI);
                }
            };
        }
        if (Build.VERSION.SDK_INT >= 14 /* Build.VERSION_CODES.ICE_CREAM_SANDWICH */) {
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            context.startActivity(intent);
        } else {
            // older setting
            Intent intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            context.startActivity(intent);
        }
    }

    public static void installUpdate(final Context context, final String maybeLocalURI) {
        String string = Settings.System.getString(context.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS);
        if ("1".equals(string)) {
            doLocalInstall(context, maybeLocalURI);
        } else {
            try {
                new AlertDialog.Builder(context)
                        .setMessage("You must allow 'unknown sources' to install update")
                        .setPositiveButton("Enable unknown sources", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                launchApplicationSettingsForUnknownSources(context, maybeLocalURI);
                                //To change body of implemented methods use File | Settings | File Templates.
                            }
                        }).setNegativeButton("Skip update", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (context instanceof MainActivity) {
                            MainActivity ma = (MainActivity) context;
                            try {
                                new File(new File(context.getFilesDir(), "updates"), "ignore-" + ma.updateAvailable).createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                        dialog.cancel();
                    }
                }).show();
            } catch (Exception ex) {
                //
            }

        }
    }


}
