package com.juick.android;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import com.juickadvanced.R;
import org.apache.http.client.HttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/8/12
 * Time: 2:08 AM
 * To change this template use File | Settings | File Templates.
 */
public class UserpicStorage {

    public static UserpicStorage instance = new UserpicStorage();

    LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(50);
    Set<String> loadingImages = new HashSet<String>();

    public UserpicStorage() {

    }

    HashMap<String, ArrayList<Listener>> listeners = new HashMap<String, ArrayList<Listener>>();

    public interface Listener {
        public void onUserpicReady(int id, int size);
    }

    public void removeListener(int id, int size, Listener listener) {
        final String key = "" + id + "|" + size;
        synchronized (loadingImages) {
            ArrayList<Listener> listeners1 = listeners.get(key);
            if (listeners1 == null) return;
            listeners1.remove(listener);
            if (listeners.size() == 0) {
                listeners.remove(key);
            }
        }
    }

    public Bitmap getUserpic(final Context ctx, final int id, final int size, Listener toAdd) {
        final String key = "" + id + "|" + size;
        Bitmap bitmap = cache.get(key);
        if (bitmap != null) {
            return bitmap;
        }

        synchronized (loadingImages) {
            if (loadingImages.contains(key)) {
                return null;
            }
            if (toAdd != null) {
                ArrayList<Listener> listeners1 = listeners.get(key);
                if (listeners1 == null) {
                    listeners1 = new ArrayList<Listener>();
                }
                listeners.put(key, listeners1);
                listeners1.add(toAdd);
            }
        }
        new Thread() {
            @Override
            public void run() {
                final Utils.ServiceGetter<DatabaseService> dbs = new Utils.ServiceGetter<DatabaseService>(ctx, DatabaseService.class);
                dbs.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        final byte[] arr = service.getStoredUserpic(id);
                        if (arr == null) {
                            synchronized (loadingImages) {
                                if (!loadingImages.contains(key)) {
                                    loadingImages.add(key);
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            try {
                                                Utils.BINResponse binary = Utils.getBinary(ctx, "http://i.juick.com/as/" + id + ".png", null, 0);
                                                boolean persist = true;
                                                if (binary.errorText != null) {
                                                    binary.result = new byte[0];
                                                    if (binary.errorText.toLowerCase().contains("notfound")) {
                                                        // persist
                                                    } else {
                                                        persist = false;    // other error, will retry later
                                                    }
                                                }
                                                if (persist) {
                                                    dbs.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                                                        @Override
                                                        public void withService(DatabaseService service) {
                                                            service.storeUserpic(id, arr);
                                                        }
                                                    });
                                                }
                                                scaleAndPutToCache(ctx, binary.result, size, id, key);
                                            } finally {
                                                synchronized (loadingImages) {
                                                    loadingImages.remove(key);
                                                }
                                            }
                                        }
                                    }.start();
                                }
                            }
                        } else {
                            scaleAndPutToCache(ctx, arr, size, id, key);
                        }
                    }
                });
            }

        }.start();
        return null;
    }

    private void scaleAndPutToCache(Context ctx, byte[] arr, int size, int id, String key) {
        if (arr.length == 0) {
            Bitmap bmp = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.default_userpic);
            bmp = Bitmap.createScaledBitmap(bmp, size, size, true);
            cache.put(key, bmp);
        } else {
            Bitmap bmp = BitmapFactory.decodeByteArray(arr, 0, arr.length);
            bmp = Bitmap.createScaledBitmap(bmp, size, size, true);
            cache.put(key, bmp);
        }
        ArrayList<Listener> listeners1 = null;
        synchronized (loadingImages) {
            ArrayList<Listener> lr = listeners.get(key);
            if (lr != null) {
                listeners1 = new ArrayList<Listener>(lr);
            }
        }
        if (listeners1 != null) {
            for (Listener listener : listeners1) {
                listener.onUserpicReady(size, id);
            }
        }

    }


}
