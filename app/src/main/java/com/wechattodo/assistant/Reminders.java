package com.wechattodo.assistant;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.provider.Settings;

public final class Reminders extends BroadcastReceiver {
    static final String CHANNEL = "task_due";
    private static final String FIRE = "com.wechattodo.assistant.REMIND";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            scheduleNext(context);
            return;
        }
        if (!FIRE.equals(action) || !canNotify(context)) {
            return;
        }
        TaskStore store = TaskStore.get(context);
        long now = System.currentTimeMillis();
        for (TaskStore.Task task : store.pendingReminders()) {
            if (task.due() > now) {
                break;
            }
            show(context, task);
            store.markReminded(task.id());
        }
        scheduleNext(context);
    }

    static void scheduleNext(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0,
                new Intent(context, Reminders.class).setAction(FIRE), PendingIntent.FLAG_IMMUTABLE);
        TaskStore.Task next = TaskStore.get(context).nextReminder();
        if (next == null || !canNotify(context)) {
            alarms.cancel(pending);
            return;
        }
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.due(), pending);
                return;
            } catch (SecurityException revoked) {
                // Access can change after canScheduleExactAlarms().
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.due(), pending);
    }

    static boolean canNotify(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL, "待办到期提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("在微信待办到期时提醒");
            channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            manager.createNotificationChannel(channel);
        }
        return manager.areNotificationsEnabled() && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void dismiss(Context context, long id) {
        context.getSystemService(NotificationManager.class).cancel(Long.toString(id), 0);
    }

    private static void show(Context context, TaskStore.Task task) {
        PendingIntent openApp = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle("待办到期")
                .setContentText(task.body())
                .setStyle(new Notification.BigTextStyle().bigText(task.body()))
                .setContentIntent(openApp)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(Long.toString(task.id()), 0, notification);
    }
}
