package com.juick.android.juick;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.juick.android.*;
import com.juick.android.Utils;
import com.juick.android.ja.JAUnansweredMessagesSource;
import com.juickadvanced.*;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.protocol.JuickHttpAPI;
import com.juickadvanced.protocol.JuickLoginProcedure;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:10 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickMicroBlog implements MicroBlog {

    public static JuickMicroBlog instance;
    Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter;

    public JuickMicroBlog() {
        instance = this;
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String msg, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, final Utils.Function<Void, String> then) {
        final RESTResponse restResponse = sendMessage(newMessageActivity, msg, pid, lat, lon, acc, attachmentUri, attachmentMime, progressDialog, progressHandler, progressDialogCancel);
        newMessageActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                then.apply(restResponse.getErrorText());
            }
        });
    }

    public static RESTResponse sendMessage(final Context context, String txt, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, final ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel) {
        try {
            final String end = "\r\n";
            final String twoHyphens = "--";
            final String boundary = "****+++++******+++++++********";

            URL apiUrl = new URL(JuickHttpAPI.getAPIURL() + "post");
            final HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setConnectTimeout(10000);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Charset", "UTF-8");
            conn.setRequestProperty("Authorization", JuickAPIAuthorizer.getBasicAuthString(context.getApplicationContext()));
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            String outStr = twoHyphens + boundary + end;
            outStr += "Content-Disposition: form-data; name=\"body\"" + end + end + txt + end;

            if (pid > 0) {
                outStr += twoHyphens + boundary + end;
                outStr += "Content-Disposition: form-data; name=\"place_id\"" + end + end + pid + end;
            }
            if (lat != 0 && lon != 0) {
                outStr += twoHyphens + boundary + end;
                outStr += "Content-Disposition: form-data; name=\"lat\"" + end + end + String.valueOf(lat) + end;
                outStr += twoHyphens + boundary + end;
                outStr += "Content-Disposition: form-data; name=\"lon\"" + end + end + String.valueOf(lon) + end;
                if (acc > 0) {
                    outStr += twoHyphens + boundary + end;
                    outStr += "Content-Disposition: form-data; name=\"acc\"" + end + end + String.valueOf(acc) + end;
                }
            }

            if (attachmentUri != null && attachmentUri.length() > 0 && attachmentMime != null) {
                String fname = "file." + (attachmentMime.equals("image/jpeg") ? "jpg" : "3gp");
                outStr += twoHyphens + boundary + end;
                outStr += "Content-Disposition: form-data; name=\"attach\"; filename=\"" + fname + "\"" + end + end;
            }
            byte outStrB[] = outStr.getBytes("utf-8");

            String outStrEnd = twoHyphens + boundary + twoHyphens + end;
            byte outStrEndB[] = outStrEnd.getBytes();

            int size = outStrB.length + outStrEndB.length;

            FileInputStream fileInput = null;
            if (attachmentUri != null && attachmentUri.length() > 0) {
                fileInput = context.getContentResolver().openAssetFileDescriptor(Uri.parse(attachmentUri), "r").createInputStream();
                size += fileInput.available();
                size += 2; // \r\n (end)
            }

            if (progressDialog != null) {
                final int fsize = size;
                progressHandler.sendEmptyMessage(fsize);
            }

            conn.setFixedLengthStreamingMode(size);
            conn.connect();
            OutputStream out = conn.getOutputStream();
            out.write(outStrB);

            if (attachmentUri != null && attachmentUri.length() > 0 && fileInput != null) {
                byte[] buffer = new byte[4096];
                int length = -1;
                int total = 0;
                int totallast = 0;
                while ((length = fileInput.read(buffer, 0, 4096)) != -1 && progressDialogCancel.bool == false) {
                    out.write(buffer, 0, length);
                    total += length;
                    if (((int) (total / 102400)) != totallast) {
                        totallast = (int) (total / 102400);
                        progressHandler.sendEmptyMessage(total);
                    }
                }
                if (progressDialogCancel.bool == false) {
                    out.write(end.getBytes());
                }
                fileInput.close();
                progressHandler.sendEmptyMessage(size);
            }
            if (progressDialogCancel.bool == false) {
                out.write(outStrEndB);
                out.flush();
            }
            out.close();

            if (progressDialogCancel.bool) {
                return new RESTResponse("Cancelled", false, null);
            } else {
                boolean b = conn.getResponseCode() == 200;
                if (!b) {
                    return new RESTResponse("HTTP "+conn.getResponseCode()+": "+conn.getResponseMessage(), false, null);
                } else {
                    return new RESTResponse(null, false, "OK");
                }
            }
        } catch (final Exception e) {
            Log.e("sendOpinion", e.toString());
            return new RESTResponse(e.toString(), false, null);
        }
    }

    @Override
    public void initialize() {
        Utils.authorizers.add(0, new JuickAPIAuthorizer());
        Utils.authorizers.add(0, new JuickWebAuthorizer());
    }

    @Override
    public String getCode() {
        return JuickMessageID.CODE;
    }

    @Override
    public String getMicroblogName(Context context) {
        return context.getString(R.string.JuickMicroblog);
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(final JuickMessage jmsg) {
        if (jmsg.User == null || jmsg.User.UID == 0) return UserpicStorage.NO_AVATAR;
        return new UserpicStorage.AvatarID() {
            @Override
            public String toString(int size) {
                if (isLargeSize(size)) {
                    return "J"+jmsg.User.UID+"L";
                } else {
                    return "J"+jmsg.User.UID;
                }
            }

            boolean isLargeSize(int size) {
                return size > 64;
            }

            @Override
            public String getURL(int size) {
                if (!isLargeSize(size)) {
                    return "http://i.juick.com/as/" + jmsg.User.UID + ".png";
                } else {
                    return "http://i.juick.com/a/" + jmsg.User.UID + ".png";
                }
            }
        };
    }

    @Override
    public MessageID createKey(String keyString) {
        return JuickMessageID.fromString(keyString);
    }

    @Override
    public MessageMenu getMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        return new MessageMenu(activity, messagesSource, listView, listAdapter);
    }

    @Override
    public String getPostNote(NewMessageActivity newMessageActivity) {
        return null;
    }

    @Override
    public void launchTagsForNewPost(final NewMessageActivity newMessageActivity) {
        JuickMicroBlog.withUserId(newMessageActivity, new Utils.Function<Void, Pair<Integer, String>>() {
            @Override
            public Void apply(Pair<Integer, String> cred) {
                Intent i = new Intent(newMessageActivity, TagsActivity.class);
                i.setAction(Intent.ACTION_PICK);
                i.putExtra("uid", cred.first.intValue());
                i.putExtra("multi", true);
                newMessageActivity.startActivityForResult(i, NewMessageActivity.ACTIVITY_TAGS);
                return null;
            }
        });

    }

    @Override
    public OperationInProgress postReply(Activity context, MessageID mid, JuickMessage threadStarter, JuickMessage selectedReply, String msg, String attachmentUri, String attachmentMime, Utils.Function<Void, String> then) {
        String msgnum = "#" + ((JuickMessageID)mid).getMid();
        if (selectedReply != null && selectedReply.getRID() > 0) {
            msgnum += "/" + selectedReply.getRID();
        }
        final String body = msgnum + " " + msg;
        if (attachmentUri == null) {
            return postText(context, body, then);
        } else {
            return postMedia(context, body, attachmentUri, attachmentMime, then);
        }
    }

    @Override
    public ThreadFragment.ThreadExternalUpdater getThreadExternalUpdater(Activity activity, MessageID mid) {
        return new WsClient(activity, mid);

    }

    @Override
    public int getPiority() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public static JuickMessagesSource getSubscriptionsMessagesSource(MainActivity activity, int labelId) {
        return new JuickCompatibleURLMessagesSource(activity.getString(labelId), "juick_api_subscriptions", activity, JuickHttpAPI.getAPIURL() + "home");
    }


    @Override
    public void addNavigationSources(final ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity activity) {
        final SharedPreferences sp = activity.sp;
        xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(activity, XMPPService.class);
        navigationItems.add(new MainActivity.NavigationItem(10001, R.string.navigationAll, R.drawable.navicon_juick, null) {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                args.putSerializable("messagesSource", new JuickAllMessagesSource(activity));
                activity.runDefaultFragmentWithBundle(args, this);
            }

            @Override
            public void restoreReadMarker() {
                activity.mf.clearSavedPosition(activity);
            }
        });
        MainActivity.NavigationItem subscriptionsItem = new MainActivity.NavigationItem(10100, R.string.navigationSubscriptions, R.drawable.navicon_juick, null) {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                JuickMessagesSource ms = getSubscriptionsMessagesSource(activity, R.string.navigationSubscriptions);
                ms.setKind("home");
                args.putSerializable("messagesSource", ms);
                activity.runDefaultFragmentWithBundle(args, this);
            }

            @Override
            public ArrayList<String> getMenuItems() {
                if (JuickAPIAuthorizer.getJuickAccountName(activity) != null) {
                    String s = "Logout";
                    ArrayList<String> strings = new ArrayList<String>();
                    strings.add(s);
                    return strings;
                } else {
                    return null;
                }
            }

            @Override
            public void handleMenuAction(int which, String value) {
                switch(which) {
                    case 0:
                        activity.logoutService(JuickMessageID.CODE);
                        break;
                }
            }


        };
        navigationItems.add(subscriptionsItem);
        MainActivity.NavigationItem subscriptionsWebItem = new MainActivity.NavigationItem(10009, R.string.navigationSubscriptionsWeb, R.drawable.navicon_juick, null) {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                JuickMessagesSource ms = new JuickWebCompatibleURLMessagesSource(activity.getString(labelId), "juick_web_subscriptions", activity, "http://juick.com/?show=my");
                ms.setKind("homeweb");
                args.putSerializable("messagesSource", ms);
                activity.runDefaultFragmentWithBundle(args, this);
            }

        };
        navigationItems.add(subscriptionsWebItem);
        navigationItems.add(new MainActivity.NavigationItem(10002, R.string.navigationUnanswered, R.drawable.navicon_juickadvanced, "msrcUnanswered") {
            MainActivity.NavigationItem thiz = this;

            @Override
            public void action() {
                JuickAdvancedApplication.confirmAdvancedPrivacy(
                        activity,
                        new Runnable() {
                            @Override
                            public void run() {
                                final Bundle args = new Bundle();
                                JAUnansweredMessagesSource ms = new JAUnansweredMessagesSource(activity);
                                args.putSerializable("messagesSource", ms);
                                activity.runDefaultFragmentWithBundle(args, thiz);
                            }

                        }, new Runnable() {
                            @Override
                            public void run() {
                                activity.restoreLastNavigationPosition();
                            }
                        }
                );
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(10003, R.string.navigationTop, R.drawable.navicon_juick, "msrcTopMessages") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(activity.getString(labelId), "top", activity).putArg("popular", "1");
                ms.setKind("popular");
                args.putSerializable("messagesSource", ms);
                activity.runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(10003, R.string.navigationPhoto, R.drawable.navicon_juick, "msrcWithPhotos") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(activity.getString(labelId), "photos", activity).putArg("media", "all");
                ms.setKind("media");
                args.putSerializable("messagesSource", ms);
                activity.runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(10004, R.string.navigationMy, R.drawable.navicon_juick, "msrcMyBlog") {
            @Override
            public void action() {
                final MainActivity.NavigationItem thiz = this;
                runAuthorized(new com.juickadvanced.Utils.Function<Void, String>() {
                    @Override
                    public Void apply(String s) {
                        withUserId(activity, new Utils.Function<Void, Pair<Integer, String>>() {
                            @Override
                            public Void apply(Pair<Integer, String> cred) {
                                final Bundle args = new Bundle();
                                JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(activity.getString(labelId), "my", activity).putArg("user_id", "" + cred.first);
                                ms.setKind("my_home");
                                args.putSerializable("messagesSource", ms);
                                activity.runDefaultFragmentWithBundle(args, thiz);
                                return null;
                            }
                        });
                        return null;
                    }
                }, activity);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(10005, R.string.navigationSrachiki, R.drawable.navicon_jugregator, "msrcSrachiki") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(activity.getString(labelId), "srachiki", activity, "http://s.jugregator.org/api");
                ms.setCanNext(false);
                ms.setKind("srachiki");
                args.putSerializable("messagesSource", ms);
                activity.runDefaultFragmentWithBundle(args, this);
            }
        });
//        navigationItems.add(new MainActivity.NavigationItem(10006, R.string.navigationPrivate, R.drawable.navicon_juick, "msrcPrivate") {
//            @Override
//            public void action() {
//                final Bundle args = new Bundle();
//                JuickMessagesSource ms = new JuickWebCompatibleURLMessagesSource(activity.getString(labelId), "private", activity, "http://juick.com/?show=private");
//                args.putSerializable("messagesSource", ms);
//                activity.runDefaultFragmentWithBundle(args, this);
//            }
//        });
        navigationItems.add(new MainActivity.NavigationItem(10007, R.string.navigationDiscuss, R.drawable.navicon_juick, "msrcDiscuss") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                JuickMessagesSource ms = new JuickWebCompatibleURLMessagesSource(activity.getString(labelId), "discussions", activity, "http://juick.com/?show=discuss");
                args.putSerializable("messagesSource", ms);
                activity.runDefaultFragmentWithBundle(args, this);
            }
        });
        navigationItems.add(new MainActivity.NavigationItem(10008, R.string.navigationJuboRSS, R.drawable.navicon_jubo, "msrcJubo") {
            @Override
            public void action() {
                final int myIndex = navigationItems.indexOf(this);
                String juboRssURL = sp.getString("juboRssURL", "");
                if (juboRssURL.length() == 0) {
                    xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                        @Override
                        public void withService(XMPPService service) {
                            boolean canAskJubo = false;
                            String message = getString(R.string.JuboRSSURLIsUnknown);
                            boolean canManuallyConnectJabber = false;
                            if (!service.juboOnline) {
                                boolean useXMPP = sp.getBoolean("useXMPP", false);
                                if (!useXMPP) {
                                    message += getString(R.string.ICouldAskButXMPPIsOff);
                                } else {
                                    if (service.botOnline) {
                                        message += getString(R.string.JuboNotThere);
                                    } else {
                                        if (sp.getBoolean("useXMPPOnlyForBL", false)) {
                                            canManuallyConnectJabber = true;
                                        }
                                        message += getString(R.string.ICouldButXMPPIfNotWorking);
                                    }
                                }
                            } else {
                                canAskJubo = true;
                                message += getString(R.string.OrICanAskJuboNoe);
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                                    .setTitle(R.string.navigationJuboRSS)
                                    .setMessage(message)
                                    .setCancelable(true)
                                    .setNeutralButton(getString(R.string.EnterJuboRSSURLManually), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            enterJuboURLManually(myIndex, activity);
                                        }
                                    })
                                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
                                            activity.restoreLastNavigationPosition();
                                        }
                                    });
                            if (canManuallyConnectJabber) {
                                builder.setPositiveButton(service.getString(R.string.StartServiceAndTry), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        askJuboFirst(myIndex, true, activity);
                                    }
                                });
                            }
                            if (canAskJubo) {
                                builder.setPositiveButton(getString(R.string.AskJuBo), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        askJuboFirst(myIndex, false, activity);
                                    }
                                });
                            }
                            builder.show();
                        }
                    });
                } else {
                    openJuboMessages(myIndex, activity);
                }
            }

        });
    }

    @Override
    public void decorateNewMessageActivity(final NewMessageActivity newMessageActivity) {
        newMessageActivity.setTitle(R.string.Juick__New_message);
        if (newMessageActivity.data.microblog.equals(JuickMessageID.CODE)) {
            Thread thr = new Thread(new Runnable() {

                public void run() {
                    String jsonUrl = JuickHttpAPI.getAPIURL() + "postform";

                    LocationManager lm = (LocationManager) newMessageActivity.getSystemService(Context.LOCATION_SERVICE);
                    Location loc = null;
                    try {
                        loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    } catch (Exception ex) {
                        // java.lang.SecurityException: Provider network requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission
                    }
                    if (loc != null) {
                        jsonUrl += "?lat=" + loc.getLatitude() + "&lon=" + loc.getLongitude() + "&acc=" + loc.getAccuracy() + "&fixage=" + Math.round((System.currentTimeMillis() - loc.getTime()) / 1000);
                    }

                    final String jsonStr = Utils.getJSON(newMessageActivity, jsonUrl, null).getResult();

                    newMessageActivity.runOnUiThread(new Runnable() {

                        public void run() {
                            if (jsonStr != null) {

                                try {
                                    JSONObject json = new JSONObject(jsonStr);
                                    if (json.has("facebook")) {
                                        newMessageActivity.etTo.setText(newMessageActivity.etTo.getText() + ", Facebook");
                                    }
                                    if (json.has("twitter")) {
                                        newMessageActivity.etTo.setText(newMessageActivity.etTo.getText() + ", Twitter");
                                    }
                                    if (json.has("place")) {
                                        JSONObject jsonPlace = json.getJSONObject("place");
                                        newMessageActivity.data.pidHint = jsonPlace.getInt("pid");
                                        newMessageActivity.bLocationHint.setVisibility(View.VISIBLE);
                                        newMessageActivity.bLocationHint.setText(jsonPlace.getString("name"));
                                    }
                                } catch (JSONException e) {
                                    System.err.println(e);
                                }
                            }
                            newMessageActivity.setProgressBarIndeterminateVisibility(false);
                        }
                    });
                }
            },"Post message");
            thr.start();
        }
    }

    @Override
    public void getChildren(Activity context, MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(context, "dummy").getChildren(mid, notifications, cont);
    }

    @Override
    public JuickMessage createMessage() {
        JuickMessage juickMessage = new JuickMessage();
        juickMessage.microBlogCode = JuickMessageID.CODE;
        return juickMessage;
    }


    private String getString(int id) {
        return JuickAdvancedApplication.instance.getString(id);
    }

    public OperationInProgress postText(final Activity context, final String body, final Utils.Function<Void, String> then) {
        try {
            final String encode = URLEncoder.encode(body, "utf-8");
            final boolean[] handled = new boolean[] { false };
            Thread thr = new Thread(new Runnable() {

                public void run() {
                    final RESTResponse restResponse = Utils.postJSON(context, JuickHttpAPI.getAPIURL() + "post", "body=" + encode);
                    final String ret = restResponse.getResult();
                    if (!handled[0])
                        handled[0] = true;
                        context.runOnUiThread(new Runnable() {

                        public void run() {
                            then.apply(restResponse.errorText);
                        }
                    });
                }
            },"Post comment");
            thr.start();
            return new OperationInProgress() {
                @Override
                public void preliminarySuccess() {
                    if (!handled[0]) {
                        handled[0] = true;
                        context.runOnUiThread(new Runnable() {

                            public void run() {
                                then.apply(null);
                            }
                        });
                    }
                }
            };
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
            then.apply(e.toString());
            return null;
        }
    }

    public OperationInProgress postMedia(final Activity context, final String body, final String attachmentUri, final String attachmentMime, final Utils.Function<Void, String> then) {
        final boolean handled[] = new boolean[] { false };
        final ProgressDialog progressDialog = new ProgressDialog(context);
        final NewMessageActivity.BooleanReference progressDialogCancel = new NewMessageActivity.BooleanReference(false);
        progressDialogCancel.bool = false;
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

            public void onCancel(DialogInterface arg0) {
                progressDialogCancel.bool = true;
            }
        });
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(0);
        progressDialog.show();
        final Handler progressHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if (progressDialog.getMax() < msg.what) {
                    progressDialog.setMax(msg.what);
                } else {
                    progressDialog.setProgress(msg.what);
                }
            }
        };
        Thread thr = new Thread(new Runnable() {

            public void run() {
                final RESTResponse res = sendMessage(context, body, 0, 0, 0, 0, attachmentUri, attachmentMime, progressDialog, progressHandler, progressDialogCancel);
                if (!handled[0]) {
                    handled[0] = true;
                    context.runOnUiThread(new Runnable() {

                        public void run() {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    //
                                }
                            }
                            then.apply(res.errorText);
                        }
                    });
                }
            }
        },"Post media");
        thr.start();
        return new OperationInProgress() {
            @Override
            public void preliminarySuccess() {
                if (!handled[0]) {
                    handled[0] = true;
                    context.runOnUiThread(new Runnable() {

                        public void run() {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    //
                                }
                            }
                            then.apply(null);
                        }
                    });
                }
            }
        };
    }

    public static void withUserId(final Activity activity, final Utils.Function<Void,Pair<Integer, String>> action) {
        new Thread("withUserId password getter"){
            @Override
            public void run() {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                final String myUserId = atoi(sp.getString("myUserId", ""));
                final String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(activity);
                if (myUserId.length() == 0) {
                    String password = JuickAPIAuthorizer.getPassword(activity);
                    final RESTResponse restResponse = JuickLoginProcedure.validateLoginPassword(juickAccountName, password, new DefaultHTTPClientService(activity));
                    if (restResponse.getResult() != null) {
                        final String[] usernameId = restResponse.getResult().split(":");
                        sp.edit().putString("myUserId", usernameId[1]).commit();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                action.apply(new Pair<Integer, String>(Integer.parseInt(usernameId[1]), usernameId[0]));
                            }
                        });
                    } else {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "Error getting user ID: "+restResponse.getErrorText(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } else {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            action.apply(new Pair<Integer, String>(Integer.parseInt(myUserId), juickAccountName));
                        }
                    });

                }
            }
        }.start();
    }

    public void runAuthorized(final Utils.Function<Void, String> runWithLogin, final Activity mainActivity) {
        new Thread("runAuthorized") {
            @Override
            public void run() {
                JuickAPIAuthorizer authorizer = (JuickAPIAuthorizer) Utils.getAuthorizer("http://api.juick.com/");
                authorizer.authorize(mainActivity, false, false, "http://api.juick.com/required", new Utils.Function<Void, String>() {
                    @Override
                    public Void apply(final String s) {
                        if (!Utils.URLAuth.REFUSED_AUTH.equals(s)) {
                            mainActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (s != null) {
                                        runWithLogin.apply(s);
                                    } else {
                                        if (mainActivity instanceof MainActivity) {
                                            ((MainActivity) mainActivity).restoreLastNavigationPosition();
                                        }
                                    }
                                }
                            });
                        } else {
                            mainActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mainActivity instanceof MainActivity) {
                                        ((MainActivity) mainActivity).restoreLastNavigationPosition();
                                    }
                                }
                            });
                        }
                        return null;
                    }
                });
            }
        }.start();
    }



    private static String atoi(String myUserId) {
        try {
            Integer.parseInt(myUserId);
            return myUserId;
        } catch (Exception ex) {
            return "";
        }
    }

    public static void obtainProperUserIdByName(final Activity activity, final String userName, String titleOfDialog, final Utils.Function<Void, Pair<String,String>> withUserId) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle(titleOfDialog);
        pd.setMessage(activity.getString(R.string.ConnectingToWwwJuick));
        pd.setIndeterminate(true);
        pd.show();
        final AndroidHttpClient httpClient = AndroidHttpClient.newInstance(activity.getString(R.string.com_juick));
        new Thread("UserID obtainer") {
            @Override
            public void run() {
                try {
                    RESTResponse json = Utils.getJSON(activity, JuickHttpAPI.getAPIURL() + "users?uname=" + userName, null);
                    if (json.getErrorText() != null || !json.getResult().contains("uid")){
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, activity.getString(R.string.UnableToDetectNick) + activity.getString(R.string.NoUserAccount), Toast.LENGTH_LONG).show();
                            }});
                    } else {
                        JSONObject jo = new JSONArray(json.getResult()).getJSONObject(0);
                        final int uid = jo.getInt("uid");
                        final String uname = jo.getString("uname");
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                withUserId.apply(new Pair<String, String>(""+uid, uname));
                            }
                        });
                    }
                } catch (final Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, activity.getString(R.string.UnableToDetectNick) + e.toString(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    httpClient.close();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.hide();
                        }
                    });
                }
            }
        }.start();
    }

    private void askJuboFirst(final int juboIndex, final boolean startServiceFromTemporary, final MainActivity activity) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setIndeterminate(true);
        pd.setMessage(getString(R.string.TalkingToJuBo));
        pd.show();
        Runnable dowithXMPP = new Runnable() {
            @Override
            public void run() {
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        service.askJuboRSS();
                        activity.handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                checkJuboReturnedRSS(juboIndex, pd, activity);
                                if (startServiceFromTemporary) {
                                    activity.sp.edit().putBoolean("useXMPPOnlyForBL", true).commit();
                                }
                            }
                        }, 10000);
                    }
                });
            }
        };
        if (startServiceFromTemporary) {
            activity.sp.edit().putBoolean("useXMPPOnlyForBL", false).commit();
            MainActivity.toggleXMPP(activity);
            activity.handler.postDelayed(dowithXMPP, 5000);
        } else {
            dowithXMPP.run();
        }
    }

    private void checkJuboReturnedRSS(final int juboIndex, final ProgressDialog pd, final MainActivity activity) {
        xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
            @Override
            public void withService(XMPPService service) {
                pd.hide();
                if (service.juboRSS != null) {
                    activity.sp.edit().putString("juboRssURL", service.juboRSS).commit();
                    openJuboMessages(juboIndex, activity);
                } else {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.navigationJuboRSS)
                            .setMessage(service.juboRSSError)
                            .setCancelable(true)
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    activity.restoreLastNavigationPosition();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                    activity.restoreLastNavigationPosition();
                                }
                            }).show();
                }
            }
        });
    }

    private void enterJuboURLManually(final int myIndex, final MainActivity activity) {
        final EditText et = new EditText(activity);
        et.setSingleLine(true);
        et.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.EnterJuboRSSURLManually)
                .setView(et)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.sp.edit().putString("juboRssURL", "" + et.getText()).commit();
                        openJuboMessages(myIndex, activity);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        activity.restoreLastNavigationPosition();
                    }
                }).show();
    }

    private void openJuboMessages(int myIndex, final MainActivity activity) {
        final Bundle args = new Bundle();
        args.putSerializable("messagesSource", new JuboMessagesSource(activity));
        activity.runDefaultFragmentWithBundle(args, activity.navigationItems.get(myIndex));
    }


    public static JsonObject convertJuickMessageToJSON(JuickMessage message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("mid", ((JuickMessageID)message.getMID()).getMid());
        if (message.getRID() != 0) {
            obj.addProperty("rid", message.getRID());
            if (message.getReplyTo() != 0) {
                obj.addProperty("replyto", message.getReplyTo());
            }
        }
        if (message.tags != null && message.tags.size() > 0) {
            JsonArray tags = new JsonArray();
            for (String t : message.tags) {
                tags.add(new JsonPrimitive(t));
            }
            obj.add("tags", tags);
        }
        obj.addProperty("body", message.Text);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        obj.addProperty("timestamp", sdf.format(message.Timestamp));
        JsonObject user = new JsonObject();
        user.addProperty("uname", message.User.UName);
        if (message.User.UID > 0) {
            user.addProperty("uid", message.User.UID);
        }
        obj.add("user", user);
        return obj;
    }

    public JSONArray getUserTags(View view, int tagsUID) {
        String url = JuickHttpAPI.getAPIURL() + "tags";
        File globalTagsCache = new File(view.getContext().getCacheDir(), "tags-" + tagsUID + ".json");
        String cachedString = null;
        if (tagsUID != 0) { // -1 == mine
            url += "?user_id=" + tagsUID;
        }
        if (globalTagsCache.exists() && globalTagsCache.lastModified() > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) {
            cachedString = XMPPService.readFile(globalTagsCache);
        }
        final String jsonStr = cachedString != null ? cachedString : Utils.getJSON(view.getContext(), url, null).getResult();
        if (jsonStr != null && cachedString == null) {
            XMPPService.writeStringToFile(globalTagsCache, jsonStr);
        }
        try {
            return  jsonStr != null ? new JSONArray(jsonStr) : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
