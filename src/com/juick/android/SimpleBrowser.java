package com.juick.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
import com.juickadvanced.R;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/14/13
 * Time: 11:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class SimpleBrowser extends Activity {
    private WebView webView;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        handler = new Handler();
        super.onCreate(savedInstanceState);    //To change body of overridden methods use File | Settings | File Templates.
        setContentView(R.layout.simple_browser);
        webView = (WebView) findViewById(R.id.webview);
        final Intent intent = getIntent();
        if (intent != null) {
            Utils.setupWebView(webView, "Loading..");
            new Thread() {
                @Override
                public void run() {
                    Uri uri = intent.getData();
                    String url = "http://boilerpipe-web.appspot.com/extract?url="+Uri.encode(uri.toString())+"&extractor=ArticleExtractor&output=json&extractImages=";
                    final Utils.RESTResponse json = Utils.getJSON(SimpleBrowser.this, url, null, 60000);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (json.getErrorText() != null) {
                                Utils.setupWebView(webView, json.getErrorText());
                            } else {
                                try {
                                    final JSONObject jo = new JSONObject(json.getResult());
                                    final String status = jo.getString("status");
                                    if (!status.equals("success"))
                                        throw new RuntimeException("Status="+status);
                                    final JSONObject response = jo.getJSONObject("response");
                                    String title = null;
                                    if (response.has("title"))
                                        title = response.getString("title");
                                    String body = response.getString("content");
                                    final String[] strings = body.split("\n");
                                    StringBuilder sb = new StringBuilder();
                                    if (title != null) {
                                        sb.append("<b>");
                                        sb.append(toHTML(title));
                                        sb.append("</b>");
                                    }
                                    for (String string : strings) {
                                        sb.append("<p>");
                                        sb.append(toHTML(string));
                                        sb.append("</p>");
                                    }
                                    Utils.setupWebView(webView, sb.toString());
                                } catch (Exception e) {
                                    Utils.setupWebView(webView, "Error parsing JSON result from proxy: " + e.toString());
                                }
                            }
                        }
                    });
                }
            }.start();
        } else {
            Utils.setupWebView(webView, "Must be started with argument for URL viewing.");
        }
    }

    String toHTML(String str) {
        str = str.replace("&","&amp;");
        str = str.replace("<","&lt;");
        str = str.replace(">","&gt;");
        return str;
    }
}
