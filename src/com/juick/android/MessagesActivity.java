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
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import com.juick.android.datasource.JuickCompatibleURLMessagesSource;
import com.juick.android.datasource.MessagesSource;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;

/**
 *
 * @author Ugnich Anton
 */
public class MessagesActivity extends FragmentActivity {

    MessagesFragment mf;
    Object restoreData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        Utils.updateTheme(this);
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        MessagesSource messagesSource = (MessagesSource)i.getSerializableExtra("messagesSource");
        if (messagesSource == null) {
            messagesSource = new JuickCompatibleURLMessagesSource(this);
        }
        setTitle(messagesSource.getTitle());

//        if (uid > 0 && uname != null) {
//            setTitle("@" + uname);
//        } else if (search != null) {
//            setTitle(getResources().getString(R.string.Search) + ": " + search);
//        } else if (tag != null) {
//            String title = getResources().getString(R.string.Tag) + ": " + tag;
//            if (uid == -1) {
//                title += " (" + getResources().getString(R.string.Your_messages) + ")";
//            }
//            setTitle(title);
//        } else if (place_id > 0) {
//            setTitle("Location");
//        } else if (myBlog) {
//            setTitle(getResources().getString(R.string.My_blog));
//        } else {
//            setTitle(getResources().getString(R.string.All_messages));
//        }

        setContentView(R.layout.messages);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        restoreData = getLastCustomNonConfigurationInstance();
        mf = new MessagesFragment(restoreData);
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
}
