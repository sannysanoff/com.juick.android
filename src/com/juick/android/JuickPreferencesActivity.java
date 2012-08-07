package com.juick.android;

import android.content.Intent;
import android.view.*;
import com.juick.R;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuickPreferencesActivity extends PreferencesActivity {


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menuitem_filters:
                startActivity(new Intent(this, EditFiltersActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
