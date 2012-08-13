package com.juick.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.juick.R;
import de.quist.app.errorreporter.ExceptionReporter;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/11/12
 * Time: 1:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMPPControlActivity extends Activity {

    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xmpp_control);
        final TextView xmppStatus = (TextView) findViewById(R.id.xmpp_status);
        final TextView lastException = (TextView) findViewById(R.id.last_exception);
        final TextView messagesReceived = (TextView) findViewById(R.id.messages_received);
        final TextView juickbot = (TextView) findViewById(R.id.xmpp_juickbot);
        final Utils.ServiceGetter<XMPPService> xmppServiceServiceGetter = new Utils.ServiceGetter<XMPPService>(this, XMPPService.class);
        final Button retry = (Button) findViewById(R.id.retry);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        service.cleanup("Manual reconnect");
                        service.startup();
                    }
                });
            }
        });
        handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Runnable thiz = this;
                xmppServiceServiceGetter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                    @Override
                    public void withService(XMPPService service) {
                        xmppStatus.setText(service.connection != null ? (service.connection.isConnected() ? "CONNECTED" : "connecting") : "idle");
                        lastException.setText(service.lastException != null ? service.lastException.toString() : " --- ");
                        messagesReceived.setText(""+service.messagesReceived);
                        juickbot.setText(service.botOnline ? "ONLINE" : "offline");
                        handler.postDelayed(thiz, 200);
                    }
                });


            }
        }, 20);
    }
}
