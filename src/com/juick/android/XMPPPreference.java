package com.juick.android;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import com.google.gson.Gson;
import com.juickadvanced.R;
import com.juickadvanced.xmpp.XMPPConnectionSetup;

/**
 */
public class XMPPPreference extends Preference {

    String currentValueJSON = "";

    public XMPPPreference(Context context) {
        super(context);
    }

    public XMPPPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public XMPPPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setWidgetLayoutResource(R.layout.xmpp_preference);
    }


    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Button btn = (Button) view.findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), XMPPPreferenceActivity.class);
                getContext().startActivity(intent);
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            Gson gson = new Gson();
            XMPPConnectionSetup value = gson.fromJson((String) currentValueJSON, XMPPConnectionSetup.class);

            @Override
            public void onClick(View v) {
                LayoutInflater mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View view = mInflater.inflate(R.layout.xmpp_setup, null);
            }
        });
    }


    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        Gson gson = new Gson();
        if (restorePersistedValue) {
            currentValueJSON = getPersistedString(currentValueJSON);
        } else {
            XMPPConnectionSetup value = gson.fromJson((String) defaultValue, XMPPConnectionSetup.class);
            currentValueJSON = gson.toJson(value);
            persistString(currentValueJSON);
        }
        XMPPConnectionSetup value = gson.fromJson(currentValueJSON, XMPPConnectionSetup.class);
        if (value == null || value.login == null || value.login.length() == 0 || value.password == null || value.password.length() == 0 || value.server == null || value.server.length() == 0) {
            setSummary("Not set up");
        } else {
            setSummary(value.login +", "+value.server);
        }
    }


}
