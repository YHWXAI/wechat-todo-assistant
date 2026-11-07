package com.wechattodo.assistant;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

final class TaskStore extends SQLiteOpenHelper {
    static final String CHANGED = "com.wechattodo.assistant.TASKS_CHANGED";
    private static final long REPEAT_WINDOW = 2 * 60_000L;
    private static final long EVENT_RETENTION = 2 * 24 * 60 * 60_000L;
    private static final String[] COLUMNS = {"_id", "source", "body", "due", "done", "reminded"};
    private static final String PENDING = "due IS NOT NULL AND done = 0 AND reminded = 0";
    private static TaskStore instance;

    record Task(long id, String source, String body, Long due, boolean done, boolean reminded) {}

    static synchronized TaskStore get(Context context) {
        if (instance == null) {
            instance = new TaskStore(context.getApplicationContext());
        }
        return instance;
    }

    private TaskStore(Context context) {
        super(context, "tasks.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "source TEXT NOT NULL, body TEXT NOT NULL, due INTEGER, "
                + "done INTEGER NOT NULL DEFAULT 0, reminded INTEGER NOT NULL DEFAULT 0)");
        database.execSQL("CREATE TABLE seen (fingerprint TEXT PRIMARY KEY, seen_at INTEGER NOT NULL)");
        createChatHistory(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int previous, int current) {
        if (previous < 2) {
            createChatHistory(database);
        } else if (previous < 3) {
            database.execSQL("ALTER TABLE chat_seen ADD COLUMN accepted INTEGER NOT NULL DEFAULT 1");
        }
    }

    private static void createChatHistory(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE chat_seen (fingerprint TEXT PRIMARY KEY, "
                + "seen_at INTEGER NOT NULL, accepted INTEGER NOT NULL DEFAULT 1)");
    }

    String nextChatCandidate(VisibleWeChatChat chat) {
        SQLiteDatabase database = getReadableDatabase();
        for (String body : chat.messages()) {
            String key = fingerprint(chat.title(), body, 0);
            try (Cursor seen = database.query("chat_seen", new String[]{"fingerprint"},
                    "fingerprint = ?", new String[]{key}, null, null, null, "1")) {
                if (!seen.moveToFirst() && !alreadyInChat(database, chat.title(), body)) {
                    return body;
                }
            }
        }
        return null;
    }

    boolean resolveChatCandidate(String chat, String body, boolean accepted, ZonedDateTime now) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues seen = new ContentValues();
            seen.put("fingerprint", fingerprint(chat, body, 0));
            seen.put("seen_at", now.toInstant().toEpochMilli());
            seen.put("accepted", accepted ? 1 : 0);
            long id = database.insertWithOnConflict("chat_seen", null, seen, SQLiteDatabase.CONFLICT_IGNORE);
            boolean added = id != -1 && accepted && !alreadyInChat(database, chat, body);
            if (added) {
                insertTask(database, chat, body, TaskRules.dueFromChat(body, now));
            }
            database.setTransactionSuccessful();
            return added;
        } finally {
            database.endTransaction();
        }
    }

    Task add(String source, String body, Long due, long messageTime) {
        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(source, body, messageTime);
        long window = messageTime > 0 ? EVENT_RETENTION : REPEAT_WINDOW;
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            if (recentlyReadInChat(database, source, body, now)) {
                Task updated = due == null ? null : fillChatDeadline(database, source, body, due);
                database.setTransactionSuccessful();
                return updated;
            }
            database.delete("seen", "seen_at < ?", args(now - EVENT_RETENTION));
            try (Cursor seen = database.query("seen", new String[]{"seen_at"},
                    "fingerprint = ? AND seen_at >= ?",
                    new String[]{fingerprint, Long.toString(now - window)}, null, null, null)) {
                if (seen.moveToFirst()) {
                    database.setTransactionSuccessful();
                    return null;
                }
            }
            ContentValues seen = new ContentValues();
            seen.put("fingerprint", fingerprint);
            seen.put("seen_at", now);
            database.insertWithOnConflict("seen", null, seen, SQLiteDatabase.CONFLICT_REPLACE);

            long id = insertTask(database, source, body, due);
            database.setTransactionSuccessful();
            return new Task(id, source, body, due, false, false);
        } finally {
            database.endTransaction();
        }
    }

    private static long insertTask(SQLiteDatabase database, String source, String body, Long due) {
        ContentValues values = new ContentValues();
        values.put("source", source);
        values.put("body", body);
        values.put("due", due);
        return database.insertOrThrow("tasks", null, values);
    }

    private boolean recentlyReadInChat(SQLiteDatabase database, String source, String body, long now) {
        try (Cursor cursor = database.query("chat_seen", new String[]{"fingerprint"},
                "fingerprint IN (?, ?) AND seen_at >= ? AND accepted = 1",
                new String[]{fingerprint(source, body, 0), fingerprint(chatName(source), body, 0),
                        Long.toString(now - REPEAT_WINDOW)}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    private Task fillChatDeadline(SQLiteDatabase database, String source, String body, long due) {
        try (Cursor cursor = database.query("tasks", COLUMNS,
                "body = ? AND (source = ? OR source = ?) AND due IS NULL AND done = 0",
                new String[]{body, source, chatName(source)}, null, null, "_id DESC", "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Task task = read(cursor);
            ContentValues values = new ContentValues();
            values.put("due", due);
            database.update("tasks", values, "_id = ?", args(task.id()));
            return new Task(task.id(), task.source(), task.body(), due, task.done(), task.reminded());
        }
    }

    private static String chatName(String source) {
        int separator = source.lastIndexOf(" · ");
        return separator < 0 ? source : source.substring(0, separator);
    }

    private boolean alreadyInChat(SQLiteDatabase database, String source, String body) {
        String senderPrefix = source + " · ";
        try (Cursor cursor = database.query("tasks", new String[]{"_id"},
                "body = ? AND (source = ? OR substr(source, 1, length(?)) = ?)",
                new String[]{body, source, senderPrefix, senderPrefix}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    Task nextReminder() {
        try (Cursor cursor = getReadableDatabase().query("tasks", COLUMNS, PENDING, null,
                null, null, "due", "1")) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    List<Task> list() {
        return query(null, "done, due IS NULL, due, _id DESC");
    }

    List<Task> pendingReminders() {
        return query(PENDING, "due");
    }

    private List<Task> query(String selection, String order) {
        List<Task> tasks = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tasks", COLUMNS, selection, null,
                null, null, order)) {
            while (cursor.moveToNext()) {
                tasks.add(read(cursor));
            }
        }
        return tasks;
    }

    void complete(long id) {
        setFlag(id, "done");
    }

    void markReminded(long id) {
        setFlag(id, "reminded");
    }

    private void setFlag(long id, String column) {
        ContentValues values = new ContentValues();
        values.put(column, 1);
        getWritableDatabase().update("tasks", values, "_id = ?", args(id));
    }

    void delete(long id) {
        getWritableDatabase().delete("tasks", "_id = ?", args(id));
    }

    static void signalChange(Context context) {
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }

    private static Task read(Cursor cursor) {
        return new Task(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                cursor.isNull(3) ? null : cursor.getLong(3), cursor.getInt(4) != 0,
                cursor.getInt(5) != 0);
    }

    private static String[] args(long value) {
        return new String[]{Long.toString(value)};
    }

    private static String fingerprint(String source, String body, long messageTime) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String input = source + "\n" + messageTime + "\n" + body;
            byte[] bytes = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
