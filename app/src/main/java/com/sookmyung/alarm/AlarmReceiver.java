package com.sookmyung.alarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CH_ID = "pill_alarm_ch";

    @Override public void onReceive(Context ctx, Intent intent) {
        // 알림 정보
        String id = intent.getStringExtra("id");
        String pillName = intent.getStringExtra("pillName");
        createChannel(ctx);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CH_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("복용 알림")
                .setContentText("지정 시간입니다: " + pillName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(ctx)
                .notify(Math.abs(id.hashCode()), nb.build());

        // 다음 날 같은 시간에 다시 예약
        Alarm a = AlarmStorage.find(ctx, id);
        if (a != null) AlarmScheduler.scheduleDaily(ctx, a);
    }

    private void createChannel(Context ctx){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "복용 알림", NotificationManager.IMPORTANCE_HIGH);
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}
