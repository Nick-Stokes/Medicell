package com.sookmyung.alarm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TimeCardAdapter extends RecyclerView.Adapter<TimeCardAdapter.VH> {

    public interface Listener {
        void onEdit(TimeItem item);
        void onDelete(TimeItem item);
    }

    public static class TimeItem {
        public final int hour;
        public final int minute;

        public TimeItem(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
        }
    }

    private final List<TimeItem> items = new ArrayList<>();
    private final Listener listener;

    public TimeCardAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TimeItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm_time, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TimeItem item = items.get(position);
        holder.tvTimeValue.setText(formatTime(item.hour, item.minute));
        holder.root.setOnClickListener(v -> listener.onEdit(item));
        holder.btnDeleteTime.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTime(int hour24, int minute) {
        String amPm = hour24 < 12 ? "오전" : "오후";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return amPm + " " + hour12 + ":" + String.format(Locale.getDefault(), "%02d", minute);
    }

    static class VH extends RecyclerView.ViewHolder {
        final View root;
        final TextView tvTimeValue;
        final Button btnDeleteTime;

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.timeCardRoot);
            tvTimeValue = itemView.findViewById(R.id.tvTimeValue);
            btnDeleteTime = itemView.findViewById(R.id.btnDeleteTime);
        }
    }
}
