package com.juick.android.bnw;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.MessageMenu;
import com.juick.android.JuickMessagesAdapter;
import com.juick.android.MessagesActivity;
import com.juick.android.Utils;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.juick.android.Utils.postJSON;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/10/12
 * Time: 10:54 AM
 * To change this template use File | Settings | File Templates.
 */
public class BNWMessageMenu extends MessageMenu {
    public BNWMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        super(activity, messagesSource, listView, listAdapter);
    }

    @Override
    protected void addMicroblogSpecificCommands(String UName) {
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Subscribe_to) + " @" + UName) {
            @Override
            public void run() {
                actionSubscribeUser();
            }
        });
        if (listSelectedItem.getRID() == 0) {
            menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Recommend_message)) {
                @Override
                public void run() {
                    actionRecommendMessage();
                }
            });
        }
        menuActions.add(new RunnableItem(activity.getResources().getString(R.string.Blacklist) + " @" + UName) {
            @Override
            public void run() {
                actionBlacklistUser();
            }
        });
    }


    protected void actionBlacklistUser() {
        confirmAction(R.string.ReallyBlacklist, new Runnable() {
            @Override
            public void run() {
                Map<String, String> args = new HashMap<String, String>();
                args.put("user", listSelectedItem.User.UName);
                simpleApiCall("blacklist", args);
            }
        });
    }

    public static Utils.RESTResponse apiCall(Context context, String call, Map<String, String> args)  {
        try {
            StringBuilder builder = new StringBuilder();
            Iterator it = args.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry m = (Map.Entry) it.next();
                builder.append((String) m.getKey() + "=" + URLEncoder.encode((String) m.getValue(), "utf-8"));
                if (!it.hasNext())
                    break;
                builder.append("&");
            }
            return postJSON(context, "http://ipv4.bnw.im/api/" + call + "?", builder.toString());
        } catch (UnsupportedEncodingException e) {
            return new Utils.RESTResponse(e.toString(), false, null);
        }
    }

    private void simpleApiCall(final String call, final Map<String, String> args) {
        Thread thr = new Thread(new Runnable() {

            public void run() {
                final Utils.RESTResponse restResponse = apiCall(activity, call, args);
                activity.runOnUiThread(new Runnable() {

                    public void run() {
                        String text = "WTF?";
                        try {
                            if (restResponse.getResult() != null) {
                                JSONObject ret = new JSONObject(restResponse.getResult());
                                if (ret.getBoolean("ok"))
                                    text = "OK. " + ret.getString("desc");
                                else
                                    text = "ERROR. " + ret.getString("desc");
                            } else
                                text = restResponse.getErrorText();
                        } catch (Exception e) {
                            Log.e("simpleApiCall2", e.toString());
                        }
                        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        thr.start();
    }

    @Override
    protected void actionSubscribeUser() {
        Map<String, String> args = new HashMap<String, String>();
        args.put("user", listSelectedItem.User.UName);
        simpleApiCall("subscriptions/add", args);
    }

    @Override
    protected void actionRecommendMessage() {
        Map<String, String> args = new HashMap<String, String>();
        args.put("message", ((BnwMessageID) listSelectedItem.getMID()).getId());
        simpleApiCall("recommend", args);
    }

    protected void actionUserBlog() {
        Intent i = new Intent(activity, MessagesActivity.class);
        i.putExtra("messagesSource", new BnwCompatibleMessageSource(activity, "@" + listSelectedItem.User.UName, "/show?user=" + listSelectedItem.User.UName));
        activity.startActivity(i);
    }

    @Override
    public boolean isDialogMode() {
        return false;
    }

    @Override
    protected void maybeAddDeleteItem() {
        //
    }
}
