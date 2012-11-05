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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.app.SupportActivity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.juick.android.datasource.JuickCompatibleURLMessagesSource;
import com.juick.android.datasource.MessagesSource;
import com.juickadvanced.R;
import org.json.JSONArray;

import java.io.File;

/**
 * @author Ugnich Anton
 */
public class TagsFragment extends Fragment  {

    private TagsFragmentListener parentActivity;
    private int uid = 0;

    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        try {
            parentActivity = (TagsFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement TagsFragmentListener");
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tags_fragment, null);
    }

    View myView;

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        myView = view;
        Bundle args = getArguments();
        if (args != null) {
            uid = args.getInt("uid", 0);
            if (uid == 0) {
                MessagesSource messagesSource = (MessagesSource) args.get("messagesSource");
                if (messagesSource instanceof JuickCompatibleURLMessagesSource) {
                    JuickCompatibleURLMessagesSource jcums = (JuickCompatibleURLMessagesSource) messagesSource;
                    String user_idS = jcums.getArg("user_id");
                    if (user_idS != null) {
                        try {
                            uid = Integer.parseInt(user_idS);
                        } catch (Throwable _) {
                        }
                    }
                }
            }
        }

//        getListView().setOnItemClickListener(this);
//        getListView().setOnItemLongClickListener(this);

//        MessagesFragment.installDividerColor(getListView());
        MainActivity.restyleChildrenOrWidget(view);
        final TextView progress = (TextView)myView.findViewById(R.id.progress);
        final View progressAll = myView.findViewById(R.id.progress_all);
        progress.setText(R.string.Loading___);

        Thread thr = new Thread(new Runnable() {

            public void run() {
                String url = "http://api.juick.com/tags";
                File globalTagsCache = new File(view.getContext().getCacheDir(), "global_tags-" + uid + ".json");
                String cachedString = null;
                if (uid != 0) {
                    url += "?user_id=" + uid;
                }
                if (globalTagsCache.exists() && globalTagsCache.lastModified() > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) {
                    cachedString = XMPPService.readFile(globalTagsCache);
                }
                final String jsonStr = cachedString != null ? cachedString : Utils.getJSON(getActivity(), url, null).getResult();
                if (jsonStr != null && cachedString == null) {
                    XMPPService.writeStringToFile(globalTagsCache, jsonStr);
                }
                if (isAdded()) {
                    final SpannableStringBuilder ssb = new SpannableStringBuilder();
                    if (jsonStr != null) {
                        try {
                            JSONArray json = new JSONArray(jsonStr);
                            int cnt = json.length();
                            for (int i = 0; i < cnt; i++) {
                                int index = ssb.length();
                                final String tagg = json.getJSONObject(i).getString("tag");
                                ssb.append("*" + tagg);
                                ssb.setSpan(new URLSpan(tagg) {
                                    @Override
                                    public void onClick(View widget) {
                                        parentActivity.onTagClick(tagg, uid);
                                    }
                                }, index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                ssb.append(" ");

                            }
                        } catch (Exception ex) {
                            ssb.append("Error: "+ex.toString());
                        }
                    }
                    getActivity().runOnUiThread(new Runnable() {

                        public void run() {
                            TextView tv = (TextView)myView.findViewById(R.id.tags);
                            progressAll.setVisibility(View.GONE);
                            tv.setText(ssb);
                            //tv.setMovementMethod(LinkMovementMethod.getInstance());
                        }
                    });
                }
            }
        });
        thr.start();

    }

    public interface TagsFragmentListener {

        public void onTagClick(String tag, int uid);

        public void onTagLongClick(String tag, int uid);
    }
}
