package com.sookmyung.medicell;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.sookmyung.alarm.ui.AlarmListActivity;
import com.sookmyung.list.ui.PillListActivity;

public class threeButton extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_three_button);

        LinearLayout cardList = findViewById(R.id.card_list);
        LinearLayout cardSearch = findViewById(R.id.card_search);
        LinearLayout cardAlarm = findViewById(R.id.card_alarm);

        cardList.setOnClickListener(v ->
                startActivity(new Intent(this, PillListActivity.class)));

        cardSearch.setOnClickListener(v ->
                startActivity(new Intent(this, SquareCamera.class)));

        cardAlarm.setOnClickListener(v ->
                startActivity(new Intent(this, AlarmListActivity.class)));
    }
}