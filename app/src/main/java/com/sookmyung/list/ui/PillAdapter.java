package com.sookmyung.list.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.list.Pill;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.List;

/** 알약 리스트 어댑터 */
public class PillAdapter extends RecyclerView.Adapter<PillAdapter.VH> {

    public interface OnItemClick {
        void onClick(Pill pill);
    }

    public interface OnDeleteClick {
        void onDelete(Pill pill);
    }

    private final List<Pill> items = new ArrayList<>();
    private final OnItemClick itemCb;
    private final OnDeleteClick deleteCb;

    public PillAdapter(OnItemClick itemCb, OnDeleteClick deleteCb) {
        this.itemCb = itemCb;
        this.deleteCb = deleteCb;
    }

    public void submit(List<Pill> data) {
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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pill, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Pill p = items.get(pos);

        h.tvName.setText(p.itemName);

        String tip = "효능: " + (p.className == null ? "-" : p.className);
        TooltipCompat.setTooltipText(h.itemView, tip);

        h.tvName.setOnClickListener(v -> {
            if (itemCb != null) {
                itemCb.onClick(p);
            }
        });

        h.btnDelete.setOnClickListener(v -> {
            if (deleteCb != null) {
                deleteCb.onDelete(p);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName;
        final Button btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            btnDelete = v.findViewById(R.id.btnDeleteItem);
        }
    }
}