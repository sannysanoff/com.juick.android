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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Service;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;
import com.juickadvanced.IHTTPClient;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.R;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.BreakIterator;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import com.juickadvanced.protocol.Base64;
import com.juickadvanced.xmpp.ServerToClient;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.*;

/**
 * @author Ugnich Anton
 */
public class Utils extends com.juickadvanced.Utils {

    public static final String NO_AUTH = "NO AUTH";
    public static ArrayList<URLAuth> authorizers = new ArrayList<URLAuth>();

    public static Throwable getRootException(Throwable e, int maxLoop) {
        if (e.getCause() == e) return e;
        if (e.getCause() == null) return e;
        if (maxLoop == 0) return e;
        return getRootException(e.getCause(), maxLoop-1);

    }

    public static abstract class URLAuth {

        public static String REFUSED_AUTH = "___refused__auth____!!!";

        public boolean isNoAuthInResponse(RESTResponse restResponse) {
            return false;
        }

        public abstract boolean isForBlog(String microblogCode);

        public enum ReplyCode {
            FORBIDDEN,
            NORMAL,
            FAIL
        }

        public abstract void maybeLoadCredentials(Context context);

        public abstract boolean acceptsURL(String url);

        public abstract void authorize(Context act, boolean forceOptionalAuth, boolean forceAttachCredentials, String url, Function<Void, String> withCookie);

        public abstract void authorizeRequest(HttpRequestBase request, String cookie);

        public abstract void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url);

        public abstract String authorizeURL(String url, String cookie);

        public abstract ReplyCode validateNon200Reply(HttpURLConnection conn, String url, boolean wasForcedAuth) throws IOException;

        public abstract ReplyCode validateNon200Reply(HttpResponse o, String url, boolean wasForcedAuth);

        public abstract void clearCookie(Context context, Runnable then);

