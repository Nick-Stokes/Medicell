package com.sookmyung.alarm;

import java.util.Locale;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sookmyung.alarm.ui.AlarmListActivity;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CH_ID = "pill_alarm_ch_v3";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) {
            return;
        }

        String id = intent.getStringExtra("id");
        String pillName = intent.getStringExtra("pillName");
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);

        if (id == null || pillName == null || hour < 0 || minute < 0) {
            return;
        }

        createChannel(ctx);

        String amPm = hour < 12 ? "오전" : "오후";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;

        String alarmTimeText = amPm + " " + hour12 + ":" + String.format(Locale.getDefault(), "%02d", minute);
        String alarmMessage = pillName + " 복용할 시간이에요!";

        Intent openIntent = new Intent(ctx, AlarmListActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                ctx,
                Math.abs(id.hashCode()),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CH_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(alarmTimeText)
                .setContentText(alarmMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alarmMessage))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(ctx).notify(Math.abs(id.hashCode()), builder.build());
        }

        Alarm alarm = AlarmStorage.find(ctx, id);
        if (alarm != null) {
            AlarmScheduler.scheduleNext(ctx, alarm);
        }
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel ch = new NotificationChannel(CH_ID, "복용 알림", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("약 복용 시간 알림");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            ch.setSound(sound, audioAttributes);

            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }
}
