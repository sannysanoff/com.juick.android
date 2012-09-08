package com.juick.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuickPreferencesActivity extends PreferencesActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menuitem_filters:
                startActivity(new Intent(this, EditFiltersActivity.class));
                break;
            case R.id.menuitem_xmpp_control:
                startActivity(new Intent(this, XMPPControlActivity.class));
                break;
            case R.id.menuitem_whatsnew:
                final WhatsNew whatsNew = new WhatsNew(this);
                ListView lv = new ListView(this);
                final ArrayList<String> lst = new ArrayList<String>();
                for (WhatsNew.ReleaseFeatures feature : whatsNew.features) {
                    lst.add(feature.sinceRelease);
                }
                lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, lst));
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle("Choose release")
                        .setView(lv)
                        .setCancelable(true)
                        .setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                final AlertDialog alertDialog = builder.create();
                alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        MainActivity.restyleChildrenOrWidget(alertDialog.getWindow().getDecorView());
                    }
                });
                alertDialog.show();
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        alertDialog.dismiss();
                        whatsNew.reportFeatures(position, false, null);
                    }
                });

                break;
        }
        return super.onOptionsItemSelected(item);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
