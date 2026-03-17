package com.sookmyung.list.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.list.Pill;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.List;

/** 알약 리스트 어댑터 (툴팁으로 효능 표시) */
public class PillAdapter extends RecyclerView.Adapter<PillAdapter.VH> {

    public interface OnItemSelected { void onSelected(Pill p); }

    private final List<Pill> items = new ArrayList<>();
    private Pill selected;
    private final OnItemSelected cb;

    public PillAdapter(OnItemSelected cb) { this.cb = cb; }

    public void submit(List<Pill> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public Pill getSelected() { return selected; }

    @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pill, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(VH h, int pos) {
        Pill p = items.get(pos);
        h.tv.setText(p.itemName);
        String tip = "효능: " + (p.className == null ? "-" : p.className);
        TooltipCompat.setTooltipText(h.itemView, tip); // 길게 누르기/포커스 시 말풍선
        h.itemView.setSelected(p == selected);
        h.itemView.setOnClickListener(v -> {
            selected = p;
            notifyDataSetChanged();
            if (cb != null) cb.onSelected(p);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tv;
        VH(View v) { super(v); tv = v.findViewById(R.id.tvName); }
    }
}
