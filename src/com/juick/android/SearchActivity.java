package com.juick.android;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/24/12
 * Time: 4:57 AM
 * To change this template use File | Settings | File Templates.
 */
public class SearchActivity extends MessagesActivity {

    @Override
    public boolean shouldDelayLaunch() {
        return true;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    public void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    public void onListItemClick(ListView l,
                                View v, int position, long id) {
        // call detail activity for clicked entry
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        }
    }

    ArrayList<String> results = new ArrayList<String>();

    class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Object getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View item, ViewGroup parent) {
            if (item == null)
                item = getLayoutInflater().inflate(R.layout.listitem_juickmessage, null);
            TextView text = (TextView) item.findViewById(R.id.text);
            text.setText(results.get(position));
            return item;
        }
    }

    private void doSearch(String query) {
        messagesSource = new JuickCompatibleURLMessagesSource(getString(R.string.Search) + ": " + query, "search", this).putArg("search", Uri.encode(query));
        ((JuickCompatibleURLMessagesSource)messagesSource).setCanNext(false);
        initWithMessagesSource();
    }

    @Override
    public boolean onSearchRequested() {
        return super.onSearchRequested();
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData, boolean globalSearch) {
        super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);    //To change body of overridden methods use File | Settings | File Templates.
    }
}