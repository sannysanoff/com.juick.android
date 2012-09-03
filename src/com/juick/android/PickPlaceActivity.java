/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import com.juickadvanced.R;
import com.juick.android.api.JuickPlace;
import de.quist.app.errorreporter.ExceptionReporter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * @author Ugnich Anton
 */
public class PickPlaceActivity extends ListActivity implements OnClickListener, OnItemClickListener, OnItemLongClickListener, OnCancelListener, LocationListener {

    private static final int ACTIVITY_SETTINGS = 1;
    private static final int ACTIVITY_NEWPLACE = 2;
    private static final int ACTIVITY_PICKLOCATION = 3;
    private static final int MENUITEM_SPECIFYLOCATION = 1;
    JuickPlacesAdapter listAdapter;
    LocationManager lm;
    ProgressDialog progressDialog;
    Button bNewPlace;
    Button bNonamePlace;
    private Location location = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        Utils.updateTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pickplace);

        bNewPlace = (Button) findViewById(R.id.buttonNewPlace);
        bNonamePlace = (Button) findViewById(R.id.buttonNonamePlace);
        bNewPlace.setOnClickListener(this);
        bNonamePlace.setOnClickListener(this);

        listAdapter = new JuickPlacesAdapter(this);
        getListView().setAdapter(listAdapter);
        getListView().setOnItemClickListener(this);
        getListView().setOnItemLongClickListener(this);

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria c = new Criteria();
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String accuracy = defaultSharedPreferences.getString("locationAccuracy", "ACCURACY_FINE");
        if (accuracy.equals("ACCURACY_COARSE")) {
            c.setAccuracy(Criteria.ACCURACY_COARSE);
        } else if (accuracy.equals("ACCURACY_LOW")) {
            c.setAccuracy(Criteria.ACCURACY_LOW);
        } else {
            c.setAccuracy(Criteria.ACCURACY_FINE);
        }
        String bestProvider = lm.getBestProvider(c, true);
        if (bestProvider == null || bestProvider.length() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setMessage(R.string.Location_determination_is_disabled);
            builder.setPositiveButton(R.string.Settings, new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface arg0, int arg1) {
                    startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS), ACTIVITY_SETTINGS);
                }
            });
            builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    PickPlaceActivity.this.setResult(RESULT_CANCELED);
                    PickPlaceActivity.this.finish();
                }
            });
            builder.show();
            return;
        }
        lm.requestLocationUpdates(bestProvider, 0, 0, this);

        progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.Determining_your_location___), true, true, this);
    }

    public void onClick(View v) {
        if (location != null) {
            if (v == bNewPlace) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setClass(this, PlaceEditActivity.class);
                i.putExtra("lat", location.getLatitude());
                i.putExtra("lon", location.getLongitude());
                startActivityForResult(i, ACTIVITY_NEWPLACE);
            } else if (v == bNonamePlace) {
                Intent i = new Intent();
                i.putExtra("lat", location.getLatitude());
                i.putExtra("lon", location.getLongitude());
                i.putExtra("pname", getResources().getString(R.string.Without_name));
                setResult(RESULT_OK, i);
                finish();
            }
        } else {
            Toast.makeText(this, "Location is still unknown.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_NEWPLACE) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK, data);
            }
            finish();
        } else if (requestCode == ACTIVITY_PICKLOCATION && resultCode == RESULT_OK) {
            progressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.Please_wait___), true, true, this);
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setLatitude(data.getDoubleExtra("lat", 0));
            loc.setLongitude(data.getDoubleExtra("lon", 0));
            loc.setAccuracy(3000);
            onLocationChanged(loc);
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final JuickPlace jplace = (JuickPlace) parent.getItemAtPosition(position);
        if (jplace.source != null && jplace.source.equals("google")) {
            EditText tv = new EditText(this);
            tv.setText(jplace.name);
            tv.setSingleLine(false);
            tv.setLines(5);
            MainActivity.restyleChildrenOrWidget(tv);
            new AlertDialog.Builder(this)
                    .setTitle("Edit location before save")
                    .setView(tv)
                    .setCancelable(true)
                    .setPositiveButton("Save and continue", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialogInterface, int i) {
                            String data = "lat=" + jplace.lat + "&lon=" + jplace.lon + "&name=" + Uri.encode(jplace.name);
                            final String dataf = data;
                            final ProgressDialog pd = new ProgressDialog(PickPlaceActivity.this);
                            pd.setIndeterminate(true);
                            pd.setCancelable(false);
                            pd.show();
                            Thread thr = new Thread(new Runnable() {

                                public void run() {
                                    final String jsonStr = Utils.postJSON(PickPlaceActivity.this, "http://api.juick.com/place_add", dataf);
                                    String error = null;
                                    try {
                                        JSONObject json = new JSONObject(jsonStr);
                                        if (json.has("pid")) {
                                            jplace.pid = json.getInt("pid");
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    dialogInterface.dismiss();
                                                    pd.dismiss();
                                                    finishWithPlace(jplace);
                                                }
                                            });
                                        } else {
                                            error = "Juick was unable to store this place: "+jsonStr;
                                        }
                                    } catch (JSONException e) {
                                        error = "Juick server could not process request: "+jsonStr;
                                    }
                                    if (error != null) {
                                        final String finalError = error;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                dialogInterface.dismiss();
                                                pd.dismiss();
                                                Toast.makeText(PickPlaceActivity.this, finalError, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                            });
                            thr.start();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    }).show();
        } else {
            finishWithPlace(jplace);
        }
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        int placeId = listAdapter.getItem(position).pid;
        if (placeId != 0) {
            Intent i = new Intent(this, MessagesActivity.class);
            i.putExtra("place_id", placeId);
            startActivity(i);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu.findItem(MENUITEM_SPECIFYLOCATION) == null) {
            menu.add(Menu.NONE, MENUITEM_SPECIFYLOCATION, Menu.NONE, R.string.Specify_a_location).setIcon(android.R.drawable.ic_menu_mylocation);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENUITEM_SPECIFYLOCATION) {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setClass(this, PickLocationActivity.class);
            if (location != null) {
                i.putExtra("lat", location.getLatitude());
                i.putExtra("lon", location.getLongitude());
            }
            startActivityForResult(i, ACTIVITY_PICKLOCATION);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void onCancel(DialogInterface arg0) {
        lm.removeUpdates(this);
        bNewPlace.setEnabled(false);
        bNonamePlace.setEnabled(false);
    }

    public void onProviderDisabled(String arg0) {
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
    }

    public void onLocationChanged(final Location loc) {
        location = loc;
        lm.removeUpdates(this);
        listAdapter.clear();
        Thread thr = new Thread(new Runnable() {

            public void run() {
                String url = "http://api.juick.com/places?lat=" + String.valueOf(location.getLatitude()) + "&lon=" + String.valueOf(location.getLongitude());
                if (location.hasAccuracy()) {
                    url += "&acc=" + String.valueOf(location.getAccuracy());
                }
                final String jsonStr = Utils.getJSON(PickPlaceActivity.this, url, null);
                PickPlaceActivity.this.runOnUiThread(new Runnable() {

                    public void run() {
                        if (jsonStr != null) {
                            listAdapter.parseJSON(jsonStr);
                            progressDialog.dismiss();
                        }
                    }
                });

            }
        });
        thr.start();
        Thread thr2 = new Thread(new Runnable() {

            public void run() {
                String url = "http://maps.googleapis.com/maps/api/geocode/json?language=ru&sensor=true&latlng=" + String.valueOf(location.getLatitude()) + "," + String.valueOf(location.getLongitude());
                final String jsonStr = Utils.getJSON(PickPlaceActivity.this, url, null);
                PickPlaceActivity.this.runOnUiThread(new Runnable() {

                    public void run() {
                        try {
                            boolean added = false;
                            if (jsonStr != null) {
                                try {
                                    JSONObject jsonObject = new JSONObject(jsonStr);
                                    JSONArray results = (JSONArray) jsonObject.get("results");
                                    for (int i = 0; i < results.length(); i++) {
                                        JSONObject res = (JSONObject) results.get(i);
                                        String addr = (String) res.get("formatted_address");
                                        JuickPlace jplace = new JuickPlace();
                                        jplace.name = addr;
                                        jplace.source = "google";
                                        jplace.lat = loc.getLatitude();
                                        jplace.lon = loc.getLongitude();
                                        listAdapter.add(jplace);
                                        added = true;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                }
                            }
                            if (added)
                                listAdapter.notifyDataSetChanged();
                        } finally {
                            progressDialog.dismiss();
                        }
                    }
                });

            }
        });
        thr2.start();
    }
    private void finishWithPlace(JuickPlace jplace) {
        Intent i = new Intent();
        if (location != null) {
            i.putExtra("mylat", location.getLatitude());
            i.putExtra("mylon", location.getLongitude());
            if (location.hasAccuracy()) {
                i.putExtra("myacc", String.valueOf(location.getAccuracy()));
            }
        }
        i.putExtra("pid", jplace.pid);
        i.putExtra("lat", jplace.lat);
        i.putExtra("lon", jplace.lon);
        i.putExtra("pname", jplace.name);
        setResult(RESULT_OK, i);
        finish();
    }}

class JuickPlacesAdapter extends ArrayAdapter<JuickPlace> {

    private static final int textViewResourceId = android.R.layout.simple_list_item_1;

    public JuickPlacesAdapter(Context context) {
        super(context, textViewResourceId);
    }

    public boolean parseJSON(String jsonStr) {
        try {
            JSONArray json = new JSONArray(jsonStr);
            int cnt = json.length();
            for (int i = 0; i < cnt; i++) {
                add(JuickPlace.parseJSON(json.getJSONObject(i)));
            }
            return true;
        } catch (Exception e) {
            Log.e("initPlacesAdapter", e.toString());
        }
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView t;
        if (convertView != null && convertView instanceof TextView) {
            t = (TextView) convertView;
        } else {
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            t = (TextView) vi.inflate(textViewResourceId, null);
        }
        JuickPlace item = getItem(position);
        String label = item.name;
        if (item.source != null && item.source.equals("google")) {
            label = "[auto] " + label;
        }
        t.setText(label);
        return t;
    }
}

