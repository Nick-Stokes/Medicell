package com.sookmyung.alarm;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "MEDICELL_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.d(TAG, "BootReceiver: intent 또는 action이 null");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "BootReceiver 수신 / action=" + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {

            List<Alarm> alarms = AlarmStorage.load(context);
            Log.d(TAG, "저장된 알람 개수 = " + alarms.size());

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                Log.d(TAG, "AlarmManager가 null이라 재예약 실패");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean canExact = am.canScheduleExactAlarms();
                Log.d(TAG, "BootReceiver canScheduleExactAlarms = " + canExact);

                if (!canExact) {
                    Log.d(TAG, "정확 알람 권한이 없어 재예약하지 못함");
                    return;
                }
            }

            for (Alarm a : alarms) {
                Log.d(TAG, "재예약 시작 / pillName=" + a.pillName
                        + " / hour=" + a.hour
                        + " / minute=" + a.minute);
                AlarmScheduler.scheduleDaily(context, a);
            }

            Log.d(TAG, "BootReceiver 재예약 완료");
        }
    }
}

