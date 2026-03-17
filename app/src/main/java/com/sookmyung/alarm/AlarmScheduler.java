package com.sookmyung.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AlarmScheduler {

    private static final String TAG = "MEDICELL_ALARM";

    public static void scheduleDaily(Context ctx, Alarm a) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        if (am == null) {
            Log.d(TAG, "AlarmManager가 null이라 예약 실패");
            return;
        }

        long triggerAt = nextTriggerTime(a.hour, a.minute);

        String nowText = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
        ).format(new Date());

        String triggerText = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
        ).format(new Date(triggerAt));

        Log.d(TAG, "현재 시각 = " + nowText);
        Log.d(TAG, "예약 시각 = " + triggerText);

        PendingIntent pi = pending(ctx, a);

        Log.d(TAG, "알람 예약 시작 / pillName=" + a.pillName
                + " / hour=" + a.hour
                + " / minute=" + a.minute);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean canExact = am.canScheduleExactAlarms();
            Log.d(TAG, "canScheduleExactAlarms = " + canExact);

            if (canExact) {
                am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pi
                );
            } else {
                Log.d(TAG, "정확한 알람 권한이 없어 setAndAllowWhileIdle로 대체");
                am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pi
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pi
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pi
            );
        } else {
            am.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pi
            );
        }

        Log.d(TAG, "알람 예약 완료");
    }

    public static void cancel(Context ctx, Alarm a) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        if (am != null) {
            am.cancel(pending(ctx, a));
            Log.d(TAG, "알람 취소 완료 / pillName=" + a.pillName);
        } else {
            Log.d(TAG, "AlarmManager null이라 취소 실패");
        }
    }

    private static PendingIntent pending(Context ctx, Alarm a) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.putExtra("id", a.id);
        i.putExtra("pillName", a.pillName);

        return PendingIntent.getBroadcast(
                ctx,
                a.id.hashCode(),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static long nextTriggerTime(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        long now = System.currentTimeMillis();

        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);

        if (c.getTimeInMillis() <= now) {
            c.add(Calendar.DAY_OF_YEAR, 1);
            Log.d(TAG, "설정한 시간이 현재보다 이전이라 다음 날로 예약");
        } else {
            Log.d(TAG, "오늘 시각으로 예약");
        }

        return c.getTimeInMillis();
    }
}