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

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.*;
import android.view.MenuItem;
import android.widget.TextView;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.*;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;

/**
 *
 * @author Ugnich Anton
 */
public class MessagesActivity extends JuickFragmentActivity {

    Object restoreData;
    MessagesSource messagesSource;

    public boolean shouldDelayLaunch() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        JuickAdvancedApplication.setupTheme(this);

        JuickAdvancedApplication.maybeEnableAcceleration(this);

        super.onCreate(savedInstanceState);
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) supportActionBar.hide();
        Intent i = getIntent();
        restoreData = getLastCustomNonConfigurationInstance();
        if (!shouldDelayLaunch() || restoreData != null) {
            if (messagesSource == null) {
                messagesSource = (MessagesSource)i.getSerializableExtra("messagesSource");
                if (messagesSource == null) {
                    messagesSource = new JuickCompatibleURLMessagesSource(this, "new");
                }
                messagesSource.setContext(this);
            }
            initWithMessagesSource();
        }
    }


    protected void initWithMessagesSource() {
        setContentView(R.layout.messages);
        TextView oldTitle = (TextView)findViewById(R.id.old_title);
        oldTitle.setText(messagesSource.getTitle());
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        mf = new MessagesFragment(restoreData, this);
        Bundle args = new Bundle();
        args.putSerializable("messagesSource", messagesSource);
        mf.setArguments(args);
        ft.replace(R.id.messagesfragment, mf);
        ft.commit();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (mf != null) {
            return mf.saveState();
        }
        return super.onRetainCustomNonConfigurationInstance();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        com.actionbarsherlock.view.MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.messages, menu);
        return true;
    }

    public int getUserId() {
        if (messagesSource instanceof JuickCompatibleURLMessagesSource) {
            String user_idS = ((JuickCompatibleURLMessagesSource) messagesSource).getArg("user_id");
            if (user_idS != null) {
                try {
                    return Integer.parseInt(user_idS);
                } catch (NumberFormatException e) {
                }
            }
        }
        return 0;
    }

    public String getUname() {
        if (messagesSource instanceof JuickCompatibleURLMessagesSource) {
            return ((JuickCompatibleURLMessagesSource) messagesSource).getArg("uname");
        }
        return null;
    }

    @Override
    public boolean onPrepareOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        menu.findItem(R.id.menuitem_search).setVisible(getUserId() > 0 || getUname() != null);
        return super.onPrepareOptionsMenu(menu);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void requestWindowFeature(long featureId) {
        // actionbar sherlock deducing flag from theme id.
        if (featureId == com.actionbarsherlock.view.Window.FEATURE_ACTION_BAR) return;
        super.requestWindowFeature(featureId);
    }

    @Override
    public void onBackPressed() {
        if (mf != null && mf.listAdapter.imagePreviewHelper != null && mf.listAdapter.imagePreviewHelper.handleBack())
            return;
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuitem_search:
                Intent intent = new Intent(this, ExploreActivity.class);
                intent.putExtra("messagesSource", messagesSource);
                intent.putExtra("uid", getUserId());
                intent.putExtra("uname", getUname());
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);    //To change body of overridden methods use File | Settings | File Templates.
    }

}
