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
import java.util.Objects;

final class TaskStore extends SQLiteOpenHelper {
    static final String CHANGED = "com.wechattodo.assistant.TASKS_CHANGED";
    static final String MANUAL_SOURCE = "手动添加";
    private static final long REPEAT_WINDOW = 2 * 60_000L;
    private static final long EVENT_RETENTION = 2 * 24 * 60 * 60_000L;
    private static final long FORMAT_MATCH_WINDOW = 12_000L;
    private static final String[] COLUMNS = {"_id", "source", "body", "due", "done", "reminded", "manual_category",
            "app_package", "sender", "original_body"};
    private static final String PENDING = "due IS NOT NULL AND done = 0 AND reminded = 0";
    private static TaskStore instance;

    record Origin(String packageName, String sender) {}

    record Task(long id, String source, String body, Long due, boolean done, boolean reminded,
                TaskCategory manualCategory, Origin origin, String originalBody) {
        TaskCategory category() {
            return manualCategory == null ? TaskCategory.classify(body, source) : manualCategory;
        }
    }

    static synchronized TaskStore get(Context context) {
        if (instance == null) {
            instance = new TaskStore(context.getApplicationContext());
        }
        return instance;
    }

    private TaskStore(Context context) {
        super(context, "tasks.db", null, 6);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "source TEXT NOT NULL, body TEXT NOT NULL, due INTEGER, "
                + "done INTEGER NOT NULL DEFAULT 0, reminded INTEGER NOT NULL DEFAULT 0, manual_category TEXT, "
                + "app_package TEXT, sender TEXT, original_body TEXT)");
        database.execSQL("CREATE TABLE seen (fingerprint TEXT PRIMARY KEY, seen_at INTEGER NOT NULL)");
        createChatHistory(database);
        createNotificationHistory(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int previous, int current) {
        if (previous < 2) {
            createChatHistory(database);
        } else if (previous < 3) {
            database.execSQL("ALTER TABLE chat_seen ADD COLUMN accepted INTEGER NOT NULL DEFAULT 1");
        }
        if (previous < 4 && current >= 4) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN manual_category TEXT");
        }
        if (previous < 5 && current >= 5) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN app_package TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN sender TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN original_body TEXT");
        }
        if (previous < 6 && current >= 6) createNotificationHistory(database);
    }

    private static void createChatHistory(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE chat_seen (fingerprint TEXT PRIMARY KEY, "
                + "seen_at INTEGER NOT NULL, accepted INTEGER NOT NULL DEFAULT 1)");
    }

    private static void createNotificationHistory(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE notification_seen (fingerprint TEXT PRIMARY KEY, "
                + "conversation_body TEXT NOT NULL, observed_at INTEGER NOT NULL, seen_at INTEGER NOT NULL, "
                + "structured INTEGER NOT NULL)");
        database.execSQL("CREATE INDEX notification_seen_lookup ON notification_seen "
                + "(conversation_body, structured, observed_at)");
    }

    String nextChatCandidate(VisibleWeChatChat chat) {
        SQLiteDatabase database = getReadableDatabase();
        for (String body : chat.messages()) {
            String key = fingerprint(chat.title(), body, 0);
            String original = chat.original(body);
            String originalKey = original.equals(body) ? key : fingerprint(chat.title(), original, 0);
            try (Cursor seen = database.query("chat_seen", new String[]{"fingerprint"},
                    "fingerprint IN (?, ?)", new String[]{key, originalKey}, null, null, null, "1")) {
                if (!seen.moveToFirst() && !alreadyInChat(database, chat.title(), body)
                        && (original.equals(body) || !alreadyInChat(database, chat.title(), original))) {
                    return body;
                }
            }
        }
        return null;
    }

    boolean resolveChatCandidate(String chat, String body, boolean accepted, ZonedDateTime now) {
        return resolveChatCandidate(chat, body, accepted, now, null);
    }

    boolean resolveChatCandidate(String chat, String body, boolean accepted, ZonedDateTime now, String sender) {
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
                insertTask(database, chat, body, TaskRules.dueFromChat(body, now), null,
                        new Origin(MonitoringRules.App.WECHAT.packageName, sender));
            }
            database.setTransactionSuccessful();
            return added;
        } finally {
            database.endTransaction();
        }
    }

    Task add(String source, String body, Long due, long messageTime) {
        return add(source, body, body, due, messageTime);
    }

    Task add(String source, String original, String body, Long due, long messageTime) {
        return add(source, original, body, due, messageTime, null);
    }

    Task add(String source, String original, String body, Long due, long messageTime, Origin origin) {
        return add(source, original, body, due, messageTime, origin, 0);
    }

    Task add(String source, String original, String body, Long due, long messageTime, Origin origin, long observedAt) {
        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(source, original, messageTime);
        long window = messageTime > 0 ? EVENT_RETENTION : REPEAT_WINDOW;
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            String readBody = recentlyReadInChat(database, source, body, now) ? body
                    : !original.equals(body) && recentlyReadInChat(database, source, original, now) ? original : null;
            if (readBody != null) {
                Task updated = mergeChatNotification(database, source, readBody, due, origin);
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

            if (observedAt > 0 && origin != null && origin.sender() != null && !origin.sender().isBlank()) {
                String conversation = origin.packageName() + "\n" + source + "\n" + origin.sender();
                if (matchesOtherFormat(database, fingerprint, fingerprint(conversation, original, 0),
                        messageTime > 0, observedAt, now)) {
                    database.setTransactionSuccessful();
                    return null;
                }
            }
            long id = insertTask(database, source, body, due, null, origin);
            database.setTransactionSuccessful();
            return new Task(id, source, body, due, false, false, null, origin, null);
        } finally {
            database.endTransaction();
        }
    }

    private static boolean matchesOtherFormat(SQLiteDatabase database, String eventKey, String conversationBody,
                                              boolean structured, long observedAt, long now) {
        database.delete("notification_seen", "seen_at < ?", args(now - REPEAT_WINDOW));
        String closest = null;
        long distance = FORMAT_MATCH_WINDOW + 1;
        try (Cursor rows = database.query("notification_seen", new String[]{"fingerprint", "observed_at"},
                "conversation_body = ? AND structured = ? AND observed_at BETWEEN ? AND ?",
                new String[]{conversationBody, structured ? "0" : "1",
                        Long.toString(observedAt - FORMAT_MATCH_WINDOW), Long.toString(observedAt + FORMAT_MATCH_WINDOW)},
                null, null, null)) {
            while (rows.moveToNext()) {
                long gap = Math.abs(rows.getLong(1) - observedAt);
                if (gap >= distance) continue;
                closest = rows.getString(0);
                distance = gap;
            }
        }
        if (closest != null) {
            // Pair each observation once. A second timestamped message can have exactly the same text.
            database.delete("notification_seen", "fingerprint = ?", new String[]{closest});
            return true;
        }
        ContentValues pending = new ContentValues();
        pending.put("fingerprint", eventKey);
        pending.put("conversation_body", conversationBody);
        pending.put("observed_at", observedAt);
        pending.put("seen_at", now);
        pending.put("structured", structured ? 1 : 0);
        database.insertWithOnConflict("notification_seen", null, pending, SQLiteDatabase.CONFLICT_REPLACE);
        return false;
    }

    void addManual(String body, Long due, TaskCategory category) {
        String text = body.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Empty task");
        insertTask(getWritableDatabase(), MANUAL_SOURCE, text, due, category, null);
    }

    private static long insertTask(SQLiteDatabase database, String source, String body, Long due,
                                   TaskCategory category, Origin origin) {
        ContentValues values = new ContentValues();
        values.put("source", source);
        values.put("body", body);
        values.put("due", due);
        if (category != null) values.put("manual_category", category.name());
        if (origin != null) {
            values.put("app_package", origin.packageName());
            values.put("sender", origin.sender());
        }
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

    private Task mergeChatNotification(SQLiteDatabase database, String source, String body, Long due, Origin origin) {
        try (Cursor cursor = database.query("tasks", COLUMNS,
                "(body = ? OR original_body = ?) AND (source = ? OR source = ?)",
                new String[]{body, body, source, chatName(source)}, null, null, "_id DESC", "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Task task = read(cursor);
            ContentValues values = new ContentValues();
            Long deadline = task.due();
            if (due != null && deadline == null && task.originalBody() == null && !task.done()) {
                deadline = due;
                values.put("due", deadline);
            }
            Origin current = task.origin();
            if (origin != null && (current == null || current.sender() == null && origin.sender() != null)) {
                current = origin;
                values.put("app_package", current.packageName());
                values.put("sender", current.sender());
            }
            if (values.size() == 0) return null;
            database.update("tasks", values, "_id = ?", args(task.id()));
            return new Task(task.id(), task.source(), task.body(), deadline, task.done(), task.reminded(),
                    task.manualCategory(), current, task.originalBody());
        }
    }

    private static String chatName(String source) {
        int separator = source.lastIndexOf(" · ");
        return separator < 0 ? source : source.substring(0, separator);
    }

    private boolean alreadyInChat(SQLiteDatabase database, String source, String body) {
        String senderPrefix = source + " · ";
        try (Cursor cursor = database.query("tasks", new String[]{"_id"},
                "(body = ? OR original_body = ?) AND (source = ? OR substr(source, 1, length(?)) = ?)",
                new String[]{body, body, source, senderPrefix, senderPrefix}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    Task nextReminder() {
        try (Cursor cursor = getReadableDatabase().query("tasks", COLUMNS, PENDING, null,
                null, null, "due", "1")) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    List<Task> list(boolean sortByDeadline) {
        return query(null, sortByDeadline ? "done, due IS NULL, due, _id DESC" : "done, _id DESC");
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

    boolean edit(long id, String body, Long due) {
        String text = body.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Empty task");
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try (Cursor cursor = database.query("tasks", COLUMNS, "_id = ?", args(id), null, null, null, "1")) {
            if (!cursor.moveToFirst()) return false;
            Task task = read(cursor);
            boolean deadlineChanged = !Objects.equals(task.due(), due);
            if (task.body().equals(text) && !deadlineChanged) return false;
            ContentValues values = new ContentValues();
            values.put("body", text);
            values.put("due", due);
            if (task.originalBody() == null) values.put("original_body", task.body());
            if (deadlineChanged) values.put("reminded", 0);
            boolean edited = database.update("tasks", values, "_id = ?", args(id)) == 1;
            database.setTransactionSuccessful();
            return edited;
        } finally {
            database.endTransaction();
        }
    }

    static void signalChange(Context context) {
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }

    private static Task read(Cursor cursor) {
        String storedCategory = cursor.getString(6);
        TaskCategory category = TaskCategory.DAILY.name().equals(storedCategory) ? TaskCategory.DAILY
                : TaskCategory.WORK.name().equals(storedCategory) ? TaskCategory.WORK : null;
        String packageName = cursor.getString(7);
        String sender = cursor.getString(8);
        Origin origin = packageName == null && sender == null ? null : new Origin(packageName, sender);
        return new Task(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                cursor.isNull(3) ? null : cursor.getLong(3), cursor.getInt(4) != 0, cursor.getInt(5) != 0, category,
                origin, cursor.getString(9));
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
