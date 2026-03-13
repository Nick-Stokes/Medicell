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
import java.util.List;
import java.util.Locale;

public class AlarmListAdapter extends RecyclerView.Adapter<AlarmListAdapter.VH> {

    public interface OnDelete {
        void delete(Alarm a);
    }

    private final List<Alarm> items = new ArrayList<>();
    private final OnDelete cb;

    public AlarmListAdapter(OnDelete cb) {
        this.cb = cb;
    }

    public void submit(List<Alarm> data) {
        int oldSize = items.size();
        if (oldSize > 0) {
            items.clear();
            notifyItemRangeRemoved(0, oldSize);
        } else {
            items.clear();
        }

        if (data != null && !data.isEmpty()) {
            items.addAll(data);
            notifyItemRangeInserted(0, items.size());
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Alarm a = items.get(position);
        holder.tvName.setText(a.pillName);
        holder.tvTime.setText(formatKoreanTime(a.hour, a.minute));

        holder.btnDelete.setOnClickListener(v -> {
            if (cb != null) {
                cb.delete(a);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatKoreanTime(int hour24, int minute) {
        String amPm = (hour24 < 12) ? "오전" : "오후";
        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }

        return amPm + " " + hour12 + ":" + String.format(Locale.getDefault(), "%02d", minute);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvTime;
        final Button btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
            btnDelete = itemView.findViewById(R.id.tvDelete);
        }
    }
}