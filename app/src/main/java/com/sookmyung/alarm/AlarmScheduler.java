package com.sookmyung.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

public class AlarmScheduler {

    private static final String TAG = "MEDICELL_ALARM";

    public static void scheduleNext(Context ctx, Alarm alarm) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }

        long triggerAt = findNextTriggerTime(alarm, System.currentTimeMillis());
        if (triggerAt <= 0L) {
            cancel(ctx, alarm);
            Log.d(TAG, "다음 예약 시간이 없어 알람 종료 / " + alarm.pillName);
            return;
        }

        PendingIntent pi = pending(ctx, alarm);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
        Log.d(TAG, "알람 예약 완료 / " + alarm.pillName + " / triggerAt=" + triggerAt);
    }

    public static void cancel(Context ctx, Alarm alarm) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pending(ctx, alarm));
        }
    }

    public static void cancelGroup(Context ctx, String groupId) {
        List<Alarm> alarms = AlarmStorage.findByGroup(ctx, groupId);
        for (Alarm alarm : alarms) {
            cancel(ctx, alarm);
        }
    }

    private static PendingIntent pending(Context ctx, Alarm alarm) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra("id", alarm.id);
        intent.putExtra("pillName", alarm.pillName);
        intent.putExtra("hour", alarm.hour);
        intent.putExtra("minute", alarm.minute);
        return PendingIntent.getBroadcast(
                ctx,
                Math.abs(alarm.id.hashCode()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static long findNextTriggerTime(Alarm alarm, long nowMillis) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(alarm.startDateMillis);
        zeroDate(start);

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(alarm.endDateMillis);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(nowMillis);
        zeroDate(cursor);

        if (cursor.before(start)) {
            cursor.setTimeInMillis(start.getTimeInMillis());
        }

        for (int i = 0; i < 730; i++) {
            if (cursor.after(end)) {
                return -1L;
            }

            if (isDayAllowed(alarm, cursor)) {
                Calendar candidate = (Calendar) cursor.clone();
                candidate.set(Calendar.HOUR_OF_DAY, alarm.hour);
                candidate.set(Calendar.MINUTE, alarm.minute);
                candidate.set(Calendar.SECOND, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                if (candidate.getTimeInMillis() > nowMillis &&
                        candidate.getTimeInMillis() >= start.getTimeInMillis() &&
                        candidate.getTimeInMillis() <= end.getTimeInMillis()) {
                    return candidate.getTimeInMillis();
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
        return -1L;
    }

    private static boolean isDayAllowed(Alarm alarm, Calendar date) {
        if (alarm.everyDay) {
            return true;
        }
        List<Integer> days = alarm.daysOfWeek;
        return days != null && days.contains(date.get(Calendar.DAY_OF_WEEK));
    }

    private static void zeroDate(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
