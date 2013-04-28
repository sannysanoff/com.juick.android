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

    public class ChartMenuItem extends MenuItem {
        private final String chartName;

        public ChartMenuItem(int labelId, int label2Id, final String chartName) {
            super(labelId, label2Id, new Runnable() {
                @Override
                public void run() {
                    showChart(chartName);
                }
            });
            this.chartName = chartName;
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
        menu.add(new MenuItem(R.string.ContentSettings, R.string.ContentSettings2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_content);

            }
        }));
        menu.add(new MenuItem(R.string.Notifications, R.string.Notifications2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_xmpp);
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
        menu.add(new MenuItem(R.string.Other, R.string.Other2, new Runnable() {
            @Override
            public void run() {
                runPrefs(R.xml.prefs_other);
            }
        }));
        allMenus.put(Menu.TOP_LEVEL.name(), menu);
        menu = new ArrayList<MenuItem>();
        allMenus.put(Menu.REPORTS_CHARTS.name(), menu);
        initReportsMenu(menu);

    }

    private void initReportsMenu(ArrayList<MenuItem> menu) {
        menu.add(new ChartMenuItem(R.string.UsageReportsPerWeek, R.string.UsageReportsPerWeek2, "USAGE_RPW2"));
        menu.add(new ChartMenuItem(R.string.JuickUsersPerMonth, R.string.JuickUsersPerMonth2, "JUICK_UPM2"));
        menu.add(new ChartMenuItem(R.string.JuickTrafficAllTime, R.string.JuickTrafficAllTime2, "JUICK_TPM"));
        menu.add(new ChartMenuItem(R.string.JuickTrafficRecently, R.string.JuickTrafficRecently2, "JUICK_TPW"));
        menu.add(new ChartMenuItem(R.string.JuickWeekActivity, R.string.JuickWeekActivity2, "JUICK_WEEK_ACTIVITY"));
        menu.add(new ChartMenuItem(R.string.JuickPostLength, R.string.JuickPostLength2, "JUICK_POSTLEN"));
        menu.add(new ChartMenuItem(R.string.JuickHoles, R.string.JuickHoles2, "JA_HOLES"));
        menu.add(new ChartMenuItem(R.string.FeatureUsage, R.string.FeatureUsage2, "FEATURE_USAGE"));
        menu.add(new ChartMenuItem(R.string.ThemeUsage, R.string.ThemeUsage2, "THEME_USAGE"));
        menu.add(new ChartMenuItem(R.string.VersionInstalls, R.string.VersionInstalls2, "VERSION_INSTALLS"));
        menu.add(new ChartMenuItem(R.string.ClassicUsers, R.string.ClassicUsers2, "CLASSIC_USERS"));
    }

    private void showChart(String chart) {
        showChart(this, chart, "");
    }

    public static void showChart(Activity context, String chart, String args) {
        Intent intent = new Intent(context, DisplayChartActivity2.class);
        //String host = "192.168.1.77:8080";
        String host = "ja.ip.rt.ru:8080";
        String url = "http://" + host + "/charts/JuickCharts2/" + chart + ".jsp";
        if (args != null && args.length() > 0) {
            url += "?"+args;
        }
        intent.setData(Uri.parse(url));
        context.startActivity(intent);
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
            try {
                text2.setText(items.get(position).label2Id);
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            return listItem;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.R.layout.list_content);
        ListView list = (ListView)findViewById(android.R.id.list);
        if (list != null) {
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
        } else {
            Toast.makeText(getApplication(), "Sorry, your device does not provide needed built-in layout", Toast.LENGTH_LONG).show();
        }
    }

}
