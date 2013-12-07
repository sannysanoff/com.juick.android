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
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.R;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.GZIPInputStream;

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
public class Utils {

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

        public enum ReplyCode {
            FORBIDDEN,
            NORMAL,
            FAIL
        }

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


        String jaiprtruCache = null;

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
            if (jaiprtruCache != null && jaiprtruCache.length() > 0) {
                if (request.getURI().toString().contains(jaiprtruCache)) {
                    request.addHeader("Host","ja.ip.rt.ru");
                }
            }

        }

        @Override
        public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public String authorizeURL(String url, String cookie) {
            if (url.startsWith("http://ja.ip.rt.ru")) {
                if (jaiprtruCache == null) {
                    try {
                        InetAddress byName = Inet4Address.getByName("ja.ip.rt.ru");
                        jaiprtruCache = byName.getHostAddress();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
                if (jaiprtruCache != null && jaiprtruCache.length() > 0) {
                    url = url.replace("ja.ip.rt.ru", jaiprtruCache);
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
    public static final String JA_ADDRESS = "ja.ip.rt.ru:8080";
    public static final String JA_ADDRESS_HTTPS = "ja.ip.rt.ru:8443";

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

    public static interface Function<T, A> {
        T apply(A a);
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
                            receive.withService(binder.getService());
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


    public static interface Notification {

    }

    public static interface RetryNotification extends Notification {
        public void notifyRetryIsInProgress(int retry);
    }

    public static interface BackupServerNotification extends Notification {
        public void notifyBackupInUse(boolean backup);
    }

    public static interface HasCachedCopyNotification extends Notification {
        public void onCachedCopyObtained(ArrayList<JuickMessage> messages);
    }

    public static interface DownloadProgressNotification extends Notification {
        public void notifyDownloadProgress(int progressBytes);

        public void notifyHttpClientObtained(HttpClient client);
    }

    public static interface DownloadErrorNotification extends Notification {
        public void notifyDownloadError(String error);
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
                                ret[0] = streamToString(content, progressNotification);
                                content.close();
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

    public static class RESTResponse {
        public String result;
        public String errorText;
        public boolean mayRetry;

        public RESTResponse(String errorText, boolean mayRetry, String result) {
            this.errorText = errorText;
            this.mayRetry = mayRetry;
            this.result = result;
        }

        public String getResult() {
            return result;
        }

        public boolean isMayRetry() {
            return mayRetry;
        }

        public String getErrorText() {
            return errorText;
        }
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

    static final byte[][] allowedSignatures = new byte[][] {
            /*ja.ip.rt.ru */ new byte[] {-114,53,80,-7,114,-98,6,-50,-121,-46,127,58,-64,-4,12,-125,-19,38,-31,112,-10,56,-32,-101,113,67,-84,-9,60,-70,73,73,31,-46,123,98,-23,-118,45,-37,-7,-90,-117,111,123,-66,-15,-59,-69,-108,-16,-26,18,-71,112,33,2,88,39,62,30,-40,110,119,106,-90,95,91,127,-32,-54,107,37,-118,-8,-27,57,-85,7,36,-12,81,36,103,31,29,64,31,37,77,-48,-114,-24,-101,73,98,-39,-22,41,102,58,-40,-11,-115,-26,59,110,-44,49,58,80,-128,59,106,-93,56,104,-65,25,40,-59,-21,-48,65,-78,91,-107,68,-12,-72,37,-28,-53,54,73,28,-35,-68,34,-91,32,124,57,-76,61,111,-12,-56,3,48,-68,111,121,127,28,-50,50,67,-21,15,-116,-12,7,57,31,38,29,79,45,-96,104,-1,-62,17,122,99,10,35,-60,83,38,-103,57,-81,-77,-44,52,-65,45,93,110,-59,24,2,119,5,95,-44,-51,-71,106,122,37,-14,-89,42,92,-64,71,-57,14,-44,57,-83,-30,-34,87,-119,106,41,110,-57,51,-4,32,-27,86,62,113,-35,40,108,90,-55,91,90,-89,8,-45,63,-123,-59,-108,9,100,13,68,87,-112,-19,84,-71,-17,-2,-74,40},
            /* localhost */ new byte[] {106,51,41,24,68,-57,-20,13,-94,88,-18,-30,-127,-128,-41,56,98,-26,-49,-95,69,127,-72,-24,68,-85,46,-8,112,44,-76,51,79,25,-55,34,63,79,-85,-49,-22,44,90,-108,59,-63,96,-33,18,71,94,-58,-25,-102,30,21,-6,78,-122,-48,-7,-36,-25,-16,79,-72,-40,-17,72,-55,-122,71,2,-44,-81,-29,108,14,34,78,-3,110,9,81,21,84,-90,67,26,68,-124,-42,112,-103,-114,-15,40,-125,-60,-12,-100,102,20,87,-13,-97,71,-103,-41,-84,106,-78,-16,-35,-35,27,-37,-108,70,3,-101,78,-90,17,91,97,3,70,-68,-72,-94,19,54,117,-28,102,78,-42,13,-23,-118,12,-55,-33,-32,107,19,-32,80,-78,-42,107,94,28,-95,-123,-31,-50,58,-120,103,-100,-75,-95,-124,-121,57,-39,-40,54,-12,47,6,-106,-5,3,37,-88,-22,120,-27,117,122,114,-114,-39,-36,-89,-104,107,-32,89,7,-45,-15,117,54,-32,123,-124,46,-76,96,-35,68,28,-98,63,94,16,-43,-18,44,-120,-8,-57,52,33,66,91,21,41,-120,-29,-51,10,82,-89,65,-72,-122,103,67,8,-77,56,95,43,-128,-4,113,-23,-2,125,-68,28,-38,-7,-124,40,87,103,33,87,20,45},
            /* *.juick.com */ new byte[] {
            (byte) 0x50, (byte) 0x61, (byte) 0xc0, (byte) 0x3a, (byte) 0x20, (byte) 0x76, (byte) 0x8c, (byte) 0x0e, (byte) 0xa1, (byte) 0x57, (byte) 0xcb, (byte) 0xf7, (byte) 0xac, (byte) 0xcd, (byte) 0x09, (byte) 0x08,
            (byte) 0xa4, (byte) 0x92, (byte) 0x56, (byte) 0x00, (byte) 0x75, (byte) 0xf7, (byte) 0x7c, (byte) 0xdc, (byte) 0x6a, (byte) 0x53, (byte) 0xd7, (byte) 0x64, (byte) 0x05, (byte) 0x8c, (byte) 0x5e, (byte) 0x75,
            (byte) 0xef, (byte) 0x38, (byte) 0x06, (byte) 0x4a, (byte) 0xce, (byte) 0x8f, (byte) 0xcd, (byte) 0x3f, (byte) 0x59, (byte) 0x3f, (byte) 0xd3, (byte) 0x88, (byte) 0x9d, (byte) 0x7f, (byte) 0x73, (byte) 0x3a,
            (byte) 0x51, (byte) 0x0b, (byte) 0x0f, (byte) 0xaf, (byte) 0xf6, (byte) 0xfa, (byte) 0x1d, (byte) 0xb0, (byte) 0x99, (byte) 0xd3, (byte) 0xfb, (byte) 0xe2, (byte) 0xa3, (byte) 0x2d, (byte) 0xd6, (byte) 0x34,
            (byte) 0x36, (byte) 0x06, (byte) 0x7d, (byte) 0xee, (byte) 0xa4, (byte) 0xb3, (byte) 0x8d, (byte) 0x19, (byte) 0x91, (byte) 0x44, (byte) 0x1f, (byte) 0xfa, (byte) 0x82, (byte) 0x8d, (byte) 0xf1, (byte) 0xbe,
            (byte) 0x6f, (byte) 0xbd, (byte) 0xaf, (byte) 0x57, (byte) 0xe0, (byte) 0x1b, (byte) 0x6e, (byte) 0xfe, (byte) 0xfc, (byte) 0x6a, (byte) 0x15, (byte) 0x31, (byte) 0xae, (byte) 0xc1, (byte) 0x23, (byte) 0xda,
            (byte) 0xbe, (byte) 0xe9, (byte) 0xf5, (byte) 0x39, (byte) 0x4c, (byte) 0x6b, (byte) 0xf3, (byte) 0x2b, (byte) 0xd1, (byte) 0x59, (byte) 0xfd, (byte) 0x09, (byte) 0x49, (byte) 0x61, (byte) 0x4f, (byte) 0x0c,
            (byte) 0xaa, (byte) 0xef, (byte) 0x4d, (byte) 0xee, (byte) 0x3e, (byte) 0xa9, (byte) 0x33, (byte) 0x32, (byte) 0xf5, (byte) 0x3a, (byte) 0xf0, (byte) 0x65, (byte) 0xc3, (byte) 0x35, (byte) 0xf6, (byte) 0xc4,
            (byte) 0x88, (byte) 0x9f, (byte) 0x7c, (byte) 0x31, (byte) 0x05, (byte) 0x65, (byte) 0x1b, (byte) 0xb5, (byte) 0x61, (byte) 0xba, (byte) 0x9b, (byte) 0x82, (byte) 0x7f, (byte) 0xce, (byte) 0xd4, (byte) 0xeb,
            (byte) 0x2c, (byte) 0xab, (byte) 0x0e, (byte) 0x10, (byte) 0x0c, (byte) 0xae, (byte) 0xa0, (byte) 0xbc, (byte) 0xb5, (byte) 0xf7, (byte) 0xd4, (byte) 0x70, (byte) 0x6d, (byte) 0x76, (byte) 0x55, (byte) 0x3a,
            (byte) 0xe9, (byte) 0x1c, (byte) 0x38, (byte) 0x90, (byte) 0xcc, (byte) 0x66, (byte) 0x11, (byte) 0x66, (byte) 0x9b, (byte) 0xc5, (byte) 0x28, (byte) 0x59, (byte) 0xe1, (byte) 0x97, (byte) 0x95, (byte) 0x56,
            (byte) 0x16, (byte) 0xca, (byte) 0x5e, (byte) 0xfd, (byte) 0xb9, (byte) 0xc5, (byte) 0xf6, (byte) 0x2c, (byte) 0xa9, (byte) 0x43, (byte) 0xe4, (byte) 0x18, (byte) 0x41, (byte) 0x8e, (byte) 0xd0, (byte) 0xe7,
            (byte) 0x1b, (byte) 0xbb, (byte) 0xf6, (byte) 0xd8, (byte) 0x46, (byte) 0x94, (byte) 0x91, (byte) 0x08, (byte) 0xaf, (byte) 0xe8, (byte) 0x43, (byte) 0x54, (byte) 0x4c, (byte) 0x30, (byte) 0xb3, (byte) 0x57,
            (byte) 0x1f, (byte) 0x7b, (byte) 0xa2, (byte) 0xce, (byte) 0x59, (byte) 0xb0, (byte) 0xcb, (byte) 0xb6, (byte) 0x29, (byte) 0x6b, (byte) 0x63, (byte) 0x93, (byte) 0xea, (byte) 0x4b, (byte) 0x14, (byte) 0x48,
            (byte) 0x90, (byte) 0x0f, (byte) 0xd7, (byte) 0x9d, (byte) 0xe8, (byte) 0x9a, (byte) 0x89, (byte) 0x5b, (byte) 0x3d, (byte) 0xeb, (byte) 0x8e, (byte) 0x7d, (byte) 0x3f, (byte) 0x27, (byte) 0x70, (byte) 0x85,
            (byte) 0xbd, (byte) 0x2e, (byte) 0x47, (byte) 0xa7, (byte) 0xc1, (byte) 0x4e, (byte) 0x05, (byte) 0x26, (byte) 0xdb, (byte) 0x19, (byte) 0xe0, (byte) 0x27, (byte) 0x9e, (byte) 0xf3, (byte) 0x76, (byte) 0xc7
        }
    };

    public static class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    for (X509Certificate chainElement: chain) {
                        byte[] thisSignature = chainElement.getSignature();
                        for (byte[] allowedSignature : allowedSignatures) {
                            if (Arrays.equals(thisSignature, allowedSignature)) {
                                return;
                            }
                        }
                    }
                    throw new CertificateException("Invalid HTTPS certificate. Please update Juick Advanced client, this may fix it.");
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[]{tm}, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

    public static DefaultHttpClient getNewHttpClient() {

        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
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
                    //conn.setRequestMethod("POST");

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


    public static RESTResponse streamToString(InputStream is, Notification progressNotification) {
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
            return new RESTResponse(null, false, new String(baos.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification)
                ((DownloadErrorNotification) progressNotification).notifyDownloadError(e.toString());
            Log.e("streamReader", e.toString());
            return new RESTResponse(e.toString(), true, null);
        }
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


}
