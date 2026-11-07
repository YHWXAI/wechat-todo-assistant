package com.tencent.mm;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.os.Bundle;

public final class FixtureActivity extends Activity {
    private static final String CHANNEL = "test_messages";

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "测试微信通知", 3));
        String scenario = getIntent().getStringExtra("scenario");
        Notification.Builder notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email);
        int id = 1;
        if ("due".equals(scenario)) {
            id = 2;
            notification.setContentTitle("测试好友").setContentText("请在2024-01-02上午8点提交报价");
        } else if ("group".equals(scenario)) {
            id = 3;
            long now = System.currentTimeMillis() / 60_000 * 60_000;
            Person sender = new Person.Builder().setName("测试群成员").build();
            Notification.MessagingStyle style = new Notification.MessagingStyle(
                    new Person.Builder().setName("我").build())
                    .setConversationTitle("测试工作群").setGroupConversation(true)
                    .addMessage("请明天准备旧文件", now - 15 * 60_000, sender)
                    .addMessage("请明天准备群聊纪要", now, sender)
                    .addMessage("请明天发我的副本", now, (Person) null);
            notification.setStyle(style);
        } else if ("plain".equals(scenario)) {
            id = 4;
            notification.setContentTitle("测试好友").setContentText("明天一起吃饭吗？");
        } else {
            notification.setContentTitle("测试好友").setContentText("请明天下午3点发送设计稿");
        }
        manager.notify(id, notification.build());
        finish();
    }
}
