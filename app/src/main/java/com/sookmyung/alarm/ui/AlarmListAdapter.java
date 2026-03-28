package com.sookmyung.alarm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.alarm.Alarm;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AlarmListAdapter extends RecyclerView.Adapter<AlarmListAdapter.VH> {

    public interface Listener {
        void onEdit(AlarmGroupItem item);
        void onDelete(AlarmGroupItem item);
    }

    public static class AlarmGroupItem {
        public String groupId;
        public String pillName;
        public long startDateMillis;
        public long endDateMillis;
        public boolean everyDay;
        public List<Integer> daysOfWeek = new ArrayList<>();
        public List<Alarm> alarms = new ArrayList<>();
    }

    private final List<AlarmGroupItem> items = new ArrayList<>();
    private final Listener listener;

    public AlarmListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<AlarmGroupItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AlarmGroupItem item = items.get(position);
        holder.tvName.setText(item.pillName);
        holder.tvDays.setText(buildDaysText(item.everyDay, item.daysOfWeek));
        holder.tvTime.setText(buildTimeText(item.alarms));
        holder.root.setOnClickListener(v -> listener.onEdit(item));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String buildTimeText(List<Alarm> alarms) {
        List<Alarm> sorted = new ArrayList<>(alarms);
        Collections.sort(sorted, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a1, Alarm a2) {
                if (a1.hour != a2.hour) {
                    return Integer.compare(a1.hour, a2.hour);
                }
                return Integer.compare(a1.minute, a2.minute);
            }
        });

        List<String> parts = new ArrayList<>();
        for (Alarm alarm : sorted) {
            parts.add(formatTime(alarm.hour, alarm.minute));
        }
        return join(parts, "\n");
    }


    private String buildDaysText(boolean everyDay, List<Integer> days) {
        return everyDay ? "매일" : dayText(days);
    }

    private String dayText(List<Integer> days) {
        List<Integer> copy = new ArrayList<>(days);
        Collections.sort(copy);
        List<String> names = new ArrayList<>();
        for (Integer day : copy) {
            if (day == Calendar.MONDAY) names.add("월");
            else if (day == Calendar.TUESDAY) names.add("화");
            else if (day == Calendar.WEDNESDAY) names.add("수");
            else if (day == Calendar.THURSDAY) names.add("목");
            else if (day == Calendar.FRIDAY) names.add("금");
            else if (day == Calendar.SATURDAY) names.add("토");
            else if (day == Calendar.SUNDAY) names.add("일");
        }
        return join(names, ", ");
    }

    private String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private String formatTime(int hour24, int minute) {
        String amPm = hour24 < 12 ? "오전" : "오후";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return amPm + " " + hour12 + ":" + String.format(Locale.getDefault(), "%02d", minute);
    }

    static class VH extends RecyclerView.ViewHolder {
        final View root;
        final TextView tvName;
        final TextView tvTime;
        final TextView tvDays;
        final Button btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.alarmCardRoot);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvDays = itemView.findViewById(R.id.tvDays);
            btnDelete = itemView.findViewById(R.id.tvDelete);
        }
    }
}
