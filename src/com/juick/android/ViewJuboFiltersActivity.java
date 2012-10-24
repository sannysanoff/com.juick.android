package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class ViewJuboFiltersActivity extends Activity {

    ArrayList<String> listContent = new ArrayList<String>();

    class ListAdapter extends BaseAdapter {
        ListAdapter() {
            XMPPService.JuboMessageFilter anyJuboMessageFilter = XMPPService.getAnyJuboMessageFilter();
            XMPPService.JuickBlacklist anyJuickBlacklist = XMPPService.getAnyJuickBlacklist();
            for (String stopTag : anyJuboMessageFilter.stopTags) {
                listContent.add("*"+stopTag + " -- JuBo stop tag");
            }
            for (String stopTag : anyJuboMessageFilter.stopWords) {
                listContent.add(stopTag + " -- JuBo stop word");
            }
            for (String stopTag : anyJuickBlacklist.stopTags) {
                listContent.add("*" + stopTag + " -- Juick stop tag");
            }
            for (String stopTag : anyJuickBlacklist.stopUsers) {
                listContent.add("@" + stopTag + " -- Juick user blacklist");
            }
            if (listContent.size() == 0) {
                listContent.add(getString(R.string.StopListsEmpty));
            }
            Collections.sort(listContent);
        }

        @Override
        public int getCount() {
            return listContent.size();
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
            String txt = listContent.get(i);
            tv.setPadding(10, 0, 0, 0);
            int ix = txt.indexOf(" -- ");
            if (ix != -1) {
                SpannableStringBuilder  ssb = new SpannableStringBuilder();
                ssb.append(txt);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, ix, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(0xFF808080), ix, txt.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(ssb);
            } else {
                tv.setText(txt);
            }
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
                AlertDialog.Builder builder = new AlertDialog.Builder(ViewJuboFiltersActivity.this);
                builder.setTitle(getString(R.string.EditingJuboFilters));
                builder.setMessage(getString(R.string.YouCannotChangeJuboHere));
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
