package com.juick.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import com.juickadvanced.R;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 10/22/12
 * Time: 10:18 AM
 * To change this template use File | Settings | File Templates.
 */
public class DisplayChartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri data = getIntent().getData();
        setContentView(R.layout.displaty_chart);
        final WebView wv = (WebView)findViewById(R.id.webview);
        final TextView status = (TextView)findViewById(R.id.status);
        final TextView progress = (TextView)findViewById(R.id.progress);
        final View stop = findViewById(R.id.stop);
        final View loading = findViewById(R.id.loading);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wv.stopLoading();
                finish();
            }
        });
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                String s = url.toString();
                if (!s.startsWith("data:")) {
                    status.setText("Loading: " + s);
                }
                super.onLoadResource(view, url);    //To change body of overridden methods use File | Settings | File Templates.
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                status.setText("Done loading.");
                loading.setVisibility(View.GONE);
                super.onPageFinished(view, url);
            }


        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setText("progress: " + newProgress + " %");
                super.onProgressChanged(view, newProgress);
            }

        });
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadUrl(data.toString());
    }

    @Override
    public void finish() {
        final WebView wv = (WebView)findViewById(R.id.webview);
        wv.stopLoading();
        super.finish();
    }
}
