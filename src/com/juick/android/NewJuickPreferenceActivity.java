package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.juickadvanced.R;
import de.measite.smack.AndroidDebugger;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/22/12
 * Time: 10:18 AM
 * To change this template use File | Settings | File Templates.
 */
public class NewJuickPreferenceActivity extends Activity {

    public static class MenuItem {
        int labelId;
        int label2Id;
        Runnable action;

        MenuItem(int labelId, int label2Id, Runnable action) {
            this.labelId = labelId;
            this.label2Id = label2Id;
            this.action = action;
        }
    }

    public enum Menu {
        TOP_LEVEL,
        REPORTS_CHARTS
    } ;

    HashMap<String,ArrayList<MenuItem>> allMenus = new HashMap<String, ArrayList<MenuItem>>();
    {
        ArrayList<MenuItem> menu = new ArrayList<MenuItem>();
        menu.add(new MenuItem(R.string.UI_Settings, R.string.UsabilityEnhancements, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_ui);
            }
        }));
        menu.add(new MenuItem(R.string.Behavior, R.string.Behavior2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_behavior);

            }
        }));
        menu.add(new MenuItem(R.string.ContentSettings, R.string.ContentSettings2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_content);

            }
        }));
        menu.add(new MenuItem(R.string.Sources, R.string.Sources2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_sources);

            }
        }));
        menu.add(new MenuItem(R.string.NetworkingAndStorage, R.string.NetworkingAndStorage2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_netdb);
            }
        }));
        menu.add(new MenuItem(R.string.XMPP_client, R.string.XMPP_client2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_xmpp);
            }
        }));
        menu.add(new MenuItem(R.string.WhatsNew, R.string.WhatsNew2, new Runnable() {
            @Override
            public void run() {
                NewJuickPreferenceActivity context = NewJuickPreferenceActivity.this;
                final WhatsNew whatsNew = new WhatsNew(context);
                ListView lv = new ListView(context);
                final ArrayList<String> lst = new ArrayList<String>();
                for (WhatsNew.ReleaseFeatures feature : whatsNew.features) {
                    lst.add(feature.sinceRelease);
                }
                lv.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, lst));
                AlertDialog.Builder builder = new AlertDialog.Builder(context)
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
            }
        }));
        menu.add(new MenuItem(R.string.Privacy_Policy, R.string.Privacy_Policy2, new Runnable() {
            @Override
            public void run() {
                new WhatsNew(NewJuickPreferenceActivity.this).showPrivacyPolicy();
            }
        }));
        menu.add(new MenuItem(R.string.VariousInfo, R.string.VariousInfo2, new Runnable() {
            @Override
            public void run() {
                Intent prefsIntent = new Intent(NewJuickPreferenceActivity.this, NewJuickPreferenceActivity.class);
                prefsIntent.putExtra("menu", NewJuickPreferenceActivity.Menu.REPORTS_CHARTS.name());
                startActivity(prefsIntent);
            }
        }));
        allMenus.put(Menu.TOP_LEVEL.name(), menu);
        menu = new ArrayList<MenuItem>();
        allMenus.put(Menu.REPORTS_CHARTS.name(), menu);
        menu.add(new MenuItem(R.string.UsageReportsPerWeek, R.string.UsageReportsPerWeek2, new Runnable() {
            @Override
            public void run() {
                showChart("USAGE_RPW2");
            }
        }));
        menu.add(new MenuItem(R.string.JuickUsersPerMonth, R.string.JuickUsersPerMonth2, new Runnable() {
            @Override
            public void run() {
                showChart("JUICK_UPM2");
            }
        }));

        /**
         * reports:
         *
         * user activity
         *
         *
         */
    }

    private void showChart(String chart) {
        Intent intent = new Intent(this, DisplayChartActivity2.class);
        intent.setData(Uri.parse("http://192.168.1.77:8080/charts/JuickCharts2/"+chart+".jsp"));
        //intent.setData(Uri.parse("http://ja.ip.rt.ru:8080/charts/JuickCharts2/"+chart+".jsp"));
        startActivity(intent);
    }

    private void runPrefs(int prefsid) {
        Intent prefsIntent = new Intent(this, JuickPreferencesActivity.class);
        prefsIntent.putExtra("prefs", prefsid);
        startActivity(prefsIntent);
    }

    ArrayList<MenuItem> items = new ArrayList<MenuItem>();

    class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater layoutInflater = getLayoutInflater();
            View listItem = layoutInflater.inflate(android.R.layout.simple_list_item_2, null);
            TextView text = (TextView)listItem.findViewById(android.R.id.text1);
            text.setText(items.get(position).labelId);
            TextView text2 = (TextView)listItem.findViewById(android.R.id.text2);
            text2.setText(items.get(position).label2Id);
            return listItem;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.R.layout.list_content);
        ListView list = (ListView)findViewById(android.R.id.list);
        list.setAdapter(new Adapter());
        String menu = getIntent().getStringExtra("menu");
        if (menu != null) {
            items = allMenus.get(menu);
        }
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    items.get(position).action.run();
                } catch (Exception e) {
                    Toast.makeText(NewJuickPreferenceActivity.this, e.toString(), Toast.LENGTH_SHORT);
                }
            }
        });
    }
}
