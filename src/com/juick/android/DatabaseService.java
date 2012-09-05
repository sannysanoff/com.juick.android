package com.juick.android;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;
import com.juick.android.api.JuickMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
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

    public static class DB extends SQLiteOpenHelper {

        public final static int CURRENT_VERSION = 1;

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
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int from, int to) {

        }


    }

    DB database;

    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<DatabaseService>(this);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public static class ReadMarker {
        int mid;
        int nreplies;

        ReadMarker(int mid, int nreplies) {
            this.mid = mid;
            this.nreplies = nreplies;
        }
    }

    public ArrayList<Utils.Function<Boolean, Void>> writeJobs = new ArrayList<Utils.Function<Boolean,Void>>();
    Thread writerThread;
    SQLiteDatabase db;

    public static class MessageReadStatus {
        public int messageId;
        public boolean read;
        public int nreplies;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        database = new DB(this);
        db = database.getWritableDatabase();
        writerThread = new WriterThread();
        writerThread.start();
    }

    @Override
    public void onDestroy() {
        synchronized (writeJobs) {
            writerThread.interrupt();
        }
        db.releaseReference();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public void storeMessage(final JuickMessage parsed, final String json) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    if (parsed.RID > 0) {
                        Cursor cursor = db.rawQuery("select * from message_reply where msgid=? and rid=?", new String[]{"" + parsed.MID, ""+parsed.RID});
                        if (cursor.getCount() == 0) {
                            db.execSQL("insert into message_reply (msgid, rid, body) values(?,?,?)", new Object[] {parsed.MID, parsed.RID, compressGZIP(json)});
                        }
                        cursor.close();
                    } else {
                        Cursor cursor = db.rawQuery("select * from message where msgid=?", new String[]{"" + parsed.MID});
                        int msgCount = cursor.getCount();
                        cursor.close();
                        Cursor cursor2 = db.rawQuery("select * from message where prevmsgid=?", new String[]{"" + parsed.MID});
                        int nextmsgid = -1;
                        if (cursor2.getCount() > 0) {
                            cursor2.moveToFirst();
                            nextmsgid = cursor2.getInt(cursor2.getColumnIndex("msgid"));
                        }
                        cursor2.close();
                        if (msgCount == 0) {
                            ContentValues cv = new ContentValues();
                            cv.put("msgid", parsed.MID);
                            cv.put("tm", parsed.Timestamp.getTime());
                            cv.put("prevmsgid", parsed.previousMID);
                            if (Math.abs(parsed.previousMID - parsed.MID) > 30) {
                                cv = cv;
                            }
                            cv.put("nextmsgid", nextmsgid);
                            cv.put("body", compressGZIP(json));
                            if (-1 == db.insert("message", null, cv)) {
                                throw new SQLException("Insert into table MESSAGE filed");
                            }
                        } else {
                            if (parsed.previousMID != -1) {
                                db.execSQL("update message set prevmsgid=? where msgid=?",
                                        new Object[] {parsed.previousMID, parsed.MID});
                            }
                        }
                        if (parsed.previousMID != -1) {
                            db.execSQL("update message set nextmsgid=? where msgid=?",
                                    new Object[] {parsed.MID, parsed.previousMID});
                        }
                    }
                    db.setTransactionSuccessful();
                    return true;
                }
            });
            writeJobs.notify();
        }
    }

    private byte[] compressGZIP(String json) {
        try {
            byte[] bytes = json.getBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzos = new GZIPOutputStream(baos);
            gzos.write(bytes);
            gzos.finish();
            gzos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    ArrayList<MessageReadStatus> cachedMRS = new ArrayList<MessageReadStatus>();

    public void getMessageReadStatus(final int messageId, final Utils.Function<Void,MessageReadStatus> callback) {
        synchronized (cachedMRS) {
            for (MessageReadStatus messageReadStatus : cachedMRS) {
                if (messageReadStatus.messageId == messageId) {
                    callback.apply(messageReadStatus);
                    return;
                }
            }
        }
        new Thread("getMessageReadStatus: "+messageId) {
            @Override
            public void run() {
                MessageReadStatus mrs = getMessageReadStatus0(messageId);
                synchronized (cachedMRS) {
                    if (cachedMRS.size() > 10) {
                        cachedMRS.remove(0);
                    }
                    cachedMRS.add(mrs);
                }
                callback.apply(mrs);
                super.run();    //To change body of overridden methods use File | Settings | File Templates.
            }
        }.start();
    }

    private MessageReadStatus getMessageReadStatus0(int messageId) {
        Cursor cursor = db.rawQuery("select * from message_read where msgid=?", new String[]{"" + messageId});
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

    class QuickMessageInfo {
        long tm;
        int mid;

        QuickMessageInfo(int mid, long tm) {
            this.mid = mid;
            this.tm = tm;
        }
    }

    private QuickMessageInfo getMessageExistStatus0(int messageId) {
        Cursor cursor = db.rawQuery("select * from message where msgid=?", new String[]{"" + messageId});
        if (cursor.moveToFirst()) {
            int msgid = cursor.getInt(cursor.getColumnIndex("msgid"));
            long tm = cursor.getLong(cursor.getColumnIndex("tm"));
            cursor.close();
            return new QuickMessageInfo(msgid, tm);
        } else {
            cursor.close();
            return null;
        }
    }

    long lastDBReport = 0;
    public void markAsRead(final ReadMarker marker) {
        synchronized (writeJobs) {
            writeJobs.add(new Utils.Function<Boolean, Void>() {
                @Override
                public Boolean apply(Void aVoid) {
                    synchronized (cachedMRS) {
                        for (MessageReadStatus messageReadStatus : cachedMRS) {
                            if (messageReadStatus.messageId == marker.mid) {
                                messageReadStatus.read = true;
                                break;
                            }
                        }
                    }
                    ReadMarker readMarker = marker;
                    Cursor cursor = db.rawQuery("select * from message_read where msgid=?", new String[]{"" + readMarker.mid});
                    if (cursor.getCount() == 0) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.put("msgid", readMarker.mid);
                        contentValues.put("tm", System.currentTimeMillis());
                        contentValues.put("nreplies", readMarker.nreplies);
                        if (-1 == db.insert("message_read", null, contentValues)) {
                            throw new SQLException("Insert into message_read failed");
                        }
                    } else {
                        cursor.moveToFirst();
                        int oldNreplies = cursor.getInt(cursor.getColumnIndex("nreplies"));
                        if (oldNreplies != readMarker.nreplies) {
                            db.execSQL("update message_read set nreplies=? where msgid=?",
                                    new Object[]{readMarker.nreplies, readMarker.mid});
                        }
                    }
                    db.setTransactionSuccessful();
                    return true;
                }
            });
            writeJobs.notify();
        }
    }



    private class WriterThread extends Thread {
        @Override
        public void run() {
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
                    } catch (final SQLException e) {
                        synchronized (writeJobs) {
                            writeJobs.add(job);
                        }
                        reportDBError("Saving read jobs: " + e.toString());
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
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
    }

    class MessageLink {
        int id;
        int prev;
        int next;

        @Override
        public String toString() {
            return "MessageLink{" +
                    "id=" + id +
                    ", prev=" + prev +
                    ", next=" + next +
                    '}';
        }

        MessageLink(int id, int prev, int next) {
            this.id = id;
            this.prev = prev;
            this.next = next;
        }
    }

    public ArrayList<Period> getPeriods(int days) {
        ArrayList<Period> retval = new ArrayList<Period>();
        Cursor cursor = db.rawQuery("select min(msgid), max(msgid) from message where tm > ?", new String[]{"" + (System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L)});
        cursor.moveToFirst();
        if (cursor.isNull(1)) {
            cursor.close();
            return retval;
        }
        int bottomMsgid = cursor.getInt(0);
        int newestMsgid = cursor.getInt(1);
        cursor.close();
        cursor = db.rawQuery("select * from message_read where msgid > ? order by msgid desc", new String[]{"" + bottomMsgid});
        cursor.moveToFirst();
        int msgidIndex = cursor.getColumnIndex("msgid");
        int savedMsgid = newestMsgid;
        while(!cursor.isAfterLast()) {
            int thisMid = cursor.getInt(msgidIndex);
            if (savedMsgid != -1 && Math.abs(thisMid - savedMsgid) > 50) {   // UNREAD HOLE
                Period period = new Period();
                QuickMessageInfo savedMsgStatus = getMessageExistStatus0(savedMsgid);
                period.startMid = savedMsgid-1;
                period.beforeMid = savedMsgid-1;
                period.startDate = new Date(savedMsgStatus.tm);
                QuickMessageInfo thisMsgStatus = getMessageExistStatus0(thisMid);
                period.endMid =thisMid+1;
                period.endDate = new Date(thisMsgStatus.tm);
                period.read = false;
                retval.add(period);
                period = null;
            }
            savedMsgid = thisMid;
            cursor.moveToNext();
        }
        // coalesce periods
        for (int i = 0; i < retval.size() - 1; i++) {
            Period curr = retval.get(i);
            Period next = retval.get(i+1);
            if (Math.abs(next.startMid - curr.endMid) < 6) { // removing single reads
                curr.endMid = next.endMid;
                curr.endDate = next.endDate;
                retval.remove(i+1);
                i--;
            }
        }
        cursor.close();
        return retval;
    }

    public ArrayList<Period> getPeriodsBad(int days) {
        ArrayList<Period> retval = new ArrayList<Period>();
        Cursor cursor = db.rawQuery("select * from message where tm > ? order by tm desc", new String[]{"" + (System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L)});
        cursor.moveToFirst();
        int tmIndex = cursor.getColumnIndex("tm");
        int prevMsgIdIndex = cursor.getColumnIndex("prevmsgid");
        int nextMsgIdIndex = cursor.getColumnIndex("nextmsgid");
        int msgidIndex = cursor.getColumnIndex("msgid");
        Period period = new Period();
        ArrayList<MessageLink> links = new ArrayList<MessageLink>();
        int previousMID = -1;
        while(!cursor.isAfterLast()) {
            int prevMsgId = cursor.getInt(prevMsgIdIndex);
            int nextMsgId = cursor.getInt(nextMsgIdIndex);
            if (prevMsgId == -1 && nextMsgId == -1) continue; // random nonlinear message
            int thisMid = cursor.getInt(msgidIndex);
            links.add(new MessageLink(thisMid, prevMsgId, nextMsgId));
            Date thisDate = new Date(cursor.getLong(tmIndex));
            if (period.startDate == null) {
                period.startDate = thisDate;
                period.startMid = thisMid;
                period.read = true;
                period.endMid = period.startMid;
                period.endDate = period.startDate;
            }
            if (prevMsgId == -1 && period.startMid != thisMid || previousMID != -1 && Math.abs(previousMID - thisMid) > 10) {
                // next period start; message without prev
                retval.add(period);
                Period prevPeriod = period;
                period = new Period();
                period.read = false;
                period.beforeMid = prevPeriod.endMid;
                period.startDate = prevPeriod.endDate;
                period.endDate = thisDate;
                retval.add(period);
                period = new Period();
            } else {
                period.endMid = thisMid;
                period.endDate = thisDate;
                period.numberOfMessages++;
            }
            previousMID = thisMid;
            cursor.moveToNext();
        }
        // coalesce periods
        for (int i = 1; i < retval.size() - 1; i++) {
            Period period1 = retval.get(i);
            if (period1.read && period1.numberOfMessages < 10) {
                retval.remove(i);
                Period nextEmpty = retval.remove(i);
                Period previous = retval.get(i-1);
                previous.endDate = nextEmpty.endDate;
                i--;
            }
        }
        for (int i = 0; i < retval.size() - 1; i++) {
            Period period1 = retval.get(i);
            if (period1.read) {
                Period nextEmpty = retval.get(i+1);
                int rdMessages = 0;
                for(int m=period1.startMid; m >= period1.endMid; m--) {
                    QuickMessageInfo messageExistStatus0 = getMessageExistStatus0(m);
                    if (messageExistStatus0 != null) {
                        if (getMessageReadStatus0(m).read) {
                            rdMessages++;
                            continue;
                        } else {
                            nextEmpty.beforeMid = m+1;
                            nextEmpty.startDate = new Date(messageExistStatus0.tm);
                            period1.numberOfMessages = rdMessages;
                            period1.endMid = m + 1;
                            period1.endDate = nextEmpty.startDate;
                        }
                    }
                }
            }
        }
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


}
