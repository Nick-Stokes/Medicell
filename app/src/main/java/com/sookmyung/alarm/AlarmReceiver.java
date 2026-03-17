package com.sookmyung.alarm;

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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sookmyung.alarm.ui.AlarmListActivity;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CH_ID = "pill_alarm_ch_v2";
    private static final String TAG = "MEDICELL_ALARM";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null) {
            Log.d(TAG, "Context가 null이라 알림 처리 중단");
            return;
        }

        if (intent == null) {
            Log.d(TAG, "Intent가 null이라 알림 처리 중단");
            return;
        }

        String id = intent.getStringExtra("id");
        String pillName = intent.getStringExtra("pillName");

        if (id == null || id.trim().isEmpty()) {
            Log.d(TAG, "id가 null 또는 비어 있어 알림 처리 중단");
            return;
        }

        if (pillName == null || pillName.trim().isEmpty()) {
            Log.d(TAG, "pillName이 null 또는 비어 있어 알림 처리 중단");
            return;
        }

        Log.d(TAG, "onReceive 호출됨 / id=" + id + " / pillName=" + pillName);

        createChannel(ctx);

        Intent openIntent = new Intent(ctx, AlarmListActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                ctx,
                Math.abs(id.hashCode()),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CH_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("복용 알림")
                .setContentText(pillName + " 복용 시간입니다.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(pillName + " 복용 시간입니다. 지금 약을 복용하세요."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS 권한 없음 - 알림 표시 중단");
                return;
            }
        }

        try {
            NotificationManagerCompat.from(ctx)
                    .notify(Math.abs(id.hashCode()), nb.build());
            Log.d(TAG, "알림 표시 완료 / notificationId=" + Math.abs(id.hashCode()));
        } catch (SecurityException e) {
            Log.e(TAG, "알림 표시 중 SecurityException 발생", e);
            return;
        }

        Alarm a = AlarmStorage.find(ctx, id);
        if (a != null) {
            Log.d(TAG, "기존 알람 찾음 -> 다음 날 재예약 / " + a.pillName);
            AlarmScheduler.scheduleDaily(ctx, a);
        } else {
            Log.d(TAG, "AlarmStorage에서 알람을 찾지 못함 / id=" + id);
        }
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel ch = new NotificationChannel(
                    CH_ID,
                    "복용 알림",
                    NotificationManager.IMPORTANCE_HIGH
            );

            ch.setDescription("약 복용 시간 알림");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            ch.setSound(alarmSound, audioAttributes);

            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
                Log.d(TAG, "알림 채널 생성 또는 갱신 완료");
            } else {
                Log.d(TAG, "NotificationManager가 null이라 채널 생성 실패");
            }
        }
    }
}