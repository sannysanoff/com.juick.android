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
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juickadvanced.R;

/**
 *
 * @author Ugnich Anton
 */
public class TagsActivity extends FragmentActivity implements TagsFragment.TagsFragmentListener {

    private int uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.updateTheme(this);
        super.onCreate(savedInstanceState);

        uid = getIntent().getIntExtra("uid", 0);
        boolean multi = getIntent().getBooleanExtra("multi", false);

        if (uid == 0) {
            setTitle(R.string.Popular_tags);
        }

        setContentView(R.layout.tags);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        TagsFragment tf = new TagsFragment();
        Bundle args = new Bundle();
        args.putInt("uid", uid);
        args.putBoolean("multi", multi);
        tf.setArguments(args);
        ft.add(R.id.tagsfragment, tf);
        ft.commit();
        MainActivity.restyleChildrenOrWidget(getWindow().getDecorView());
    }

    public void onTagClick(String tag, int uid) {
        Intent i = new Intent();
        i.putExtra("tag", tag);
        setResult(RESULT_OK, i);
        finish();
    }

    public void onTagLongClick(String tag, int uid) {
        Intent i = new Intent(this, MessagesActivity.class);
        JuickCompatibleURLMessagesSource ms = new JuickCompatibleURLMessagesSource(getString(R.string.Tag) + ": " + tag, this).putArg("tag", Uri.encode(tag)).putArg("user_id", "" + uid);
        ms.setCanNext(false);
        ms.setKind("user_tag");
        i.putExtra("messagesSource", ms);
        startActivity(i);
    }
}
