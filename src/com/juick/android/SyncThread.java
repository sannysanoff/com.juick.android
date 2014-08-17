package com.juick.android;

import android.content.ContentValues;
import android.database.Cursor;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juickadvanced.RESTResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
* Created with IntelliJ IDEA.
* User: san
* Date: 11/28/13
* Time: 12:03 PM
* To change this template use File | Settings | File Templates.
*/ // push/pull syncable changes to/from the server
public class SyncThread extends Thread {

    private DatabaseService databaseService;

    public SyncThread(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void run() {
        setName("DB SyncerThread");
        setPriority(MIN_PRIORITY);
        long waitDelay = 5000;
        while (true) {
            synchronized (databaseService.lastModify) {
                try {
                    if (waitDelay <= 0) {
                        databaseService.lastModify.wait(30 * 60 * 1000);    // half an hour
                    } else {
                        databaseService.lastModify.wait(waitDelay);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
            if (DatabaseService.db == null) {
                continue;
            }
            if (!databaseService.sp.getBoolean("enableSynchronization", false)) {
                waitDelay = -1;
                continue;
            }
            if (databaseService.lastModify.get() > System.currentTimeMillis() - 5000 && waitDelay > 0) {
                continue;
            }
            String juickAccountName = JuickAPIAuthorizer.getJuickAccountName(databaseService);
            if (juickAccountName == null || juickAccountName.length() == 0) continue;
            if (waitDelay > 0) {
                waitDelay = -1;

                {
                    //
                    //
                    // changed something in db message_read ?
                    //
                    //
                    Cursor cursor = DatabaseService.db.rawQuery("select * from message_read where checkpoint is null", new String[0]);
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    int midIndex = cursor.getColumnIndex("msgid");
                    int tmIndex = cursor.getColumnIndex("tm");
                    int nRepliesIndex = cursor.getColumnIndex("nreplies");
                    final ArrayList<String> updateds = new ArrayList<String>();
                    int count = 0;
                    while (cursor.moveToNext()) {
                        try {
                            JSONObject jo = new JSONObject();
                            //midIndex integer not null primary key, tm integer not null, nreplies integer not null
                            String key = cursor.getString(midIndex);
                            jo.put("k", key);
                            updateds.add(key);
                            JSONObject v = new JSONObject();
                            v.put("nr", cursor.getInt(nRepliesIndex));
                            v.put("tm", cursor.getLong(tmIndex));
                            jo.put("v", v);
                            if (sb.length() > 1) {
                                sb.append(",");
                            }
                            sb.append(jo.toString());
                            count++;
                            if (count >= 1000) {
                                break;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                    cursor.close();
                    if (count > 0) {
                        // have some lastread markers to sync
                        sb.append("]");
                        final RESTResponse checkpoint = databaseService.executeServerSyncCommit("last_reads", sb.toString());
                        if (checkpoint.getErrorText() != null) {
                            waitDelay = -1; // maybe later
                            continue;
                        }
                        synchronized (databaseService.writeJobs) {
                            databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                @Override
                                public Boolean apply(Void aVoid) {
                                    for (String updated : updateds) {
                                        ContentValues cv = new ContentValues();
                                        cv.put("checkpoint", checkpoint.getResult());
                                        DatabaseService.db.update("message_read", cv, "msgid=?", new String[]{updated});
                                    }
                                    DatabaseService.db.setTransactionSuccessful();
                                    return Boolean.TRUE;
                                }
                            });
                            databaseService.writeJobs.notify();
                        }
                        if (count >= 1000) {
                            waitDelay = 10000;
                            continue;
                        }
                    }
                }

                {
                    //
                    //
                    // changed something in db saved_messages ?
                    //
                    //
                    Cursor cursor = DatabaseService.db.rawQuery("select * from saved_message2 where checkpoint is null", new String[0]);
                    // msgid text not null primary key, tm integer not null, body blob not null, save_date integer not null
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    int midIndex = cursor.getColumnIndex("msgid");
                    int tmIndex = cursor.getColumnIndex("tm");
                    int bodyIndex = cursor.getColumnIndex("body");
                    int saveDateIndex = cursor.getColumnIndex("save_date");
                    final ArrayList<String> updateds = new ArrayList<String>();
                    int count = 0;
                    while (cursor.moveToNext()) {
                        try {
                            JSONObject jo = new JSONObject();
                            //midIndex integer not null primary key, tm integer not null, nreplies integer not null
                            String key = cursor.getString(midIndex);
                            jo.put("k", key);
                            updateds.add(key);
                            JSONObject v = new JSONObject();
                            v.put("tm", cursor.getLong(tmIndex));
                            v.put("sd", cursor.getLong(saveDateIndex));
                            v.put("b", new JSONObject(DatabaseService.decompressGZIP(cursor.getBlob(bodyIndex))));
                            jo.put("v", v);
                            if (sb.length() > 1) {
                                sb.append(",");
                            }
                            sb.append(jo.toString());
                            count++;
                            if (count >= 200) {
                                break;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                    cursor.close();
                    if (count > 0) {
                        // have some lastread markers to sync
                        sb.append("]");
                        final RESTResponse checkpoint = databaseService.executeServerSyncCommit("saved_messages", sb.toString());
                        if (checkpoint.getErrorText() != null) {
                            waitDelay = -1; // maybe later
                            continue;
                        }
                        synchronized (databaseService.writeJobs) {
                            databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                @Override
                                public Boolean apply(Void aVoid) {
                                    for (String updated : updateds) {
                                        ContentValues cv = new ContentValues();
                                        cv.put("checkpoint", checkpoint.getResult());
                                        DatabaseService.db.update("saved_message2", cv, "msgid=?", new String[]{updated});
                                    }
                                    DatabaseService.db.setTransactionSuccessful();
                                    return Boolean.TRUE;
                                }
                            });
                            databaseService.writeJobs.notify();
                        }
                        if (count >= 200) {
                            waitDelay = 10000;
                            continue;
                        }
                    }

                }

                {
                    //
                    //
                    // changed something in (local) censor_blacklist ?
                    //
                    //
                    Cursor cursor = DatabaseService.db.rawQuery("select * from censor_blacklist where checkpoint is null", new String[0]);
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");

                    int categoryIdIndex = cursor.getColumnIndex("censor_category_id");
                    int tokenIndex = cursor.getColumnIndex("token");
                    int statusIndex = cursor.getColumnIndex("status");
                    int moderationResultIndex = cursor.getColumnIndex("moderation_result");
                    final ArrayList<String> updateds = new ArrayList<String>();
                    int count = 0;
                    while (cursor.moveToNext()) {
                        try {
                            JSONObject jo = new JSONObject();
                            //midIndex integer not null primary key, tm integer not null, nreplies integer not null
                            String key = cursor.getString(tokenIndex);
                            jo.put("k", key);
                            updateds.add(key);
                            JSONObject v = new JSONObject();
                            v.put("s", cursor.getString(statusIndex));
                            v.put("mr", cursor.getString(moderationResultIndex));
                            v.put("ci", cursor.getLong(categoryIdIndex));
                            jo.put("v", v);
                            if (sb.length() > 1) {
                                sb.append(",");
                            }
                            sb.append(jo.toString());
                            count++;
                            if (count >= 200) {
                                break;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                    cursor.close();
                    if (count > 0) {
                        // have some lastread markers to sync
                        sb.append("]");
                        final RESTResponse checkpoint = databaseService.executeServerSyncCommit("censor_local", sb.toString());
                        if (checkpoint.getErrorText() != null) {
                            waitDelay = -1; // maybe later
                            continue;
                        }
                        synchronized (databaseService.writeJobs) {
                            databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                @Override
                                public Boolean apply(Void aVoid) {
                                    for (String updated : updateds) {
                                        ContentValues cv = new ContentValues();
                                        cv.put("checkpoint", checkpoint.getResult());
                                        DatabaseService.db.update("censor_blacklist", cv, "token=?", new String[]{updated});
                                    }
                                    DatabaseService.db.setTransactionSuccessful();
                                    return Boolean.TRUE;
                                }
                            });
                            databaseService.writeJobs.notify();
                        }
                        if (count >= 200) {
                            waitDelay = 10000;
                            continue;
                        }
                    }

                }

                if (System.currentTimeMillis() > databaseService.lastSyncIn + 30 * 60 * 1000) {
                    {
                        //
                        //
                        // maybe something new in saved_message2 on server?
                        //
                        //
                        Cursor cursor = DatabaseService.db.rawQuery("select max(checkpoint) from saved_message2", new String[0]);
                        cursor.moveToNext();
                        int lastCheckpoint = cursor.getInt(0);  // 0 is ok.
                        cursor.close();
                        RESTResponse somethingToSync = databaseService.executeServerSyncQuery("saved_messages", lastCheckpoint);
                        if (somethingToSync.getErrorText() != null) {
                            JuickAdvancedApplication.addToGlobalLog("sync: "+somethingToSync.getErrorText(), null);
                            waitDelay = -1; // maybe later
                            continue;
                        } else {
                            try {
                                byte[] bytes = somethingToSync.getResult().getBytes("ISO-8859-1");
                                String json = DatabaseService.decompressGZIP(bytes);
                                final JSONArray jo = new JSONArray(json);
                                if (jo.length() > 0) {

                                    synchronized (databaseService.writeJobs) {
                                        databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                            @Override
                                            public Boolean apply(Void aVoid) {
                                                try {
                                                    for(int i=0; i<jo.length(); i++) {
                                                        JSONObject row = (JSONObject)jo.get(i);
                                                        ContentValues cv = new ContentValues();
                                                        String msgid = row.getString("k");
                                                        JSONObject v = row.getJSONObject("v");
                                                        cv.put("checkpoint", row.getInt("n"));


                                                        cv.put("tm", v.getLong("tm"));
                                                        cv.put("save_date", v.getLong("sd"));
                                                        cv.put("body", DatabaseService.compressGZIP(v.getJSONObject("b").toString()));

                                                        if (0 == DatabaseService.db.update("saved_message2", cv, "msgid=?", new String[]{msgid})) {
                                                            cv.put("msgid", msgid);
                                                            DatabaseService.db.insert("saved_message2", null, cv);
                                                        }
                                                    }
                                                    DatabaseService.db.setTransactionSuccessful();
                                                } catch (JSONException e) {
                                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.

                                                }
                                                return Boolean.TRUE;
                                            }
                                        });
                                        databaseService.writeJobs.notify();
                                    }
                                }

                            } catch (Exception e) {
                                waitDelay = -1;
                                continue;
                            }
                        }

                    }

                    {
                        //
                        //
                        // maybe something new in message_read on server?
                        //
                        //
                        Cursor cursor = DatabaseService.db.rawQuery("select max(checkpoint) from message_read", new String[0]);
                        cursor.moveToNext();
                        int lastCheckpoint = cursor.getInt(0);  // 0 is ok.
                        cursor.close();
                        RESTResponse somethingToSync = databaseService.executeServerSyncQuery("last_reads", lastCheckpoint);
                        if (somethingToSync.getErrorText() != null) {
                            waitDelay = -1; // maybe later
                            continue;
                        } else {
                            try {
                                byte[] bytes = somethingToSync.getResult().getBytes("ISO-8859-1");
                                String json = DatabaseService.decompressGZIP(bytes);
                                final JSONArray jo = new JSONArray(json);
                                if (jo.length() > 0) {

                                    synchronized (databaseService.writeJobs) {
                                        databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                            @Override
                                            public Boolean apply(Void aVoid) {
                                                try {
                                                    for(int i=0; i<jo.length(); i++) {
                                                        JSONObject row = (JSONObject)jo.get(i);
                                                        ContentValues cv = new ContentValues();
                                                        String msgid = row.getString("k");
                                                        JSONObject v = row.getJSONObject("v");
                                                        cv.put("checkpoint", row.getInt("n"));
                                                        cv.put("tm", v.getLong("tm"));
                                                        cv.put("nreplies", v.getInt("nr"));

                                                        if (0 == DatabaseService.db.update("message_read", cv, "msgid=?", new String[]{msgid})) {
                                                            cv.put("msgid", msgid);
                                                            DatabaseService.db.insert("message_read", null, cv);
                                                        }
                                                    }
                                                    DatabaseService.db.setTransactionSuccessful();
                                                } catch (JSONException e) {
                                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.

                                                }
                                                return Boolean.TRUE;
                                            }
                                        });
                                        databaseService.writeJobs.notify();
                                    }
                                }

                            } catch (Exception e) {
                                waitDelay = -1;
                                continue;
                            }
                        }

                    }


                    {
                        //
                        //
                        // maybe something new in censor_category on server?
                        //
                        //
                        Cursor cursor = DatabaseService.db.rawQuery("select max(checkpoint) from censor_category", new String[0]);
                        cursor.moveToNext();
                        int lastCheckpoint = cursor.getInt(0);  // 0 is ok.
                        cursor.close();
                        RESTResponse somethingToSync = databaseService.executeServerSyncQuery("censor_category", lastCheckpoint);
                        if (somethingToSync.getErrorText() != null) {
                            waitDelay = -1; // maybe later
                            continue;
                        } else {
                            try {
                                byte[] bytes = somethingToSync.getResult().getBytes("ISO-8859-1");
                                String json = DatabaseService.decompressGZIP(bytes);
                                final JSONArray jo = new JSONArray(json);
                                if (jo.length() > 0) {
                                    synchronized (databaseService.writeJobs) {
                                        databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                            @Override
                                            public Boolean apply(Void aVoid) {
                                                try {
                                                    for(int i=0; i<jo.length(); i++) {
                                                        JSONObject row = (JSONObject)jo.get(i);
                                                        ContentValues cv = new ContentValues();
                                                        String id = ""+row.getInt("id");
                                                        cv.put("description_ru", row.getString("description_ru"));
                                                        cv.put("description_en", row.getString("description_ru"));
                                                        cv.put("checkpoint", ""+row.getInt("n"));

                                                        if (0 == DatabaseService.db.update("censor_category", cv, "id=?", new String[]{id})) {
                                                            cv.put("id", id);
                                                            DatabaseService.db.insert("censor_category", null, cv);
                                                        }
                                                    }
                                                    DatabaseService.db.setTransactionSuccessful();
                                                } catch (JSONException e) {
                                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.

                                                }
                                                return Boolean.TRUE;
                                            }
                                        });
                                        databaseService.writeJobs.notify();
                                    }
                                }

                            } catch (Exception e) {
                                waitDelay = -1;
                                continue;
                            }
                        }

                    }


                    {
                        //
                        //
                        // maybe something new in censor_global on server?
                        //
                        //
                        Cursor cursor = DatabaseService.db.rawQuery("select max(checkpoint) from censor_global_blacklist", new String[0]);
                        cursor.moveToNext();
                        int lastCheckpoint = cursor.getInt(0);  // 0 is ok.
                        cursor.close();
                        RESTResponse somethingToSync = databaseService.executeServerSyncQuery("censor_global", lastCheckpoint);
                        if (somethingToSync.getErrorText() != null) {
                            waitDelay = -1; // maybe later
                            continue;
                        } else {
                            try {
                                byte[] bytes = somethingToSync.getResult().getBytes("ISO-8859-1");
                                String json = DatabaseService.decompressGZIP(bytes);
                                final JSONArray jo = new JSONArray(json);
                                if (jo.length() > 0) {
                                    synchronized (databaseService.writeJobs) {
                                        databaseService.writeJobs.add(new Utils.Function<Boolean, Void>() {
                                            @Override
                                            public Boolean apply(Void aVoid) {
                                                try {
                                                    for(int i=0; i<jo.length(); i++) {
                                                        JSONObject row = (JSONObject)jo.get(i);
                                                        ContentValues cv = new ContentValues();
                                                        String token = row.getString("t");
                                                        JSONObject v = row.getJSONObject("v");
                                                        cv.put("checkpoint", ""+row.getInt("n"));
                                                        cv.put("censor_category_id", ""+v.getInt("ci"));
                                                        cv.put("status", ""+v.getString("s"));
                                                        cv.put("replacement_regex", v.getString("rgx"));

                                                        if (0 == DatabaseService.db.update("censor_global_blacklist", cv, "token=?", new String[]{token})) {
                                                            cv.put("token", token);
                                                            DatabaseService.db.insert("censor_global_blacklist", null, cv);
                                                        }
                                                    }
                                                    DatabaseService.db.setTransactionSuccessful();
                                                } catch (JSONException e) {
                                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.

                                                }
                                                return Boolean.TRUE;
                                            }
                                        });
                                        databaseService.writeJobs.notify();
                                    }
                                }

                            } catch (Exception e) {
                                waitDelay = -1;
                                continue;
                            }
                        }

                    }


                    databaseService.lastSyncIn = System.currentTimeMillis();
                }
            } else {
                waitDelay = 5000;   // coalesce sequential changes
            }

        }
    }
}
