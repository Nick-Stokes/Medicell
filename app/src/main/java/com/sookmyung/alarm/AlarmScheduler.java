package com.sookmyung.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

public class AlarmScheduler {

    public static void scheduleDaily(Context ctx, Alarm a){
        long triggerAt = nextTriggerTime(a.hour, a.minute);
        PendingIntent pi = pending(ctx, a);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        // Doze에서도 가능한 정확 알람
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }

    public static void cancel(Context ctx, Alarm a){
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, a));
    }

    private static PendingIntent pending(Context ctx, Alarm a){
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.putExtra("id", a.id);
        i.putExtra("pillName", a.pillName);
        return PendingIntent.getBroadcast(
                ctx, a.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long nextTriggerTime(int hour, int minute){
        Calendar c = Calendar.getInstance();
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1); // 오늘 시간이 지났으면 내일
        }
        return c.getTimeInMillis();
    }
}
