package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.juick.android.ja.Network;
import com.juickadvanced.R;
import org.acra.ACRA;
import org.apache.http.client.HttpClient;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/8/12
 * Time: 4:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class WhatsNew {

    public static final long REPORT_SEND_PERIOD = 2 * 24 * 60 * 60 * 1000L;
    public static String updateURL;
    public static String updateDescription;
    static File updatesDir;
    public final static long UPDATE_CHECK_INTERVAL = 60 * 60 * 1000L;

    public static File getUpdateAPK() {
        return new File(Environment.getExternalStorageDirectory(), "juick-advanced-update.apk");
    }

    public static void checkForUpdates(final MainActivity activity) {
        updatesDir = new File(activity.getFilesDir(), "updates");
        updatesDir.mkdirs();
        final File last_check = new File(updatesDir, "last_check");
        if (last_check.exists()) {
            if (System.currentTimeMillis() - last_check.lastModified() < UPDATE_CHECK_INTERVAL) return;
        }
        final int currentVersionCode;
        try {
            currentVersionCode = activity.getApplicationContext().getPackageManager().getPackageInfo(activity.getApplicationContext().getPackageName(), 0).versionCode;
        } catch (Exception ex) {
            ACRA.getErrorReporter().handleException(ex, false);
            return;
        }
        final SharedPreferences sp = activity.getSharedPreferences("versions", Context.MODE_PRIVATE);
        final String reportedKey = "reported_version_" + currentVersionCode;
        boolean reported = sp.getBoolean(reportedKey, false);
        if (!reported) {
            new Thread() {
                @Override
                public void run() {
                    Utils.RESTResponse json = Utils.getJSON(activity, "http://" + Utils.JA_ADDRESS + "/api/notify_updated?version=" + currentVersionCode, null);
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
        new Thread() {
            @Override
            public void run() {
                Utils.RESTResponse json = Utils.getJSON(activity, "http://" + Utils.JA_ADDRESS + "/api/get_last_version", null);
                try {
                    last_check.createNewFile();
                } catch (IOException e) {
                    //
                }
                if (json.getResult() != null) {
                    Matcher matcher = Pattern.compile("com.juickadvanced-(\\d+).apk").matcher(json.getResult());
                    if (matcher.find()) {
                        String version = matcher.group(1);
                        try {
                            int updateVersionCode = Integer.parseInt(version);
                            if (updateVersionCode > currentVersionCode) {
                                if (!new File(updatesDir, "ignore-"+version).exists()) {
                                    JsonObject jsonElement = (JsonObject)new Gson().fromJson(json.getResult(), JsonElement.class);
                                    JsonPrimitive url = (JsonPrimitive)jsonElement.get("url");
                                    updateURL = url.getAsString();
                                    JsonPrimitive desc = (JsonPrimitive)jsonElement.get("description");
                                    updateDescription = desc.getAsString();
                                    MainActivity.updateAvailable = version;
                                    activity.handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            setUpdateVisible(activity);
                                        }
                                    });
                                }
                            } else {
                                File file = getUpdateAPK();
                                if (file.exists()) {
                                    file.delete();
                                }
                            }
                        } catch (Exception e) {
                            ACRA.getErrorReporter().handleException(e, false);
                        }
                    }
                }
                super.run();
            }
        }.start();
    }

    public static void setUpdateVisible(final MainActivity activity) {
        if (MainActivity.updateAvailable != null) {
            if (activity.resumed) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle("New Beta available");
                View view = activity.getLayoutInflater().inflate(R.layout.update_dialog, null);
                TextView changes = (TextView)view.findViewById(R.id.changes);
                changes.setText(updateDescription);
                TextView versionCode = (TextView)view.findViewById(R.id.versionCode);
                versionCode.setText(MainActivity.updateAvailable);

                builder.setView(view);
                builder.setPositiveButton(activity.getString(R.string.DownloadAndInstall), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        Toast.makeText(activity, activity.getString(R.string.DownloadWillStartNow), Toast.LENGTH_LONG).show();
                        new Thread() {
                            @Override
                            public void run() {
                                final Utils.BINResponse binary = Utils.getBinary(activity, updateURL, new Utils.DownloadProgressNotification() {
                                    @Override
                                    public void notifyDownloadProgress(int progressBytes) {
                                    }

                                    @Override
                                    public void notifyHttpClientObtained(HttpClient client) {
                                        //To change body of implemented methods use File | Settings | File Templates.
                                    }
                                }, -1);

                                Runnable afterDownload = new Runnable() {
                                    @Override
                                    public void run() {
                                        if (binary.getErrorText() == null) {
                                            try {
                                                FileOutputStream fos = new FileOutputStream(getUpdateAPK());
                                                fos.write(binary.getResult());
                                                fos.close();
                                                installUpdate(activity);
                                            } catch (Exception e) {
                                                Toast.makeText(activity, "Store Juick Advanced update: "+e.toString(), Toast.LENGTH_LONG).show();
                                            }
                                        } else {
                                            Toast.makeText(activity, "Download Juick Advanced update: "+binary.getErrorText(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                };
                                if (activity.resumed) {
                                    activity.handler.post(afterDownload);
                                } else {
                                    MainActivity.installerOnResume = afterDownload;
                                }
                                super.run();    //To change body of overridden methods use File | Settings | File Templates.
                            }
                        }.start();
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
                            new File(updatesDir, "ignore-"+ MainActivity.updateAvailable).createNewFile();
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


    class ReleaseFeatures {
        int textId;
        String sinceRelease;

        ReleaseFeatures(String sinceRelease, int textId) {
            this.sinceRelease = sinceRelease;
            this.textId = textId;
        }
    }

    ReleaseFeatures[] features = new ReleaseFeatures[]{
            new ReleaseFeatures("2012120501", R.string.rf_2012120501),
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


    private static void doLocalInstall(final Activity context) {
        File file = new File(Environment.getExternalStorageDirectory(), "juick-advanced-update.apk");
        Intent promptInstall = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        promptInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(promptInstall);
    }

    private static void launchApplicationSettingsForUnknownSources(final Context context) {
        if (context instanceof MainActivity) {
            ((MainActivity) context).installerOnResume = new Runnable() {
                @Override
                public void run() {
                    installUpdate((MainActivity) context);
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

    public static void installUpdate(final Activity context) {
        String string = Settings.System.getString(context.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS);
        if ("1".equals(string)) {
            doLocalInstall(context);
        } else {
            new AlertDialog.Builder(context)
                    .setMessage("You must allow 'unknown sources' to install update")
                    .setPositiveButton("Enable unknown sources", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            launchApplicationSettingsForUnknownSources(context);
                            //To change body of implemented methods use File | Settings | File Templates.
                        }
                    }).setNegativeButton("Skip update", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (context instanceof MainActivity) {
                        MainActivity ma = (MainActivity)context;
                        try {
                            new File(new File(context.getFilesDir(),"updates"), "ignore-"+ma.updateAvailable).createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                    dialog.cancel();
                }
            }).show();

        }
    }


}
