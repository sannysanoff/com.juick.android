package com.juick.android;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.*;
import com.juick.android.api.MessageIDAdapter;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juick.android.juick.JuickMicroBlog;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessageID;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/3/12
 * Time: 2:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class DatabaseService extends Service {

    Handler handler;
    SharedPreferences sp;

    /**
     * save to user's "Saved messages list"
     *
     * @param messag
     */
    public void saveMessage(final JuickMessage messag) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    Gson gson = getGson();
                    final String value = gson.toJson(messag);
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put("msgid", messag.getMID().toString());
                        cv.put("tm", messag.Timestamp.getTime());
                        cv.put("save_date", System.currentTimeMillis());
                        cv.put("body", compressGZIP(value));
                        db.insert("saved_message2", null, cv);   // failed uniq constraint is handled here.
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    return Boolean.TRUE;
                }
            });
            writeJobs.notify();
        }
    }

    public void saveRecentlyOpenedThread(final JuickMessage messag) {
        saveRecentThread(messag, "recent_threads_opened");
    }

    public void saveRecentlyCommentedThread(final JuickMessage messag) {
        saveRecentThread(messag, "recent_threads_wrote");
    }

    public ArrayList<JuickMessage> getRecentlyOpenedThreads() {
        Cursor cursor = db.rawQuery("select * from recent_threads_opened order by save_date desc", new String[]{});
        return convertFromSavedMessages(cursor);

    }

    public ArrayList<JuickMessage> getRecentlyCommentedThreads() {
        Cursor cursor = db.rawQuery("select * from recent_threads_wrote order by save_date desc", new String[]{});
        return convertFromSavedMessages(cursor);

    }

    public void saveRecentThread(final JuickMessage messag, final String table) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    Gson gson = getGson();
                    final String value = gson.toJson(messag);
                    try {
                        db.delete(table, "msgid = ?", new String[]{messag.getMID().toString()});
                        Cursor cursor = db.rawQuery("select count(*) cnt from " + table, new String[]{});
                        cursor.moveToNext();
                        int count = cursor.getInt(0);
                        cursor.close();
                        for (int i = 49; i < count; i++) {
                            // cleanup
                            cursor = db.rawQuery("select msgid from " + table + " order by save_date asc limit 1", new String[]{});
                            cursor.moveToNext();
                            String msgid = cursor.getString(0);
                            cursor.close();
                            db.delete(table, "msgid = ?", new String[]{msgid});  // opened most recently
                        }
                        ContentValues cv = new ContentValues();
                        cv.put("msgid", messag.getMID().toString());
                        cv.put("save_date", System.currentTimeMillis());
                        cv.put("body", compressGZIP(value));
                        db.insert(table, null, cv);
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    return Boolean.TRUE;
                }
            });
            writeJobs.notify();
        }
    }

    static GsonBuilder gsonBuilder = new GsonBuilder();

    {
        gsonBuilder.registerTypeAdapter(MessageID.class, new MessageIDAdapter());
    }

    public static Gson getGson() {
        return gsonBuilder.create();
    }

    public synchronized byte[] getStoredUserpic(final String uid) {
        try {
            Cursor cursor = db.rawQuery("select * from userpic where uid=?", new String[]{uid});
            byte[] body = null;
            try {
                boolean exists = cursor.moveToFirst();
                if (!exists) return null;
                body = cursor.getBlob(cursor.getColumnIndex("body"));
            } finally {
                cursor.close();
            }
            return body;
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        }
    }

    public void storeUserpic(final String uid, final byte[] body) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put("uid", uid);
                        cv.put("body", body);
                        cv.put("save_date", System.currentTimeMillis());
                        long l = db.insert("userpic", null, cv);
                        if (l != -1) {
                            db.setTransactionSuccessful();
                        }
                    } catch (Exception e) {
                        System.out.println("oh");
                        // duplicate key
                    }
                    return true;
                }
            });
            writeJobs.notify();
        }
    }

    public void addLocalCensorWord(final int censorCategoryId, final String token) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    // create table censor_blacklist(censor_category_id integer not null, checkpoint integer, token text not null, status text, moderation_result text)
                    ContentValues cv = new ContentValues();
                    cv.put("censor_category_id", censorCategoryId);
                    cv.put("token", token);
                    db.insert("censor_blacklist", null, cv);   // failed uniq constraint is handled here.
                    db.setTransactionSuccessful();
                    return Boolean.TRUE;
                }
            });
            writeJobs.notify();
        }
    }

    public static class CensorCategory {
        int id;
        String name;

        public CensorCategory(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public ArrayList<CensorCategory> getCensorCategories() {
        boolean russian = Locale.getDefault().toString().toUpperCase().contains("RU");
        Cursor cursor = db.rawQuery("select * from censor_category order by id", new String[]{});
        ArrayList<CensorCategory> retval = new ArrayList<CensorCategory>();
        try {
            cursor.moveToFirst();
            while(!cursor.isAfterLast()) {
                retval.add(new CensorCategory(
                        cursor.getInt(cursor.getColumnIndex("id")),
                        cursor.getString(cursor.getColumnIndex(russian ? "description_ru" : "description_en")
                        )));
                cursor.moveToNext();
            }
            return retval;
        } finally {
            cursor.close();
        }
    }

    public void runGenericWriteJob(final Utils.Function<Boolean, DatabaseService> job) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    return job.apply(DatabaseService.this);
                }
            });
            writeJobs.notify();
        }
    }

    /**
     * remove from user's "Saved messages list"
     *
     * @param message
     */
    public void unsaveMessage(final JuickMessage message) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    try {
                        db.execSQL("delete from saved_message2 where msgid=?", new Object[]{message.getMID().toString()});
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Boolean.TRUE;
                }
            });
            writeJobs.notify();
        }
    }


    /**
     * get list of user "Saved" messages
     *
     * @param afterSavedDate
     * @return
     */
    public ArrayList<JuickMessage> getSavedMessages(long afterSavedDate) {
        Cursor cursor = db.rawQuery("select * from saved_message2 where save_date < ? order by save_date desc limit 20", new String[]{"" + afterSavedDate});
        return convertFromSavedMessages(cursor);

    }

    /**
     * @param cursor
     * @return
     */
    private ArrayList<JuickMessage> convertFromSavedMessages(Cursor cursor) {
        ArrayList<JuickMessage> retval = new ArrayList<JuickMessage>();
        cursor.moveToFirst();
        int blobIndex = cursor.getColumnIndex("body");
        int saveDateIndex = cursor.getColumnIndex("save_date");
        MessageIDAdapter tmp = new MessageIDAdapter();
        while (!cursor.isAfterLast()) {
            byte[] blob = cursor.getBlob(blobIndex);
            String str = decompressGZIP(blob);
            Gson gson = getGson();
            if (str != null && gson != null) {
                JsonObject jsonObject = (JsonObject) gson.fromJson(str, JsonElement.class);
                MessageID mid = tmp.deserialize(jsonObject.get("MID"), null, null);
                JuickMessage msg = MainActivity.getMicroBlog(mid.getMicroBlogCode()).createMessage();
                JuickMessage mesg = gson.fromJson(jsonObject, msg.getClass());
                if (mesg != null) {
                    mesg.User.UName = mesg.User.UName.trim();   // bug i am lazy to hunt on (CR unneeded in json)
                    mesg.messageSaveDate = cursor.getLong(saveDateIndex);
                    retval.add(mesg);
                }
            }
            cursor.moveToNext();
        }
        cursor.close();
        return retval;
    }

    public void reportFeature(String feature_name, String feature_value) {
        ContentValues cv = new ContentValues();
        cv.put("feature_name", feature_name);
        cv.put("feature_value", feature_value);
        db.insert("feature_usage", null, cv);
        db.setTransactionSuccessful();
    }

    public void cleanupUsageData() {
        db.beginTransaction();
        db.delete("feature_usage", "1=1", new String[]{});
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public static void rememberVisited(JuickMessage message) {
        //To change body of created methods use File | Settings | File Templates.
    }

    public void storeThread(final MessageID mid, final ArrayList<String> raw) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    if (raw.size() == 0) return true;        // broken?
                    Cursor cursor = db.rawQuery("select * from msg2 where mid=?", new String[]{mid.toString()});
                    boolean exists = cursor.moveToFirst();
                    cursor.close();
                    try {
                        insertOrUpdateThread(exists, raw, mid);
                        db.setTransactionSuccessful();
                        return true;
                    } catch (IOException e) {
                        Log.e("com.juickadvanced", "while storeThread", e);
                        // failure
                    }
                    return false;
                }
            });
            writeJobs.notify();
        }
        //To change body of created methods use File | Settings | File Templates.
    }

    private void insertOrUpdateThread(boolean exists, ArrayList<String> raw, MessageID mid) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(raw);
        oos.flush();
        byte[] blob = compressGZIPArr(baos.toByteArray());
        ContentValues cv = new ContentValues();
        cv.put("body", blob);
        cv.put("nreplies", raw.size());
        cv.put("save_date", System.currentTimeMillis());
        if (exists) {
            db.update("msg2", cv, "mid=?", new String[]{mid.toString()});
        } else {
            cv.put("mid", mid.toString());
            db.insert("msg2", "", cv);
        }
    }

    public void appendToStoredThread(final MessageID mid, final String raw) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    ArrayList<String> storedThread = getStoredThread(mid);
                    if (storedThread == null) {
                        storedThread = new ArrayList<String>();
                    } else {
                        int myrid = -1;
                        int lastrid = -1;
                        try {
                            JSONObject jsonObject = new JSONObject(raw);
                            if (jsonObject.has("rid")) {
                                myrid = jsonObject.getInt("rid");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        try {
                            if (storedThread.size() > 0) {
                                JSONObject jsonObject = new JSONObject(storedThread.get(storedThread.size() - 1));
                                if (jsonObject.has("rid")) {
                                    lastrid = jsonObject.getInt("rid");
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        if (myrid != -1 && lastrid != -1 && myrid <= lastrid) {
                            // already there.
                            db.setTransactionSuccessful();
                            return true;
                        }
                    }
                    storedThread.add(raw);
                    try {
                        insertOrUpdateThread(true, storedThread, mid);
                    } catch (IOException e) {
                        // bad luck
                        return true;
                    }
                    db.setTransactionSuccessful();
                    return true;
                }
            });
            writeJobs.notify();
        }
    }


    public ArrayList<String> getStoredThread(MessageID mid) {
        Cursor cursor = db.rawQuery("select * from msg2 where mid=?", new String[]{mid.toString()});
        try {
            cursor.moveToFirst();
            int blobIndex = cursor.getColumnIndex("body");
            if (!cursor.isAfterLast()) {
                byte[] blob = cursor.getBlob(blobIndex);
                blob = decompressGZIPArr(blob);
                try {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob));
                    try {
                        return (ArrayList<String>) ois.readObject();
                    } finally {
                        ois.close();
                    }
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    public void maybeSaveMessages(ArrayList<JuickMessage> messages) {
        boolean messageDB = sp.getBoolean("enableMessageDB", false);
        if (messageDB) {
            for (JuickMessage message : messages) {
                if (message.getMID() instanceof JuickMessageID) {
                    JuickMessageID mid = (JuickMessageID) message.getMID();
                    JsonObject obj = JuickMicroBlog.convertJuickMessageToJSON(message);
                    appendToStoredThread(message.getMID(), new Gson().toJson(obj));
                }
            }
        }
    }


    public static class DB extends SQLiteOpenHelper {

        public final static int CURRENT_VERSION = 15;

        public DB(Context context) {
            super(context, "messages_db", null, CURRENT_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL("create table message(msgid integer not null primary key, tm integer not null, prevmsgid integer not null, nextmsgid integer not null, body blob not null)");
            sqLiteDatabase.execSQL("create table message_reply(msgid integer not null, rid integer not null, body blob not null)");
            sqLiteDatabase.execSQL("create table message_read(msgid integer not null primary key, tm integer not null, nreplies integer not null)");
            sqLiteDatabase.execSQL("create index if not exists ix_message_date on message (tm)");
            sqLiteDatabase.execSQL("create index if not exists ix_message_prev on message (prevmsgid)");
            sqLiteDatabase.execSQL("create index if not exists ix_message_next on message (nextmsgid)");
            onUpgrade(sqLiteDatabase, 1, CURRENT_VERSION);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int from, int to) {
            if (from == 1) {
                from++;
                //
            }
            if (from == 2) {
                // NEVER DROP!
                sqLiteDatabase.execSQL("create table saved_message(msgid integer not null primary key, tm integer not null, body blob not null, save_date integer not null)");
                sqLiteDatabase.execSQL("create index if not exists ix_savedmessage_savedate on saved_message (save_date)");
                from++;
            }
            if (from == 3) {
                sqLiteDatabase.execSQL("alter table message_read add column message_date integer");
                sqLiteDatabase.execSQL("update message_read set message_date=tm");
                from++;
            }
            if (from == 4) {
                sqLiteDatabase.execSQL("create table feature_usage(feature_name text not null, feature_value text not null)");
                from++;
            }
            if (from == 5) {
                sqLiteDatabase.execSQL("create table last_visited_threads(msgid integer not null primary key, visit_date integer not null)");
                from++;
            }
            if (from == 6) {
                try {
                    sqLiteDatabase.execSQL("drop table message");
                    sqLiteDatabase.execSQL("drop table message_reply");
                } catch (SQLException e) {
                    //  no luck
                }
                // msg.blob contains serialized ArrayList<String> with messages in Strings
                // to speed up append
                sqLiteDatabase.execSQL("create table msg(mid integer, save_date integer not null, nreplies integer, body blob not null)");
                sqLiteDatabase.execSQL("create index ixmsg_mid  on msg(mid)");
                sqLiteDatabase.execSQL("create index ixmsg_savedate on msg(save_date)");
                from++;
            }
            if (from == 7) {
                sqLiteDatabase.execSQL("create table userpic(uid text, save_date integer not null, body blob not null)");
                sqLiteDatabase.execSQL("create unique index userpic_uid  on userpic(uid)");
                sqLiteDatabase.execSQL("create index userpic_savedate on userpic(save_date)");
                from++;
            }
            if (from == 8) {
                sqLiteDatabase.execSQL("drop table userpic");
                sqLiteDatabase.execSQL("create table userpic(uid text, save_date integer not null, body blob not null)");
                sqLiteDatabase.execSQL("create unique index userpic_uid  on userpic(uid)");
                sqLiteDatabase.execSQL("create index userpic_savedate on userpic(save_date)");
                from++;
            }
            if (from == 9) {
                sqLiteDatabase.execSQL("drop table message_read");
                sqLiteDatabase.execSQL("create table message_read(msgid text not null primary key, tm integer not null, nreplies integer not null, message_date integer)");
                from++;
            }
            if (from == 10) {
                sqLiteDatabase.execSQL("create table msg2(mid text, save_date integer not null, nreplies integer, body blob not null)");
                sqLiteDatabase.execSQL("insert into msg2 select * from msg");
                sqLiteDatabase.execSQL("drop table msg");
                sqLiteDatabase.execSQL("create index ixmsg2_mid  on msg2(mid)");
                sqLiteDatabase.execSQL("create index ixmsg2_savedate on msg2(save_date)");
                sqLiteDatabase.execSQL("create table saved_message2(msgid text not null primary key, tm integer not null, body blob not null, save_date integer not null)");
                sqLiteDatabase.execSQL("create index if not exists ix_savedmessage_savedate on saved_message2(save_date)");
                sqLiteDatabase.execSQL("insert into saved_message2 select * from saved_message");
                sqLiteDatabase.execSQL("drop table saved_message");
                from++;
            }
            if (from == 11) {
                sqLiteDatabase.execSQL("create table recent_threads_wrote(msgid text not null primary key, body blob not null, save_date integer not null)");
                sqLiteDatabase.execSQL("create table recent_threads_opened(msgid text not null primary key, body blob not null, save_date integer not null)");
                from++;
            }
            if (from == 12) {
                sqLiteDatabase.execSQL("alter table message_read add checkpoint integer");
                sqLiteDatabase.execSQL("alter table saved_message2 add checkpoint integer");
                from++;
            }
            if (from == 13) {
                sqLiteDatabase.execSQL("create table censor_blacklist(level integer not null, token text not null)");
                from++;
            }
            if (from == 14) {
                /*
                db.censor_category.save({id:1, n:1, description_ru:'Сильный мат',description_en:'Strong offensive'});
                db.censor_category.save({id:2, n:1, description_ru:'Грязные слова',description_en:'Dirty words'});
                db.censor_category.save({id:3, n:1, description_ru:'Мелкая вонючесть',description_en:'Little stinky words'});
                db.censor_category.save({id:4, n:1, description_ru:'Оскорбления ватников',description_en:'Offences for russian patriots'});
                db.censor_category.save({id:5, n:1, description_ru:'Оскорбления пгм-ных',description_en:'Offences for believers'});
                db.censor_category.save({id:6, n:1, description_ru:'Боль здравомыслящих атеистов',description_en:"Pain of atheist's mind"});
                db.censor_category.save({id:7, n:1, description_ru:'(эксперимент) Негативные эмоции',description_en:'(experiment) Negative emotions'});
                 */
                sqLiteDatabase.execSQL("drop table censor_blacklist");
                sqLiteDatabase.execSQL("create table censor_category(id integer not null primary key, checkpoint integer, description_ru text, description_en text)");
                sqLiteDatabase.execSQL("create table censor_blacklist(censor_category_id integer not null, checkpoint integer, token text not null, status text, moderation_result text)");
                sqLiteDatabase.execSQL("create table censor_global_blacklist(censor_category_id integer not null, checkpoint integer, token text not null, status text, replacement_regex text)");
                from++;
            }
        }


    }

    DB database;

    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<DatabaseService>(this);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public static class ReadMarker {
        MessageID mid;
        long messageDate;
        int nreplies;

        ReadMarker(MessageID mid, int nreplies, long messageDate) {
            this.mid = mid;
            this.nreplies = nreplies;
            this.messageDate = messageDate;
        }
    }

    public ArrayList<Utils.Function<Boolean, Void>> writeJobs = new ArrayList<Utils.Function<Boolean, Void>>();
    Thread writerThread;
    Thread syncerThread;
    static SQLiteDatabase db;

    public static class MessageReadStatus {
        public MessageID messageId;
        public boolean read;
        public int nreplies;
    }

    @Override
    public void onCreate() {
        synchronized (DatabaseService.class) {
            super.onCreate();
            sp = PreferenceManager.getDefaultSharedPreferences(this);
            handler = new Handler();
            database = new DB(this);
            if (db == null)
                db = database.getWritableDatabase();
            writerThread = new WriterThread();
            writerThread.start();
            syncerThread = new SyncThread(this);
            syncerThread.start();
            synchronized (writeJobs) {
                writeJobs.add(new Utils.Function<Boolean, Void>() {
                    @Override
                    public Boolean apply(Void aVoid) {
                        int messageDBperiod = 30;
                        try {
                            messageDBperiod = Integer.parseInt(sp.getString("messageDBperiod", "30"));
                        } catch (Exception e) {
                            //
                        }
                        String oldDate = "" + (System.currentTimeMillis() - messageDBperiod * 24 * 60 * 60 * 1000L);
                        db.delete("msg2", "save_date < ?", new String[]{oldDate});

                        db.execSQL("delete from userpic where save_date < ?", new String[]{""+(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)});

                        db.setTransactionSuccessful();
                        return Boolean.TRUE;
                    }
                });
                writeJobs.notify();
            }
        }
    }

    @Override
    public void onDestroy() {
        synchronized (writeJobs) {
            writerThread.interrupt();
            syncerThread.interrupt();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    static byte[] compressGZIP(String json) {
        try {
            byte[] bytes = json.getBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzos = new GZIPOutputStream(baos);
            gzos.write(bytes);
            gzos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] compressGZIPArr(byte[] bytes) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzos = new GZIPOutputStream(baos);
            gzos.write(bytes);
            gzos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String decompressGZIP(byte[] gzipped) {
        try {
            GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] arr = new byte[1024];
            while (true) {
                int rd = gzis.read(arr);
                if (rd < 1) break;
                baos.write(arr, 0, rd);
            }
            return baos.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] decompressGZIPArr(byte[] gzipped) {
        try {
            GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] arr = new byte[1024];
            while (true) {
                int rd = gzis.read(arr);
                if (rd < 1) break;
                baos.write(arr, 0, rd);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public MessageReadStatus getMessageReadStatus0(MessageID messageId) {
        Cursor cursor = db.rawQuery("select * from message_read where msgid=?", new String[]{messageId.toString()});
        MessageReadStatus mrs = new MessageReadStatus();
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            mrs.read = true;
            int nreplies = cursor.getInt(cursor.getColumnIndex("nreplies"));
            mrs.nreplies = nreplies;
        }
        cursor.close();
        mrs.messageId = messageId;
        return mrs;
    }

    private long getMessageDate(MessageID messageId) {
        Cursor cursor = db.rawQuery("select message_date from message_read where msgid=?", new String[]{messageId.toString()});
        if (cursor.moveToFirst()) {
            long tm = cursor.getLong(cursor.getColumnIndex("message_date"));
            cursor.close();
            return tm;
        } else {
            cursor.close();
            return -1;
        }
    }

    long lastDBReport = 0;

    public void markAsRead(final ReadMarker marker) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    ReadMarker readMarker = marker;
                    Cursor cursor = db.rawQuery("select * from message_read where msgid=?", new String[]{readMarker.mid.toString()});
                    if (cursor.getCount() == 0) {
                        cursor.close();
                        ContentValues contentValues = new ContentValues();
                        contentValues.put("msgid", readMarker.mid.toString());
                        contentValues.put("tm", System.currentTimeMillis());
                        contentValues.put("nreplies", readMarker.nreplies);
                        contentValues.put("message_date", readMarker.messageDate);
                        if (-1 == db.insert("message_read", null, contentValues)) {
                            throw new SQLException("Insert into message_read failed");
                        }
                    } else {
                        cursor.moveToFirst();
                        int oldNreplies = cursor.getInt(cursor.getColumnIndex("nreplies"));
                        cursor.close();
                        if (oldNreplies != readMarker.nreplies) {
                            db.execSQL("update message_read set nreplies=?, checkpoint=null where msgid=?",
                                    new Object[]{readMarker.nreplies, readMarker.mid.toString()});
                        }
                    }
                    db.setTransactionSuccessful();
                    return true;
                }
            });
            writeJobs.notify();
        }
    }

    AtomicLong lastModify = new AtomicLong(System.currentTimeMillis());

    long lastSyncIn = -1;


    public Utils.RESTResponse executeServerSyncQuery(String db, int sinceCheckpoint) {
        String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(DatabaseService.this);
        String juickPassword = JuickAPIAuthorizer.getPassword(this);
        ArrayList<Utils.NameValuePair> nvs = new ArrayList<Utils.NameValuePair>();
        Utils.RESTResponse restResponse = Utils.postForm(this,
                "http://" + Utils.JA_ADDRESS + "/api/syncdb?command=get_since_checkpoint&db=" + db + "&checkpoint=" + sinceCheckpoint + "&login=" + juickAccountName + "&password=" + juickPassword,
                //"https://" + Utils.JA_ADDRESS_HTTPS + "/api/syncdb?command=get_since_checkpoint&db=" + db + "&checkpoint=" + sinceCheckpoint + "&login=" + juickAccountName + "&password=" + juickPassword,
                nvs);
        return restResponse;
    }

    public static Utils.RESTResponse obtainSharingURL(Context context, boolean reset) {
        String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(context);
        String juickPassword = JuickAPIAuthorizer.getPassword(context);
        ArrayList<Utils.NameValuePair> nvs = new ArrayList<Utils.NameValuePair>();
        Utils.RESTResponse restResponse = Utils.postForm(context,
                Utils.JA_ADDRESS_HTTPS + "/api/share_saved?reset="+reset+"&login=" + juickAccountName + "&password=" + juickPassword,
                nvs);
        return restResponse;
    }

    Utils.RESTResponse executeServerSyncCommit(String db, String json) {
        String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(DatabaseService.this);
        String juickPassword = JuickAPIAuthorizer.getPassword(this);
        ArrayList<Utils.NameValuePair> nvs = new ArrayList<Utils.NameValuePair>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            GZIPOutputStream gzos = new GZIPOutputStream(baos);
            gzos.write(json.getBytes());
            gzos.close();
        } catch (IOException e) {
            return new Utils.RESTResponse(e.toString(), false, null);
        }
        nvs.add(new Utils.NameStreamValuePair("data", new ByteArrayInputStream(baos.toByteArray())));
        JuickAdvancedApplication.addToGlobalLog("syncdb: commit: db="+db+" len="+baos.size(), null);
        Utils.RESTResponse restResponse = Utils.postForm(this,
                Utils.JA_ADDRESS_HTTPS + "/api/syncdb?command=commit&db=" + db + "&login=" + juickAccountName + "&password=" + juickPassword,
                nvs);
        if (restResponse.getErrorText() != null) {
            JuickAdvancedApplication.addToGlobalLog("syncdb: commit: "+restResponse.getErrorText(), null);
            restResponse.mayRetry = true;
            return restResponse;
        }
        String result = restResponse.getResult();
        try {
            JSONObject jsonObject = new JSONObject(result);
            String revision = jsonObject.get("revision").toString();
            JuickAdvancedApplication.addToGlobalLog("syncdb: commit OK: "+revision, null);
            return new Utils.RESTResponse(null, false, revision);
        } catch (Exception ex) {
            return new Utils.RESTResponse(ex.toString(), false, null);
        }
    }


    private class WriterThread extends Thread {
        @Override
        public void run() {
            setName("DatabaseService.writer");
            setPriority(MIN_PRIORITY);

            while (true) {
                ArrayList<Utils.Function<Boolean, Void>> jobs = new ArrayList<Utils.Function<Boolean, Void>>();
                synchronized (writeJobs) {
                    try {
                        if (writeJobs.size() == 0)
                            writeJobs.wait();
                        jobs.addAll(writeJobs);
                        writeJobs.clear();
                    } catch (InterruptedException e) {
                        writeJobs = null;
                        return;
                    }
                }
                for (Utils.Function<Boolean, Void> job : jobs) {
                    try {
                        db.beginTransaction();
                        if (!job.apply(null)) {
                            throw new SQLException("Job failed");
                        }
                        db.endTransaction();
                    } catch (final IllegalStateException e) {
                        // database closed
                        // bad luck.
                        break;
                    } catch (final SQLException e) {
                        db.endTransaction();
                        JuickAdvancedApplication.addToGlobalLog("writerThread", e);
                        job.retryCount++;
                        if (job.retryCount > 3) {
                            // lost!
                        } else {
                            synchronized (writeJobs) {
                                writeJobs.add(job);
                            }
                        }
                        reportDBError("Saving read jobs: " + e.toString());
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }
                if (jobs.size() > 0) {
                    synchronized (lastModify) {
                        lastModify.set(System.currentTimeMillis());
                        lastModify.notify();
                    }
                }
            }
        }
    }

    public static class Period implements Serializable {
        boolean read;
        Date startDate;
        Date endDate;

        // for read messages
        public int startMid;
        int endMid;
        int numberOfMessages;

        // for unread
        int beforeMid;

        @Override
        public String toString() {
            return "Period{" +
                    "startDate=" + startDate +
                    ", endDate=" + endDate +
                    ", read=" + read +
                    '}';
        }

        public double getHours() {
            return ((startDate.getTime() - endDate.getTime()) / 1000) / (60 * 60.0);
        }
    }

    public ArrayList<Period> getJuickPeriods(int days) {
        ArrayList<Period> retval = new ArrayList<Period>();
        Cursor cursor = db.rawQuery("select min(msgid), max(msgid) from message_read where tm > ? and msgid like 'jui-%'", new String[]{"" + (System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L)});
        cursor.moveToFirst();
        if (cursor.isNull(1)) {
            cursor.close();
            return retval;
        }
        JuickMessageID bottomMsgid = JuickMessageID.fromString(cursor.getString(0));
        JuickMessageID newestMsgid = JuickMessageID.fromString(cursor.getString(1));
        cursor.close();
        cursor = db.rawQuery("select * from message_read where msgid > ? and msgid like 'jui-%' order by msgid desc", new String[]{"" + bottomMsgid});
        cursor.moveToFirst();
        int msgidIndex = cursor.getColumnIndex("msgid");
        int messageDateIndex = cursor.getColumnIndex("message_date");
        JuickMessageID savedMsgid = newestMsgid;
        long savedMsgDate = getMessageDate(savedMsgid);
        while (!cursor.isAfterLast()) {
            JuickMessageID thisMid = JuickMessageID.fromString(cursor.getString(msgidIndex));
            long thisMessageDate = cursor.getLong(messageDateIndex);
            if (thisMessageDate > 200) {    // bug hider :-E
                if (savedMsgid != null && Math.abs(thisMid.getMid() - savedMsgid.getMid()) > 50) {   // UNREAD HOLE
                    Period period = new Period();
                    period.startMid = savedMsgid.getMid() - 1;
                    period.beforeMid = savedMsgid.getMid() - 1;
                    period.startDate = new Date(savedMsgDate);
                    period.endMid = thisMid.getMid() + 1;
                    period.endDate = new Date(thisMessageDate);
                    period.read = false;
                    if (Math.abs(period.getHours()) > days * 24) {
                        // bug hider
                    } else {
                        // ok
                        retval.add(period);
                    }
                }
                savedMsgid = thisMid;
                savedMsgDate = thisMessageDate;
            }
            cursor.moveToNext();
        }
        // coalesce periods
        for (int i = 0; i < retval.size() - 1; i++) {
            Period curr = retval.get(i);
            Period next = retval.get(i + 1);
            if (Math.abs(next.startMid - curr.endMid) < 6) { // removing single reads
                curr.endMid = next.endMid;
                curr.endDate = next.endDate;
                retval.remove(i + 1);
                i--;
            }
        }
        cursor.close();
        return retval;
    }

    public final static long DBREPORT_PERIOD_MSEC = 2 * 60 * 1000L;     // 2 minutes

    private void reportDBError(final String errmsg) {
        if (System.currentTimeMillis() - lastDBReport < DBREPORT_PERIOD_MSEC) return;
        lastDBReport = System.currentTimeMillis();
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DatabaseService.this, errmsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public JsonObject prepareUsageReportValue() {
        JsonObject jo = new JsonObject();
        copyBoolean(jo, sp, "useXMPP", false);
        copyBoolean(jo, sp, "useXMPPOnlyForBL", false);
        copyBoolean(jo, sp, "persistLastMessagesPosition", false);
        copyBoolean(jo, sp, "lastReadMessages", false);
        copyBoolean(jo, sp, "showNumbers", false);
        copyBoolean(jo, sp, "showUserpics", true);
        copyBoolean(jo, sp, "enableMessageDB", false);
        copyBoolean(jo, sp, "confirmActions", true);
        copyBoolean(jo, sp, "enableScaleByGesture", true);
        copyBoolean(jo, sp, "compressedMenu", false);
        copyBoolean(jo, sp, "singleLineMenu", false);
        copyBoolean(jo, sp, "prefetchMessages", false);
        copyBoolean(jo, sp, "dialogMessageMenu", false);
        copyBoolean(jo, sp, "web_for_subscriptions", false);
        copyBoolean(jo, sp, "web_for_myblog", false);
        copyBoolean(jo, sp, "wrapUserpics", true);
        copyBoolean(jo, sp, "compactComments", false);
        copyBoolean(jo, sp, "feedlyFonts", false);
        copyBoolean(jo, sp, "helvNueFonts", false);
        copyBoolean(jo, sp, "fullScreenMessages", false);
        copyBoolean(jo, sp, "fullScreenThread", false);
        copyBoolean(jo, sp, "enableDrafts", false);
        copyBoolean(jo, sp, "turnOffButtons", false);
        copyBoolean(jo, sp, "googlePlusNavigation", false);
        copyString(jo, sp, "messagesFontScale", "1.0");
        copyInteger(jo, sp, "Colors.COMMON_BACKGROUND", -1);
        copyString(jo, sp, "locationAccuracy", "ACCURACY_FINE");
        copyString(jo, sp, "menuFontScale", "1.0");
        copyString(jo, sp, "useBackupServer", "-1");
        copyString(jo, sp, "juickBotOn", "skip");
        copyString(jo, sp, "image.loadMode", "off");
        copyString(jo, sp, "image.height_percent", "0.3");
        copyString(jo, sp, "keyScrollMode", "page");
        copyBoolean(jo, sp, "imageproxy.skipOnWifi", false);
        copyBoolean(jo, sp, "image.hide_gif", false);
        copyBoolean(jo, sp, "ringtone_enabled", true);
        copyBoolean(jo, sp, "vibration_enabled", true);
        copyBoolean(jo, sp, "led_enabled", true);
        copyBoolean(jo, sp, "current_vibration_enabled", true);
        copyBoolean(jo, sp, "image.indirect", true);

        copyBoolean(jo, sp, "msrcTopMessages", true);
        copyBoolean(jo, sp, "msrcWithPhotos", true);
        copyBoolean(jo, sp, "msrcMyBlog", false);
        copyBoolean(jo, sp, "msrcSrachiki", false);
        copyBoolean(jo, sp, "msrcUnread", false);
        copyBoolean(jo, sp, "msrcUnanswered", false);
        copyBoolean(jo, sp, "msrcSaved", false);
        copyBoolean(jo, sp, "msrcPrivate", false);
        copyBoolean(jo, sp, "msrcDiscuss", false);
        copyBoolean(jo, sp, "msrcRecentOpen", false);
        copyBoolean(jo, sp, "msrcRecentComment", false);

        jo.addProperty("manufacturer", Build.MANUFACTURER);
        jo.addProperty("model", Build.MODEL);
        jo.addProperty("brand", Build.BRAND);
        jo.addProperty("display", Build.DISPLAY);
        jo.addProperty("display_width", MainActivity.displayWidth);
        jo.addProperty("display_height", MainActivity.displayHeight);
        String uniqueId = getUniqueInstallationId(this, "");
        jo.addProperty("device_install_id", uniqueId);
        try {
            jo.addProperty("ja_version", "" + getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            jo.addProperty("ja_version", "unknown");
        }

        WhatsNew.collectUsage(this, jo);

        return jo;
    }

    public static String getUniqueInstallationId(Context ctx, String salt) {
        String uniqueId = "";
        WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            String wifiMac = wifiManager.getConnectionInfo().getMacAddress();
            uniqueId += wifiMac;
        }
        String androidID = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidID != null)
            uniqueId += androidID;

        if (salt.length() > 0) {
            // salt = '' for submitting usage reports. Leaving it compatible to keep statistics.
            // Today, for sessionId for extxmpp (uses salt != ''), i decided
            // to add more randomness when I saw that "UNKNOWN_ID" below...
            String buildProp = XMPPService.readFile(new File("/system/build.prop"));
            if (buildProp != null) {
                uniqueId += buildProp;
            }
        }
        if (uniqueId.length() == 0) {
            uniqueId = "UNKNOWN_ID";
        } else {
            uniqueId = Utils.getMD5DigestForString(uniqueId + salt);
        }
        uniqueId += "__";


        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(ctx.getPackageName(), 0);
            String appFile = appInfo.sourceDir;
            long installed = new File(appFile).lastModified(); //Epoch Time
            uniqueId += "" + installed;
        } catch (PackageManager.NameNotFoundException e) {
        }
        return uniqueId;
    }

    private void copyBoolean(JsonObject jo, SharedPreferences sp, String prefname, boolean dflt) {
        boolean value = sp.getBoolean(prefname, dflt);
        jo.addProperty(prefname.replace('.', '_'), value);
    }

    private void copyString(JsonObject jo, SharedPreferences sp, String prefname, String dflt) {
        String value = sp.getString(prefname, dflt);
        jo.addProperty(prefname.replace('.', '_'), value);
    }

    private void copyInteger(JsonObject jo, SharedPreferences sp, String prefname, int dflt) {
        int value = sp.getInt(prefname, dflt);
        jo.addProperty(prefname.replace('.', '_'), "" + value);
    }

    public void deleteCensorBlacklist(final int level) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    try {
                        db.delete("censor_blacklist", "level = ?", new String[]{String.valueOf(level)});
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Boolean.TRUE;
                }
            });
            writeJobs.notify();
        }
    }

    public void saveCensorBlacklist(final int level, final List<String> blacklist) {
        if ((blacklist == null) || (blacklist.isEmpty())) {
            return; // nothing to save to DB
        }
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    try {
                        for (String token : blacklist) {
                            ContentValues cv = new ContentValues();
                            cv.put("level", level);
                            cv.put("token", token);
                            db.insert("censor_blacklist", null, cv);
                        }
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    return Boolean.TRUE;
                }
            });
            writeJobs.notify();
        }
    }

    public List<String> getCensorBlacklist(final int level) {
        List<String> retval = new ArrayList<String>();
        try {
            Cursor cursor = db.rawQuery("select token from censor_blacklist where level = ?",
                    new String[]{String.valueOf(level)});
            try {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    retval.add(cursor.getString(0));
                    cursor.moveToNext();
                }
                return retval;
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        }
    }
}
