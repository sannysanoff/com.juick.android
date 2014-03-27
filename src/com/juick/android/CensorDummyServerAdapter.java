package com.juick.android;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.widget.Toast;
import com.juick.android.juick.JuickAPIAuthorizer;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: coderoo
 * Date: 28/10/13
 * Time: 8:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class CensorDummyServerAdapter implements Censor.CensorServerAdapter {

    private Context context;

    public CensorDummyServerAdapter(Context context) {
        //To change body of created methods use File | Settings | File Templates.
        this.context = context;
    }

    @Override
    public void submitForReview(final int censorCategoryId, final String token, final Utils.Function<Void, Utils.RESTResponse> continuation) {
        new Thread() {
            @Override
            public void run() {
                final String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(context);
                final String juickPassword = JuickAPIAuthorizer.getPassword(context);
                if (juickAccountName == null || juickPassword == null) {
                    Toast.makeText(context, "Not authorized", Toast.LENGTH_LONG).show();
                    return;
                }
                Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        super.withService(service);    //To change body of overridden methods use File | Settings | File Templates.
                        service.addLocalCensorWord(censorCategoryId, token);
                        continuation.apply(new Utils.RESTResponse(null, false, "OK"));
                    }
                });
            }
        }.start();

    }
}
