package com.juick.android;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.juickadvanced.R;
import org.apache.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/22/12
 * Time: 10:18 AM
 * To change this template use File | Settings | File Templates.
 */
public class DisplayChartActivity2 extends Activity implements Utils.Notification, Utils.DownloadProgressNotification {
    @Override

    public void notifyDownloadProgress(final int progressBytes) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                TextView progress = (TextView)findViewById(R.id.progress);
                progress.setText(getString(R.string.Loading___)+" "+progressBytes/1024+" KB");
            }
        });
    }

    @Override
    public void notifyHttpClientObtained(HttpClient client) {

    }

    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        final Uri data = getIntent().getData();
        setContentView(R.layout.displaty_chart2);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                View mainView = findViewById(R.id.display_chart);
                final int width = mainView.getWidth();
                final int height = mainView.getHeight();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            String url = data.toString();
                            if (url.contains("?")) {
                                url += '&';
                            } else {
                                url += '?';
                            }
                            url += "w=" + width + "&h=" + height;
                            Utils.BINResponse json = Utils.getBinary(DisplayChartActivity2.this, url, DisplayChartActivity2.this, 0);
                            if (json.errorText != null)
                                throw new IOException(json.errorText);
                            final File file = new File(getCacheDir(), "chart.png");
                            file.delete();
                            FileOutputStream fos = new FileOutputStream(file);
                            fos.write(json.getResult());
                            fos.close();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    View loading = findViewById(R.id.loading);
                                    loading.setVisibility(View.GONE);
                                    ImageView viewById = (ImageView) findViewById(R.id.imgview);
                                    try {
                                        viewById.setImageURI(Uri.fromFile(file));
                                    } catch (Exception e) {
                                        Toast.makeText(DisplayChartActivity2.this, e.toString(), Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        } catch (final IOException e) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(DisplayChartActivity2.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    ;
                                }
                            });
                        }
                    }
                }.start();
            }
        }, 200);
    }

    @Override
    public void finish() {
        super.finish();
    }
}
