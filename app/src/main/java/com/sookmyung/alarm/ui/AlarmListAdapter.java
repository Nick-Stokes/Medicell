package com.sookmyung.alarm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.sookmyung.alarm.Alarm;
import com.sookmyung.medicell.R;
import java.util.ArrayList;
import java.util.List;

public class AlarmListAdapter extends RecyclerView.Adapter<AlarmListAdapter.VH> {

    public interface OnDelete { void delete(Alarm a); }
    private final List<Alarm> items = new ArrayList<>();
    private final OnDelete cb;

    public AlarmListAdapter(OnDelete cb){ this.cb = cb; }
    public void submit(List<Alarm> data){ items.clear(); if(data!=null) items.addAll(data); notifyDataSetChanged(); }

    @Override public VH onCreateViewHolder(ViewGroup p, int vt){
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_alarm, p, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(VH h, int pos){
        Alarm a = items.get(pos);
        h.tvName.setText(a.pillName);
        h.tvTime.setText(String.format("%02d:%02d", a.hour, a.minute));
        h.btnDelete.setOnClickListener(v -> { if (cb!=null) cb.delete(a); });
    }
    @Override public int getItemCount(){ return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, btnDelete;
        VH(View v){ super(v);
            tvName = v.findViewById(R.id.tvName);
            tvTime = v.findViewById(R.id.tvTime);
            btnDelete = v.findViewById(R.id.tvDelete);
        }
    }
}