        public abstract void reset(Context context, Handler handler);
    }

    static class DummyAuthorizer extends URLAuth {


        String jahostCache = null;

        @Override
        public boolean isForBlog(String microblogCode) {
            return false;
        }

        @Override
        public void maybeLoadCredentials(Context context) {

        }

        @Override
        public boolean acceptsURL(String url) {
            return true;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void authorize(Context act, boolean forceOptionalAuth, boolean forceAttachCredentials, String url, Function<Void, String> withCookie) {
            withCookie.apply(null);
        }

        @Override
        public void authorizeRequest(HttpRequestBase request, String cookie) {
            if (jahostCache != null && jahostCache.length() > 0) {
                if (request.getURI().toString().contains(jahostCache)) {
                    request.addHeader("Host",JAXMPPClient.jahost);
                }
            }

        }

        @Override
        public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public String authorizeURL(String url, String cookie) {
            if (url.startsWith("http://"+JAXMPPClient.jahost)) {
                if (jahostCache == null) {
                    try {
                        InetAddress byName = Inet4Address.getByName(JAXMPPClient.jahost);
                        jahostCache = byName.getHostAddress();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
                if (jahostCache != null && jahostCache.length() > 0) {
                    url = url.replace(JAXMPPClient.jahost, jahostCache);
                }
            }
            return url;
        }

        @Override
        public ReplyCode validateNon200Reply(HttpURLConnection conn, String url, boolean wasForcedAuth) {
            return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public ReplyCode validateNon200Reply(HttpResponse o, String url, boolean wasForcedAuth) {
            return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void clearCookie(Context context, Runnable then) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void reset(Context context, Handler handler) {
            //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    //public static final String JA_ADDRESS = "192.168.1.77:8080";
    public static final String JA_ADDRESS = JAXMPPClient.jahost+":8080";
    public static final String JA_API_URL = "https://"+JAXMPPClient.jahost+"/api";
    //public static final String JA_ADDRESS_HTTPS = "http://192.168.1.77:8080";

    public static void verboseDebugString(final Activity context, final String s) {
        boolean verboseDebug = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("verboseDebug", false);
        if (verboseDebug) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static boolean skipWeb = false;

    public static void setupWebView(final WebView wv, String content) {
        try {
            if (skipWeb) return;
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(wv.getContext());
            File file = new File(wv.getContext().getCacheDir(), "temp.html");
            String PREFIX = "#prefs.checked.";
            while (true) {
                int ix = content.indexOf(PREFIX);
                if (ix == -1) break;
                int ix2 = content.indexOf("#", ix + 1);
                if (ix2 == -1) break;
                String key = content.substring(ix + PREFIX.length(), ix2);
                boolean def = false;
                if (key.endsWith("!")) {
                    def = true;
                    key = key.substring(0, key.length() - 1);
                }
                boolean checked = sp.getBoolean(key, def);
                content = content.substring(0, ix) + (checked ? "checked" : "") + content.substring(ix2 + 1);
            }
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(content);
            fileWriter.close();
            wv.getSettings().setJavaScriptEnabled(true);
//            wv.getSettings().setBlockNetworkImage(false);
//            wv.getSettings().setBlockNetworkLoads(false);
            wv.getSettings().setLoadsImagesAutomatically(true);
            Uri uri = Uri.fromFile(file);
            wv.addJavascriptInterface(new Object() {
                public void onFormData(String str) {
                    wv.setTag(str);
                }
            }, "EXT");
            wv.loadUrl(uri.toString());
        } catch (IOException e) {
            //
        }

    }


    public static class GetServiceRequest {
        ServiceGetter sg;
        ServiceGetter.Receiver receive;

        GetServiceRequest(ServiceGetter sg, ServiceGetter.Receiver receive) {
            this.sg = sg;
            this.receive = receive;
        }
    }

    public static class GetServiceStatus {
        public GetServiceStatus() {
        }

        int inProgress;
        ArrayList<GetServiceRequest> waiting = new ArrayList<GetServiceRequest>();

        public void tryNext() {
            GetServiceRequest remove = null;
            synchronized (waiting) {
                if (waiting.size() > 0) {
                    remove = waiting.remove(0);
                }
            }
            if (remove != null) {
                Log.i("JA-BIND", "Dequeued request for: "+remove.sg.serviceClass);
                remove.sg.getService(remove.receive);
            }
        }
    }

    static Map<Class, GetServiceStatus> getServiceQueue = new HashMap<Class, GetServiceStatus>();

    public static class ServiceGetter<T extends Service> {

        Context context;
        Class<T> serviceClass;

        public ServiceGetter(Context context, Class<T> serviceClass) {
            this.context = context;
            this.serviceClass = serviceClass;
        }

        public abstract static class Receiver<T extends Service> {
            public void withService(T service) {

            }

            public void withoutService() {

            }
        }

        public static class LocalBinder<T extends Service> extends Binder implements IBinder {

            T service;

            public LocalBinder(T service) {
                this.service = service;
            }


            T getService() {
                return service;
            }
        }

        public void getService(final Receiver<T> receive) {
            final GetServiceStatus getServiceStatus;
            if (serviceClass == DatabaseService.class) {
                if (DatabaseService.INSTANCE != null && DatabaseService.running) {
                    DatabaseService.INSTANCE.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (DatabaseService.INSTANCE != null && DatabaseService.running) {
                                receive.withService((T)DatabaseService.INSTANCE);
                            } else {
                                getService(receive);
                            }
                        }
                    });
                    return;
                }
            }

            synchronized (getServiceQueue) {
                GetServiceStatus getServiceStatus__ = getServiceQueue.get(serviceClass);
                if (getServiceStatus__ == null) {
                    getServiceStatus__ = new GetServiceStatus();
                    getServiceQueue.put(serviceClass, getServiceStatus__);
                }
                getServiceStatus = getServiceStatus__;
                if (getServiceStatus.inProgress >= 3) {
                    Log.i("JA-BIND", "Queued request for: "+serviceClass);
                    getServiceStatus.waiting.add(new GetServiceRequest(this, receive));
                    return;
                }
                getServiceStatus.inProgress++;
            }

            if (serviceClass == XMPPService.class) {
                if (XMPPService.instance != null) {
                    XMPPService.instance.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                receive.withService((T)XMPPService.instance);
                            } finally {
                                synchronized (getServiceQueue) {
                                    getServiceStatus.inProgress--;
                                    getServiceStatus.tryNext();
                                }
                            }
                        }
                    });
                    return;
                }
            }

            final Exception where = new Exception("This is the stack trace of call");
            ServiceConnection mConnection = new ServiceConnection() {

                @Override
                public void onServiceConnected(ComponentName className,
                                               IBinder ibinder) {
                    Log.i("JA-BIND", "Successful bind service: "+className+" " + ibinder);
                    // We've bound to LocalService, cast the IBinder and get LocalService instance
                    if (LocalBinder.class.isAssignableFrom(ibinder.getClass())) {
                        LocalBinder<T> binder = (LocalBinder<T>) ibinder;
                        try {
                            T svc = binder.getService();
                            if (svc != null) {
                                receive.withService(svc);
                            } else {
                                receive.withoutService();
                            }
                        } finally {
                            context.unbindService(this);
                            synchronized (getServiceQueue) {
                                getServiceStatus.inProgress--;
                                getServiceStatus.tryNext();
                            }
                        }
                    } else {
                        throw new RuntimeException("getService: bad binder: " + ibinder, where);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    receive.withoutService();
                    context.unbindService(this);
                    synchronized (getServiceQueue) {
                        getServiceStatus.inProgress--;
                    }
                    getServiceStatus.tryNext();
                }
            };

            Intent intent = new Intent(context, serviceClass);
            Log.i("JA-BIND", "Begin bind service: "+serviceClass);
            context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        }
    }


    public static int doHttpGetRequest(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();
            conn.setUseCaches(false);
            conn.connect();
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;
        } catch (Exception e) {
            Log.e("doHttpGetRequest", e.toString());
        }
        return 0;
    }

    public static boolean hasAuth(Context context) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        return accs.length > 0;
    }


    public static RESTResponse getJSONWithRetries(Context context, String url, Notification notification) {
        RESTResponse retval = null;
        for (int i = 0; i < 5; i++) {
            retval = getJSON(context, url, notification instanceof DownloadProgressNotification ? (DownloadProgressNotification) notification : null);
            if (retval.result == null && !NO_AUTH.equals(retval.getErrorText())) {
                if (notification instanceof RetryNotification) {
                    ((RetryNotification) notification).notifyRetryIsInProgress(i + 1);
                }
                continue;
            } else {
                break;
            }
        }
        return retval;
    }

    public static RESTResponse getJSON(Context context, String url, Notification progressNotification) {
        return getJSON(context, url, progressNotification, -1);
    }

    static boolean reportTimes = false;

    public static int reloginTried;

    public static RESTResponse getJSON(final Context context, final String url, final Notification progressNotification, final int timeout) {
        return getJSON(context, url, progressNotification, timeout, false);
    }

    public static RESTResponse getJSON(final Context context, final String url, final Notification progressNotification, final int timeout, final boolean forceAttachCredentials) {
        final URLAuth authorizer = getAuthorizer(url);
        final RESTResponse[] ret = new RESTResponse[]{null};
        final URLAuth finalAuthorizer = authorizer;
        final boolean[] cookieCleared = {false};
        authorizer.authorize(context, false, forceAttachCredentials, url, new Function<Void, String>() {
            @Override
            public Void apply(String myCookie) {
                final boolean noAuthRequested = myCookie != null && myCookie.equals(URLAuth.REFUSED_AUTH);
                if (noAuthRequested) myCookie = null;
                final DefaultHttpClient client = getNewHttpClient();
                try {
                    final Function<Void, String> thiz = this;
                    final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean compression = sp.getBoolean("http_compression", false);
                    long l = System.currentTimeMillis();
                    if (compression)
                        initCompressionSupport(client);
                    HttpGet httpGet = new HttpGet(authorizer.authorizeURL(url, myCookie));
                    Integer timeoutForConnection = timeout > 0 ? timeout : (sp.getBoolean("use_timeouts_json", false ) ? 10000 : 2222000);
                    client.getParams().setParameter("http.connection.timeout", timeoutForConnection);
                    httpGet.getParams().setParameter("http.socket.timeout", timeoutForConnection);
                    httpGet.getParams().setParameter("http.protocol.head-body-timeout", timeoutForConnection);


                    finalAuthorizer.authorizeRequest(httpGet, myCookie);

                    client.execute(httpGet, new ResponseHandler<Object>() {
                        @Override
                        public Object handleResponse(HttpResponse o) throws ClientProtocolException, IOException {
                            URLAuth.ReplyCode authReplyCode = o.getStatusLine().getStatusCode() == 200 ? URLAuth.ReplyCode.NORMAL : authorizer.validateNon200Reply(o, url, forceAttachCredentials);
                            boolean simulateError = false;
                            if (o.getStatusLine().getStatusCode() == 200 && !simulateError) {
                                reloginTried = 0;
                                HttpEntity e = o.getEntity();
                                if (progressNotification instanceof DownloadProgressNotification) {
                                    ((DownloadProgressNotification) progressNotification).notifyDownloadProgress(0);
                                }
                                InputStream content = e.getContent();
                                RESTResponse retval = streamToString(content, progressNotification);
                                content.close();
                                if (authorizer.isNoAuthInResponse(retval) && !cookieCleared[0]) {
                                    cookieCleared[0] = true;    // don't enter loop
                                    authorizer.clearCookie(context, new Runnable() {
                                        @Override
                                        public void run() {
                                            authorizer.authorize(context, true, false, url, thiz);
                                        }
                                    });
                                    return null;
                                }
                                ret[0] = retval;
                            } else {
                                if (authReplyCode == URLAuth.ReplyCode.FORBIDDEN && noAuthRequested) {
                                    ret[0] = new RESTResponse(NO_AUTH, false, null);
                                    return o;
                                }
                                if (authReplyCode == URLAuth.ReplyCode.FORBIDDEN && !forceAttachCredentials) {
                                    ret[0] = getJSON(context, url, progressNotification, timeout, true);
                                    return o;
                                } else if (authReplyCode == URLAuth.ReplyCode.FORBIDDEN && !cookieCleared[0]) {
                                    cookieCleared[0] = true;    // don't enter loop
                                    authorizer.clearCookie(context, new Runnable() {
                                        @Override
                                        public void run() {
                                            authorizer.authorize(context, true, false, url, thiz);
                                        }
                                    });
                                    return null;
                                } else if (o.getStatusLine().getStatusCode() / 100 == 4) {
                                    if (context instanceof Activity) {
                                        final Activity activity = (Activity) context;
                                        reloginTried++;
                                        if (reloginTried == 3) {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    sp.edit().remove("web_cookie").commit();
                                                    reloginTried = 0;
                                                }
                                            });
                                        }
                                    }
                                    // fall through
                                }

                                if (progressNotification instanceof DownloadErrorNotification) {
                                    ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP response code: " + o.getStatusLine().getStatusCode());
                                }
                                ret[0] = new RESTResponse("HTTP: " + o.getStatusLine().getStatusCode() + " " + o.getStatusLine().getReasonPhrase(), false, null);
                            }
                            return o;
                        }
                    });
                    l = System.currentTimeMillis() - l;
                    if (reportTimes) {
                        Toast.makeText(context, "Load time=" + l + " msec", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    if (progressNotification instanceof DownloadErrorNotification) {
                        ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP connect: " + e.toString());
                    }
                    Log.e("getJSON", e.toString());
                    ret[0] = new RESTResponse(e.toString(), true, null);
                } finally {
                    client.getConnectionManager().shutdown();
                }
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        while (ret[0] == null) { // bad, but true
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return ret[0];
    }

    public static URLAuth getAuthorizer(String url) {
        URLAuth authorizer = new DummyAuthorizer();
        for (URLAuth a : authorizers) {
            if (a.acceptsURL(url)) {
                authorizer = a;
                break;
            }
        }
        return authorizer;
    }


    public static BINResponse getBinary(Context context, final String url, final Notification progressNotification, int timeout) {
        try {
            if (url == null)
                return new BINResponse("NULL url", false, null);
            URLConnection urlConnection = new URL(url).openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            BINResponse binResponse = streamToByteArray(inputStream, progressNotification);
            inputStream.close();
            String location = urlConnection.getHeaderField("Location");
            if (location != null) {
                return getBinary(context, location, progressNotification, timeout);
            }
            return binResponse;
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification) {
                ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP connect: " + e.toString());
            }
            Log.e("getBinary", e.toString());
            return new BINResponse(e.toString(), true, null);
        }
    }

    public static class GzipDecompressingEntity extends HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent()
                throws IOException, IllegalStateException {

            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();

            return new GZIPInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            // length of ungzipped content not known in advance
            return -1;
        }

    } // class GzipDecompressingEntity

    private static void initCompressionSupport(DefaultHttpClient httpclient) {


        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }

        });

        httpclient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Header ceheader = entity.getContentEncoding();
                    if (ceheader != null) {
                        HeaderElement[] codecs = ceheader.getElements();
                        for (int i = 0; i < codecs.length; i++) {
                            if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                                response.setEntity(
                                        new GzipDecompressingEntity(response.getEntity()));
                                return;
                            }
                        }
                    }
                }
            }

        });
    }

    public static class BINResponse {
        byte[] result;
        String errorText;
        boolean mayRetry;

        public BINResponse(String errorText, boolean mayRetry, byte[] result) {
            this.errorText = errorText;
            this.mayRetry = mayRetry;
            this.result = result;
        }

        public byte[] getResult() {
            return result;
        }

        public boolean isMayRetry() {
            return mayRetry;
        }

        public String getErrorText() {
            return errorText;
        }
    }

    public static DefaultHttpClient getNewHttpClient() {
        return new DefaultHttpClient();
    }

    public static RESTResponse postJA(final Context context, final String url, final String dataValue) {
        try {
            HttpClient client = getNewHttpClient();
            HttpPost post = new HttpPost(url);
            List<org.apache.http.NameValuePair> args = new ArrayList<org.apache.http.NameValuePair>();
            args.add(new BasicNameValuePair("data", dataValue));
            post.setEntity(new UrlEncodedFormEntity(args));
            HttpResponse execute = client.execute(post);
            HttpEntity result = execute.getEntity();
            InputStream content = result.getContent();
            RESTResponse restResponse = streamToString(content, null);
            content.close();
            return restResponse;
        } catch (Exception e) {
            return new RESTResponse(e.toString(), false, null);
        }
    }

    public abstract static class NameValuePair {
        String name;

        NameValuePair(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        public abstract InputStream getValue();
    }

    public static class NameStringValuePair extends NameValuePair {
        private String value;

        public NameStringValuePair(String name, String value) {
            super(name);
            this.value = value;
        }

        @Override
        public InputStream getValue() {
            return new ByteArrayInputStream(value.getBytes());
        }
    }

    public static class NameStreamValuePair extends NameValuePair {
        private InputStream is;

        public NameStreamValuePair(String name, InputStream is) {
            super(name);
            this.is = is;
        }

        @Override
        public InputStream getValue() {
            return is;
        }
    }

    static {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        } };

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public static RESTResponse postForm(final Context context, final String url, ArrayList<NameValuePair> data) {
        try {
            final String end = "\r\n";
            final String twoHyphens = "--";
            final String boundary = "****+++++******+++++++********";

            URL apiUrl = new URL(url);

            final HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setConnectTimeout(10000);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Charset", "UTF-8");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.connect();
            OutputStream out = conn.getOutputStream();

            PrintStream ps = new PrintStream(out);
            int index = 0;
            byte[] block = new byte[1024];
            for (NameValuePair nameValuePair : data) {
                ps.print(twoHyphens + boundary + end);
                ps.print("Content-Disposition: form-data; name=\""+nameValuePair.getName()+"\"" + end + end);
                final InputStream value = nameValuePair.getValue();
                while(true) {
                    final int rd = value.read(block, 0, block.length);
                    if (rd < 1) {
                        break;
                    }
                    ps.write(block, 0, rd);
                }
                value.close();
                ps.print(end);
            }
            ps.print(twoHyphens + boundary + twoHyphens + end);
            ps.close();
            boolean b = conn.getResponseCode() == 200;
            if (!b) {
                return new RESTResponse("HTTP "+conn.getResponseCode()+": "+conn.getResponseMessage(), false, null);
            } else {
                InputStream inputStream = conn.getInputStream();
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] arr = new byte[1024];
                    while(true) {
                        int rd = inputStream.read(arr);
                        if (rd < 1) break;
                        baos.write(arr, 0, rd);
                    }
                    if (conn.getHeaderField("X-GZIPCompress") != null) {
                        return new RESTResponse(null, false, baos.toString(0));
                    } else {
                        return new RESTResponse(null, false, baos.toString());
                    }
                } finally {
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            return new RESTResponse(e.toString(), false, null);
        }
    }

    public static RESTResponse postJSON(final Context context, final String url, final String data) {
        return postJSON(context, url, data, null);
    }

    public static RESTResponse postJSON(final Context context, final String url, final String data, final String contentType) {
        final URLAuth authorizer = getAuthorizer(url);
        final RESTResponse[] ret = new RESTResponse[]{null};
        final boolean[] cookieCleared = new boolean[]{false};
        authorizer.authorize(context, false, false, url, new Function<Void, String>() {
            @Override
            public Void apply(String myCookie) {
                final boolean noAuthRequested = myCookie != null && myCookie.equals(URLAuth.REFUSED_AUTH);
                if (noAuthRequested) myCookie = null;
                HttpURLConnection conn = null;
                try {
                    String nurl = authorizer.authorizeURL(url, myCookie);
                    URL jsonURL = new URL(nurl);
                    conn = (HttpURLConnection) jsonURL.openConnection();
                    if (contentType != null) {
                        conn.addRequestProperty("Content-Type", contentType);
                    }
                    authorizer.authorizeRequest(context, conn, myCookie, nurl);

                    conn.setUseCaches(false);
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");

                    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                    wr.write(data);
                    wr.close();

                    URLAuth.ReplyCode authReplyCode = authorizer.validateNon200Reply(conn, url, false);
                    try {
                        if (authReplyCode == URLAuth.ReplyCode.FORBIDDEN && noAuthRequested) {
                            ret[0] = new RESTResponse(NO_AUTH, false, null);
                        } else if (authReplyCode == URLAuth.ReplyCode.FORBIDDEN && !cookieCleared[0]) {
                            cookieCleared[0] = true;    // don't enter loop
                            final Function<Void, String> thiz = this;
                            authorizer.clearCookie(context, new Runnable() {
                                @Override
                                public void run() {
                                    authorizer.authorize(context, true, false, url, thiz);
                                }
                            });
                        } else {
                            if (conn.getResponseCode() == 200 || authReplyCode == URLAuth.ReplyCode.NORMAL) {
                                InputStream inputStream = conn.getInputStream();
                                ret[0] = streamToString(inputStream, null);
                                inputStream.close();
                            } else {
                                ret[0] = new RESTResponse("HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage(), false, null);
                            }
                        }
                    } finally {
                        conn.disconnect();
                    }
                } catch (Exception e) {
                    Log.e("getJSON", e.toString());
                    ret[0] = new RESTResponse(ServerToClient.NETWORK_CONNECT_ERROR + e.toString(), true, null);
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        while (ret[0] == null) { // bad, but true
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return ret[0];
    }


    public static BINResponse streamToByteArray(InputStream is, Notification progressNotification) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long l = System.currentTimeMillis();
            byte[] buf = new byte[1024];
            while (true) {
                int len = is.read(buf);
                if (len <= 0) break;
                baos.write(buf, 0, len);
                if (System.currentTimeMillis() - l > 100) {
                    l = System.currentTimeMillis();
                    if (progressNotification instanceof DownloadProgressNotification)
                        ((DownloadProgressNotification) progressNotification).notifyDownloadProgress(baos.size());
                }
            }
            return new BINResponse(null, false, baos.toByteArray());
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification)
                ((DownloadErrorNotification) progressNotification).notifyDownloadError(e.toString());
            Log.e("streamReader", e.toString());
            return new BINResponse(e.toString(), true, null);
        }
    }

    public static String getMD5DigestForString(String str) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final byte[] bytes = str.getBytes();
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        StringBuffer sb = new StringBuffer();
        Base64.encode(digest, 0, digest.length, sb);
        return sb.toString();
    }

    public static Bitmap downloadImage(String url) {
        try {
            URL imgURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) imgURL.openConnection();
            conn.setDoInput(true);
            conn.connect();
            return BitmapFactory.decodeStream(conn.getInputStream());
        } catch (Exception e) {
            Log.e("downloadImage", e.toString());
        }
        return null;
    }

    public static Set<String> string2set(String str) {
        List<String> strings = Arrays.asList(str.split("@"));
        HashSet<String> strings1 = new HashSet<String>();
        for (String string : strings) {
            string = string.replace("[SOBAKA]", "@");               // kind of escaped
            strings1.add(string);
        }
        return strings1;
    }

    public static String set2string(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() != 0)
                sb.append("@");
            sb.append(s.replace("@", "[SOBAKA]"));
        }
        return sb.toString();
    }

    public static String getWordAtOffset(final String text, final int offset) {
        BreakIterator wordIterator = BreakIterator.getWordInstance();
        wordIterator.setText(text);
        int start = wordIterator.first();
        for (int end = wordIterator.next(); end != BreakIterator.DONE; start = end, end = wordIterator.next()) {
            if ((end >= offset) && (end - start > 1)) {
                return text.substring(start, end);
            }
        }
        return null;
    }

    public static class AndroidHTTPClient implements IHTTPClient {

        HttpRequestBase base;
        Context context;
        HttpClient client = new DefaultHttpClient();

        public AndroidHTTPClient(Context context) {
            this.context = context;
        }


        @Override
        public void setURL(String method, String url) {
            if (method.equals("POST")) {
                base = new HttpPost(url);
            } else {
                base = new HttpGet(url);
            }

        }

        @Override
        public void addHeader(String name, String value) {
            base.addHeader(name, value);
        }

        @Override
        public Response execute() throws IOException {
            final String url = base.getURI().toURL().toString();
            final URLAuth authorizer = getAuthorizer(url);
            final AtomicReference<Response> retval = new AtomicReference<Response>();
            new Thread() {
                public void run() {
                    authorizer.authorize(context, false, false, url, new Function<Void, String>() {
                        @Override
                        public Void apply(String myCookie) {
                            try {
                                authorizer.authorizeRequest(base, myCookie);
                                final HttpResponse execute = client.execute(base);
                                final HttpEntity entity = execute.getEntity();
                                retval.set(new Response(execute.getStatusLine().getStatusCode()) {
                                    @Override
                                    public InputStream getStream() throws IOException {
                                        return entity.getContent();
                                    }

                                    @Override
                                    public Header[] getHeaders(String s) {
                                        org.apache.http.Header[] headers = execute.getHeaders(s);
                                        Header[] retval = new Header[headers.length];
                                        for (int i = 0; i < headers.length; i++) {
                                            org.apache.http.Header header = headers[i];
                                            retval[i] = new Header(header.getName(), header.getValue());
                                        }
                                        return retval;
                                    }
                                });
                            } catch (Exception e) {
                                retval.set(new Response(599) {
                                    @Override
                                    public InputStream getStream() throws IOException {
                                        return null;
                                    }

                                    @Override
                                    public Header[] getHeaders(String s) {
                                        return new Header[0];
                                    }
                                });
                            } finally {
                                synchronized (retval) {
                                    retval.notify();
                                }
                            }
                            return null;
                        }
                    });
                }
            }.start();
            synchronized (retval) {
                try {
                    retval.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return retval.get();
        }

        @Override
        public void setURLEncodedPostData(String data) {
            try {
                if (base instanceof HttpPost) {
                    ((HttpPost)base).setEntity(new StringEntity(data));
                    ((HttpPost)base).addHeader("Content-Type","application/x-www-form-urlencoded");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void terminate() {
            client.getConnectionManager().shutdown();
        }
    }

}
