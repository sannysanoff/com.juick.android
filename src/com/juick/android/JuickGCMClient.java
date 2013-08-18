package com.juick.android;

import android.content.Context;
import com.juick.android.juick.JuickMessagesSource;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/5/13
 * Time: 12:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuickGCMClient implements GCMIntentService.GCMMessageListener {

    Context context;
    private boolean started;

    public JuickGCMClient(Context context) {
        this.context = context;
    }

    @Override
    public void onGCMMessage(final String message, Set<String> categories) {
        final JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(message);
        } catch (JSONException e) {
            return;
        }
        if (jsonObject.has("mid")) {
            Utils.ServiceGetter<XMPPService> xmppServiceGetter = new Utils.ServiceGetter<XMPPService>(context, XMPPService.class);
            xmppServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                @Override
                public void withService(XMPPService service) {
                    try {
                        JuickMessage juickMessage = JuickMessagesSource.initFromJSON(jsonObject);
                        XMPPService.IncomingMessage msg = null;
                        if (juickMessage.getRID() == 0) {
                            if (juickMessage.tags.contains("private")) {
                                msg = new XMPPService.JuickPrivateIncomingMessage(
                                        "@"+juickMessage.User.UName,
                                        juickMessage.Text,
                                        "#"+((JuickMessageID)juickMessage.getMID()).getMid(),
                                        juickMessage.Timestamp);
                            } else {
                                XMPPService.JuickSubscriptionIncomingMessage imsg = new XMPPService.JuickSubscriptionIncomingMessage(
                                        "@"+juickMessage.User.UName,
                                        juickMessage.Text,
                                        "#"+((JuickMessageID)juickMessage.getMID()).getMid(),
                                        juickMessage.Timestamp);
                                imsg.tags = new ArrayList<String>();
                                imsg.tags.addAll(juickMessage.tags);
                                msg = imsg;
                            }
                        } else {
                            XMPPService.JuickThreadIncomingMessage tmsg = new XMPPService.JuickThreadIncomingMessage(
                                    "@"+juickMessage.User.UName,
                                    juickMessage.Text,
                                    "#"+((JuickMessageID)juickMessage.getMID()).getMid()+"/"+juickMessage.getRID(),
                                    juickMessage.Timestamp);
                            tmsg.replyto = juickMessage.getReplyTo();
                            msg = tmsg;
                        }
                        XMPPService.juickGCMReceived++;
                        XMPPService.juickGCMKLasReceived = new Date();
                        service.handleJuickMessage(XMPPService.JUICK_ID, "", msg);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Override
    public void onRegistration(final String regid) {
        new Thread("juick gcm server reg") {
            @Override
            public void run() {
                registerOnServer(regid);
            }
        }.start();
    }

    @Override
    public void onUnregistration(final String registrationId) {
        new Thread("juick gcm server unreg") {
            @Override
            public void run() {
                unregisterOnServer(registrationId);
                XMPPService.juickGCMStatus = "Unregistered at "+new Date();
            }
        }.start();
    }

    private void registerOnServer(String registrationId) {
        Utils.RESTResponse json = Utils.getJSON(context, "http://api.juick.com/android/register?regid=" + registrationId, null);
        if (json.getResult() != null) {
            XMPPService.juickGCMStatus = "Registered";
            JuickAdvancedApplication.showXMPPToast("JuickGCMClient register success");
        } else {
            XMPPService.juickGCMStatus = "Failed at "+new Date();
            JuickAdvancedApplication.showXMPPToast("JuickGCMClient register FAIL");
        }
    }

    private void unregisterOnServer(String registrationId) {
        Utils.getJSON(context, "http://api.juick.com/android/unregister?regid=" + registrationId, null);
        JuickAdvancedApplication.showXMPPToast("JuickGCMClient unregistered");
    }

    // must be called in thread
    public void stop() {
        if (!started) return;
        started = false;
        GCMIntentService.listeners.remove(this);
        if  (JuickAdvancedApplication.registrationId != null) {
            unregisterOnServer(JuickAdvancedApplication.registrationId);
        }
    }

    // must be called in thread
    public void start() {
        if (started) return;
        started = true;
        GCMIntentService.listeners.add(this);
        if  (JuickAdvancedApplication.registrationId != null) {
            registerOnServer(JuickAdvancedApplication.registrationId);
        }

    }

}
