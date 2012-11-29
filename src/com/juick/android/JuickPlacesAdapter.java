package com.juick.android;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.juickadvanced.data.juick.JuickPlace;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/29/12
 * Time: 2:43 PM
 * To change this template use File | Settings | File Templates.
 */
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
                add(parseJuickPlaceJSON(json.getJSONObject(i)));
            }
            return true;
        } catch (Exception e) {
            Log.e("initPlacesAdapter", e.toString());
        }
        return false;
    }

    public static JuickPlace parseJuickPlaceJSON(JSONObject json) throws JSONException {
        JuickPlace jplace = new JuickPlace();

        jplace.pid = json.getInt("pid");
        jplace.lat = json.getDouble("lat");
        jplace.lon = json.getDouble("lon");
        jplace.name = json.getString("name").replace("&quot;", "\"");
        if (json.has("description")) {
            jplace.description = json.getString("description").replace("&quot;", "\"");
        }
        if (json.has("users")) {
            jplace.users = json.getInt("users");
        }
        if (json.has("messages")) {
            jplace.messages = json.getInt("messages");
        }
        if (json.has("distance")) {
            jplace.distance = json.getInt("distance");
        }
        if (json.has("tags")) {
            JSONArray tags = json.getJSONArray("tags");
            for (int n = 0; n < tags.length(); n++) {
                jplace.tags.add(tags.getString(n).replace("&quot;", "\""));
            }
        }

        return jplace;
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
