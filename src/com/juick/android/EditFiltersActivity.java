package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class EditFiltersActivity extends Activity {

    ArrayList<String> filteredOutUzers;

    class ListAdapter extends BaseAdapter {
        ListAdapter() {
            filteredOutUzers = new ArrayList(JuickMessagesAdapter.getFilteredOutUsers(EditFiltersActivity.this));
            Collections.sort(filteredOutUzers);
        }

        @Override
        public int getCount() {
            return filteredOutUzers.size();
        }

        @Override
        public Object getItem(int i) {
            return i;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.listitem_filteredout, null);
            }
            TextView tv = (TextView)view.findViewById(R.id.text);
            tv.setText(filteredOutUzers.get(i));
            return view;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        super.onCreate(savedInstanceState);    //To change body of overridden methods use File | Settings | File Templates.
        setContentView(R.layout.edit_filters);
        final ListView lv = (ListView) findViewById(R.id.list);
        lv.setAdapter(new ListAdapter());
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                AlertDialog.Builder builder = new AlertDialog.Builder(EditFiltersActivity.this);
                CharSequence[] items = new CharSequence[1];
                final String uzerName = filteredOutUzers.get(i);
                items[0] = "Un-filter user @"+ uzerName;
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Set<String> uzers = JuickMessagesAdapter.getFilteredOutUsers(EditFiltersActivity.this);
                        uzers.remove(uzerName);
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(EditFiltersActivity.this);
                        sp.edit().putString("filteredOutUsers", Utils.set2string(uzers)).commit();
                        Parcelable parcelable = lv.onSaveInstanceState();
                        lv.setAdapter(new ListAdapter());
                        lv.onRestoreInstanceState(parcelable);
                    }
                });
                builder.create().show();
                return false;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
//        lv.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
//            @Override
//            public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
//                contextMenu.add(0, 0, 0, );
//            }
//        });
    }

}
