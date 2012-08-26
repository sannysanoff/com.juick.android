package com.juick.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import com.juickadvanced.R;
import de.quist.app.errorreporter.ExceptionReporter;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/7/12
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuickPreferencesActivity extends PreferencesActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ExceptionReporter.register(this);
        super.onCreate(savedInstanceState);
    }

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
            case R.id.colors:
                startActivity(new Intent(this, ColorsTheme.class));
                break;
            case R.id.menuitem_xmpp_control:
                startActivity(new Intent(this, XMPPControlActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
