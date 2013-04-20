package com.juick.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import com.juickadvanced.R;
import org.apache.http.client.HttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/14/13
 * Time: 11:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class SimpleBrowser extends Activity implements Utils.DownloadProgressNotification, Utils.DownloadErrorNotification {
    private WebView webView;
    private Handler handler;


    enum Mode {
        JA_ANY,
        GWT_NOIMG,
        GWT,
        BOILERPIPE,
        JA_POCKET,
        JA_BOILERPIPE,
    }

    Mode mode = Mode.JA_ANY;
    String currentURL, currentBody;
    ArrayList<String> history = new ArrayList<String>();
    ArrayList<String> historyCache = new ArrayList<String>();
    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        handler = new Handler();
        super.onCreate(savedInstanceState);    //To change body of overridden methods use File | Settings | File Templates.
        setContentView(R.layout.simple_browser);
        final SharedPreferences sp = getPreferences(MODE_PRIVATE);
        mode = Mode.values()[sp.getInt("mode", 0)];
        final Intent intent = getIntent();
        Uri uri = intent.getData();
        currentURL = uri.toString();
        webView = (WebView) findViewById(R.id.webview);
        button = (Button) findViewById(R.id.button);
        button.setEnabled(false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (history.size() > 0) {
                    currentURL = history.remove(history.size()-1);
                    currentBody = historyCache.remove(historyCache.size()-1);
                    Utils.setupWebView(webView, currentBody);
                }
                button.setEnabled(history.size() > 0);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String oldURL = currentURL;
                if (url.startsWith("file://")) {
                    String subpath = url.substring(7);
                    switch(mode) {
                        case GWT:
                        case GWT_NOIMG:
                            // file:///gwt/x?u=http://habrahabr.ru/company/e-Legion/blog/177187/%23comment_6153575&ei=3WZwUaO2FY6b_AbPgoHQAw&wsc=fa&ct=np&whp=374
                            if (url.indexOf("&ct=") < 0) {
                                URLParser parser = new URLParser(url);
                                currentURL = Uri.decode(parser.getArgsMap().get("u"));
                            } else {
                                currentURL = "!http://www.google.com" + url.substring(7);
                            }
                            break;
                        default:
                            return true;
                    }
                } else {
                    currentURL = url;
                }
                if (currentURL != null) {
                    if (!currentURL.equals(oldURL)) {
                        history.add(oldURL);
                        historyCache.add(currentBody);
                        button.setEnabled(true);
                    }
                    reload();
                }
                return true;
            }
        });
        Button browser = (Button) findViewById(R.id.browser);
        browser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentURL)));
            }
        });
        Spinner modeWidget = (Spinner) findViewById(R.id.mode);
        modeWidget.setSelection(mode.ordinal());
        modeWidget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mode.ordinal() != position) {
                    sp.edit().putInt("mode", position).commit();
                    mode = Mode.values()[position];
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

        });
        reload();
    }

    private void reload() {
        if (currentURL != null) {
            Utils.setupWebView(webView, "Loading..");
            new Thread() {
                @Override
                public void run() {
                    final String loadingURL = currentURL;
                    currentBody = null;
                    final Utils.RESTResponse response = processURL(currentURL, SimpleBrowser.this);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (currentURL.equals(loadingURL)) {
                                if (response.getErrorText() != null) {
                                    Utils.setupWebView(webView, response.getErrorText());
                                } else {
                                    final String result = postProcessHTMLResult(response.getResult());
                                    Utils.setupWebView(webView, result);
                                    currentBody = response.getResult();
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

    private String postProcessHTMLResult(String result) {
        if (mode == Mode.GWT) {
            result = result.replace("src='/gwt/x/i","src='http://google.com/gwt/x/i");
        }
        return result;
    }

    private Utils.RESTResponse processURL(String url, Utils.Notification notification) {
        if (url.startsWith("!")) {
            // paging in gwt, for example
            return Utils.getJSON(SimpleBrowser.this, url.substring(1), notification, 60000);
        } else {
            switch (mode) {
                case BOILERPIPE:
                    final Utils.RESTResponse json = Utils.getJSON(SimpleBrowser.this, "http://boilerpipe-web.appspot.com/extract?url=" + Uri.encode(url) + "&extractor=ArticleExtractor&output=json&extractImages=", notification, 60000);
                    if (json.getErrorText() != null) return json;
                    try {
                        final JSONObject jo = new JSONObject(json.getResult());
                        final String status = jo.getString("status");
                        if (!status.equals("success"))
                            return new Utils.RESTResponse("status="+status, false, null);
                        final JSONObject response = jo.getJSONObject("response");
                        String title = null;
                        if (response.has("title"))
                            title = response.getString("title");
                        String body = response.getString("content");
                        final String[] strings = body.split("\n");
                        StringBuilder sb = new StringBuilder();
                        sb.append("<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'></head><body>");
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
                        return new Utils.RESTResponse(null, false, sb.toString());
                    } catch (JSONException e) {
                        return new Utils.RESTResponse(e.toString(), false, null);
                    }
                case GWT:
                    return Utils.getJSON(SimpleBrowser.this, "http://google.com/gwt/x?u="+Uri.encode(url)+"&ie=UTF-8&oe=UTF-8", notification, 60000);
                case GWT_NOIMG:
                    return Utils.getJSON(SimpleBrowser.this, "http://google.com/gwt/x?u="+Uri.encode(url)+"&ie=UTF-8&oe=UTF-8&noimg=1", notification, 60000);
                case JA_BOILERPIPE:
                case JA_POCKET:
                case JA_ANY:
                    String extractor = "any";
                    switch(mode) {
                        case JA_BOILERPIPE: extractor = "boilerpipe"; break;
                        case JA_POCKET: extractor = "pocketcom"; break;
                    }
                    final Utils.RESTResponse jaresult = Utils.getJSON(SimpleBrowser.this, "http://ja.ip.rt.ru:8080/htmlproxy?extractor="+extractor+"&url=" + Uri.encode(url), notification, 60000);
                    if (jaresult.getResult() == null) return jaresult;
                    StringBuilder sb = new StringBuilder();
                    sb.append("<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'></head><body>");
                    try {
                        final JSONObject jsonObject = new JSONObject(jaresult.getResult());
                        if (jsonObject.has("article")) {
                            extractor = "pocketcom";
                        } else {
                            extractor = "boilerpipe";
                        }
                        if (extractor.contains("pocketcom")) {
                            final JSONObject article = jsonObject.getJSONObject("article");
                            if (article != null) {
                                sb.append("<b>");
                                sb.append(toHTML(article.getString("title")));
                                sb.append("</b><p>");
                                sb.append(article.getString("article"));
                            }
                        }
                        if (extractor.equals("boilerpipe")) {
                            sb.append("<b>");
                            sb.append(toHTML(jsonObject.getString("title")));
                            sb.append("</b><p>");
                            final String[] texts = jsonObject.getString("text").split("\n");
                            for (String text : texts) {
                                sb.append("<p>");
                                sb.append(SimpleBrowser.toHTML(text));
                                sb.append("\n");
                            }
                        }
                        return new Utils.RESTResponse(null, false, sb.toString());
                    } catch (JSONException e) {
                        return new Utils.RESTResponse(e.toString(), false, null);
                    }

                default:
                    return new Utils.RESTResponse("Bad option", false, null);
            }
        }
    }

    @Override
    public void notifyDownloadError(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Utils.setupWebView(webView, error);
            }
        });
    }

    @Override
    public void notifyDownloadProgress(final int progressBytes) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Utils.setupWebView(webView, getString(R.string.Loading___) + " " + progressBytes / 1024 + " KB");
            }
        });

    }

    @Override
    public void notifyHttpClientObtained(HttpClient client) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public static String toHTML(String str) {
        str = str.replace("&","&amp;");
        str = str.replace("<","&lt;");
        str = str.replace(">","&gt;");
        return str;
    }
}
