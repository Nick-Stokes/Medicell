package com.sookmyung.alarm.ui;

import android.widget.ImageButton;
import android.util.Log;
import android.view.Window;
import android.Manifest;
import android.app.AlarmManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmScheduler;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;
import com.sookmyung.list.ui.PillListActivity;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AddAlarmActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 1001;

    private ImageButton btnBackCircle;
    private View step1Container;
    private View step2Container;

    private Button btnBottom;

    private LinearLayout selectPillBox;
    private TextView tvSelectedPill;
    private TextView btnEveryDay;
    private TextView btnManualDays;
    private final List<TextView> dayViews = new ArrayList<>();
    private RecyclerView rvTimes;
    private TextView tvTimeGuide;

    private final List<String> pillNames = new ArrayList<>();
    private String selectedPillName;
    private ListPopupWindow pillPopupWindow;

    private final List<Integer> selectedDays = new ArrayList<>();
    private boolean everyDay = true;
    private long startDateMillis;
    private long endDateMillis;
    private int currentStep = 1;

    private final List<TimeCardAdapter.TimeItem> timeItems = new ArrayList<>();
    private TimeCardAdapter timeCardAdapter;
    private String editGroupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_alarm);

        bindViews();
        requestNotificationPermissionIfNeeded();
        setupDefaultDates();
        loadPillNames();
        setupDropdown();
        setupDaySelection();
        setupTimeSection();
        loadEditDataIfNeeded();
        updateStepUI();
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                AddAlarmActivity.this.handleBackClick();
            }
        });
    }

    private void bindViews() {
        btnBackCircle = findViewById(R.id.btnBackCircle);
        step1Container = findViewById(R.id.step1Container);
        step2Container = findViewById(R.id.step2Container);
        btnBottom = findViewById(R.id.btnBottom);

        selectPillBox = findViewById(R.id.selectPillBox);
        tvSelectedPill = findViewById(R.id.tvSelectedPill);
        btnEveryDay = findViewById(R.id.btnEveryDay);
        btnManualDays = findViewById(R.id.btnManualDays);

        dayViews.clear();
        dayViews.add(findViewById(R.id.tvMon));
        dayViews.add(findViewById(R.id.tvTue));
        dayViews.add(findViewById(R.id.tvWed));
        dayViews.add(findViewById(R.id.tvThu));
        dayViews.add(findViewById(R.id.tvFri));
        dayViews.add(findViewById(R.id.tvSat));
        dayViews.add(findViewById(R.id.tvSun));

        rvTimes = findViewById(R.id.rvTimes);
        tvTimeGuide = findViewById(R.id.tvTimeGuide);

        btnBackCircle.setOnClickListener(v -> handleBackClick());
        findViewById(R.id.btnAddTime).setOnClickListener(v -> openWheelTimeDialog(null));
        btnBottom.setOnClickListener(v -> onBottomButtonClicked());
    }

    private void setupDefaultDates() {
        Calendar today = Calendar.getInstance();
        zeroDate(today);
        startDateMillis = today.getTimeInMillis();

        Calendar twoYearsLater = (Calendar) today.clone();
        twoYearsLater.add(Calendar.DAY_OF_YEAR, 730);
        endDateMillis = twoYearsLater.getTimeInMillis();
    }

    private void loadPillNames() {
        pillNames.clear();
        List<Pill> pills = PillStorage.load(this);
        for (Pill pill : pills) {
            if (pill != null && pill.itemName != null && !pill.itemName.trim().isEmpty()) {
                if (!pillNames.contains(pill.itemName)) {
                    pillNames.add(pill.itemName);
                }
            }
        }
    }

    private void setupDropdown() {
        if (pillNames.isEmpty()) {
            tvSelectedPill.setText("복용 중인 약이 없습니다");
            selectPillBox.setOnClickListener(v -> showNoPillDialog());
            return;
        }

        if (selectedPillName == null) {
            selectedPillName = pillNames.get(0);
        }
        tvSelectedPill.setText(selectedPillName);
        markPillSelected(true);
        selectPillBox.setOnClickListener(v -> showPillDropdown());
    }

    private void showPillDropdown() {
        if (pillPopupWindow != null && pillPopupWindow.isShowing()) {
            pillPopupWindow.dismiss();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 0, pillNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = LayoutInflater.from(getContext()).inflate(
                            R.layout.item_pill_dropdown,
                            parent,
                            false
                    );
                }

                LinearLayout rowRoot = view.findViewById(R.id.dropdownRowRoot);
                TextView tvName = view.findViewById(R.id.tvPillName);
                TextView tvCheck = view.findViewById(R.id.tvSelectedCheck);
                View divider = view.findViewById(R.id.dropdownDivider);

                String pillName = getItem(position);
                boolean isSelected = pillName != null && pillName.equals(selectedPillName);
                boolean isLast = position == getCount() - 1;

                tvName.setText(pillName);
                rowRoot.setBackgroundResource(
                        getDropdownItemBackgroundRes(position, getCount(), isSelected)
                );

                if (isSelected) {
                    tvCheck.setVisibility(View.VISIBLE);
                    tvName.setTextColor(Color.parseColor("#222222"));
                    tvName.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    tvCheck.setVisibility(View.INVISIBLE);
                    tvName.setTextColor(Color.parseColor("#222222"));
                    tvName.setTypeface(Typeface.DEFAULT_BOLD);
                }

                divider.setVisibility(isLast ? View.GONE : View.VISIBLE);
                return view;
            }
        };

        pillPopupWindow = new ListPopupWindow(this);
        pillPopupWindow.setAnchorView(selectPillBox);
        pillPopupWindow.setAdapter(adapter);
        pillPopupWindow.setModal(true);
        pillPopupWindow.setBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_pill_dropdown_popup)
        );
        pillPopupWindow.setWidth(selectPillBox.getWidth());
        pillPopupWindow.setHorizontalOffset(0);

        int verticalOffset = dpToPx(6);
        pillPopupWindow.setVerticalOffset(verticalOffset);
        pillPopupWindow.setHeight(getDropdownPopupHeight(selectPillBox, verticalOffset, pillNames.size()));

        pillPopupWindow.setOnDismissListener(() -> markPillSelected(true));
        pillPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            selectedPillName = pillNames.get(position);
            tvSelectedPill.setText(selectedPillName);
            markPillSelected(true);
            pillPopupWindow.dismiss();
        });

        selectPillBox.post(() -> {
            pillPopupWindow.setWidth(selectPillBox.getWidth());
            pillPopupWindow.setHeight(getDropdownPopupHeight(selectPillBox, verticalOffset, pillNames.size()));
            markPillSelected(true);
            pillPopupWindow.show();
        });
    }

    private int getDropdownItemBackgroundRes(int position, int count, boolean isSelected) {
        boolean isSingle = count == 1;
        boolean isFirst = position == 0;
        boolean isLast = position == count - 1;

        if (isSelected) {
            if (isSingle) return R.drawable.bg_dropdown_item_selected_single;
            if (isFirst) return R.drawable.bg_dropdown_item_selected_top;
            if (isLast) return R.drawable.bg_dropdown_item_selected_bottom;
            return R.drawable.bg_dropdown_item_selected_middle;
        } else {
            if (isSingle) return R.drawable.bg_dropdown_item_single;
            if (isFirst) return R.drawable.bg_dropdown_item_top;
            if (isLast) return R.drawable.bg_dropdown_item_bottom;
            return R.drawable.bg_dropdown_item_middle;
        }
    }

    private int getDropdownPopupHeight(View anchor, int verticalOffsetPx, int itemCount) {
        Rect windowFrame = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(windowFrame);

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);

        int anchorBottomOnScreen = location[1] + anchor.getHeight();
        int spaceBelow = windowFrame.bottom - (anchorBottomOnScreen + verticalOffsetPx);

        int popupOuterMargin = dpToPx(8);
        int availableHeight = Math.max(spaceBelow - popupOuterMargin, dpToPx(160));

        int rowHeight = dpToPx(120);
        int dividerHeight = dpToPx(1);
        int popupVerticalPadding = dpToPx(8);

        int contentHeight;
        if (itemCount <= 0) {
            contentHeight = dpToPx(160);
        } else {
            contentHeight = (rowHeight * itemCount)
                    + (dividerHeight * Math.max(itemCount - 1, 0))
                    + popupVerticalPadding;
        }

        return Math.min(contentHeight, availableHeight);
    }

    private void setupDaySelection() {
        btnEveryDay.setOnClickListener(v -> {
            everyDay = true;
            selectedDays.clear();
            updateDayModeUI();
        });

        btnManualDays.setOnClickListener(v -> {
            everyDay = false;
            if (selectedDays.isEmpty()) {
                selectedDays.add(Calendar.MONDAY);
            }
            updateDayModeUI();
        });

        int[] dayConsts = new int[]{
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
        };

        for (int i = 0; i < dayViews.size(); i++) {
            final int dayConst = dayConsts[i];
            TextView dayView = dayViews.get(i);
            dayView.setOnClickListener(v -> {
                if (everyDay) return;

                if (selectedDays.contains(dayConst)) {
                    if (selectedDays.size() > 1) {
                        selectedDays.remove((Integer) dayConst);
                    }
                } else {
                    selectedDays.add(dayConst);
                }
                updateDayModeUI();
            });
        }

        updateDayModeUI();
    }

    private void setupTimeSection() {
        rvTimes.setLayoutManager(new LinearLayoutManager(this));
        rvTimes.setNestedScrollingEnabled(false);

        timeCardAdapter = new TimeCardAdapter(new TimeCardAdapter.Listener() {
            @Override
            public void onEdit(TimeCardAdapter.TimeItem item) {
                openWheelTimeDialog(item);
            }

            @Override
            public void onDelete(TimeCardAdapter.TimeItem item) {
                timeItems.remove(item);
                sortTimes();
                refreshTimeList();
            }
        });

        rvTimes.setAdapter(timeCardAdapter);

        findViewById(R.id.btnAddTime).setOnClickListener(v -> openWheelTimeDialog(null));

        refreshTimeList();
    }

    private void loadEditDataIfNeeded() {
        editGroupId = getIntent().getStringExtra("groupId");
        if (editGroupId == null || editGroupId.trim().isEmpty()) {
            return;
        }

        List<Alarm> group = AlarmStorage.findByGroup(this, editGroupId);
        if (group.isEmpty()) {
            return;
        }

        Alarm first = group.get(0);

        selectedPillName = first.pillName;
        tvSelectedPill.setText(selectedPillName);
        markPillSelected(true);

        startDateMillis = first.startDateMillis;
        endDateMillis = first.endDateMillis;

        everyDay = first.everyDay;
        selectedDays.clear();
        if (first.daysOfWeek != null) {
            selectedDays.addAll(first.daysOfWeek);
        }
        if (!everyDay && selectedDays.isEmpty()) {
            selectedDays.add(Calendar.MONDAY);
        }
        updateDayModeUI();

        timeItems.clear();
        for (Alarm alarm : group) {
            timeItems.add(new TimeCardAdapter.TimeItem(alarm.hour, alarm.minute));
        }
        sortTimes();
        refreshTimeList();
    }

    private void handleBackClick() {
        if (currentStep == 1) {
            finish();
        } else {
            currentStep = 1;
            updateStepUI();
        }
    }


    private void onBottomButtonClicked() {
        if (currentStep == 1) {
            if (pillNames.isEmpty()) {
                showNoPillDialog();
                return;
            }

            if (selectedPillName == null || selectedPillName.trim().isEmpty()) {
                Toast.makeText(this, "약을 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!everyDay && selectedDays.isEmpty()) {
                Toast.makeText(this, "직접 선택 시 요일을 1개 이상 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            currentStep = 2;
            updateStepUI();
            return;
        }

        if (timeItems.isEmpty()) {
            showLargeMessageDialog("복용 시간을", "추가해주세요");
            return;
        }

        saveAlarmGroup();
    }

    private void saveAlarmGroup() {
        if (!checkAndRequestAlarmPermissions()) {
            return;
        }

        String groupId = editGroupId != null ? editGroupId : UUID.randomUUID().toString();
        List<Integer> daysToSave = everyDay ? new ArrayList<>() : new ArrayList<>(selectedDays);
        List<Alarm> alarms = new ArrayList<>();

        for (TimeCardAdapter.TimeItem item : timeItems) {
            alarms.add(new Alarm(
                    UUID.randomUUID().toString(),
                    groupId,
                    selectedPillName,
                    item.hour,
                    item.minute,
                    startDateMillis,
                    endDateMillis,
                    everyDay,
                    daysToSave
            ));
        }

        AlarmScheduler.cancelGroup(this, groupId);
        AlarmStorage.replaceGroup(this, groupId, alarms);

        for (Alarm alarm : alarms) {
            AlarmScheduler.scheduleNext(this, alarm);
        }

        Toast.makeText(
                this,
                editGroupId == null ? "복용 알림이 저장되었습니다." : "복용 알림이 수정되었습니다.",
                Toast.LENGTH_SHORT
        ).show();

        finish();
    }

    private void refreshTimeList() {
        timeCardAdapter.submit(new ArrayList<>(timeItems));

        boolean hasItems = !timeItems.isEmpty();

        rvTimes.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        tvTimeGuide.setVisibility(hasItems ? View.GONE : View.VISIBLE);

        if (!hasItems) {
            tvTimeGuide.setText("알림을 1개 이상\n추가해주세요");
        }
    }

    private void sortTimes() {
        Collections.sort(timeItems, new Comparator<TimeCardAdapter.TimeItem>() {
            @Override
            public int compare(TimeCardAdapter.TimeItem o1, TimeCardAdapter.TimeItem o2) {
                if (o1.hour != o2.hour) {
                    return Integer.compare(o1.hour, o2.hour);
                }
                return Integer.compare(o1.minute, o2.minute);
            }
        });
    }

    private void updateStepUI() {
        step1Container.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        step2Container.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);

        if (currentStep == 1) {
            btnBottom.setText("다음");
            btnBottom.setEnabled(true);
        } else {
            btnBottom.setText("저장");
            btnBottom.setEnabled(true);
        }
    }



    private void updateDayModeUI() {
        btnEveryDay.setSelected(everyDay);
        btnManualDays.setSelected(!everyDay);
        btnEveryDay.setTextColor(Color.parseColor("#222222"));
        btnManualDays.setTextColor(Color.parseColor("#222222"));

        int[] dayConsts = new int[]{
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
        };

        for (int i = 0; i < dayViews.size(); i++) {
            TextView dayView = dayViews.get(i);
            int dayConst = dayConsts[i];
            boolean selected = !everyDay && selectedDays.contains(dayConst);

            dayView.setEnabled(!everyDay);
            dayView.setSelected(selected);

            if (everyDay) {
                dayView.setAlpha(1f);
                dayView.setTextColor(Color.parseColor("#A9A39B"));
            } else {
                dayView.setAlpha(1f);
                dayView.setTextColor(selected
                        ? Color.parseColor("#222222")
                        : Color.parseColor("#A9A39B"));
            }
        }
    }

    private void markPillSelected(boolean selected) {
        selectPillBox.setBackgroundResource(
                selected ? R.drawable.bg_select_box_active : R.drawable.bg_select_box
        );
        if (currentStep == 1) {
            btnBottom.setEnabled(selected);
        }
    }

    private void styleTimePickers(NumberPicker... pickers) {
        for (NumberPicker picker : pickers) {
            picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            picker.setWrapSelectorWheel(false);
            picker.setVerticalFadingEdgeEnabled(false);
            picker.setFadingEdgeLength(0);

            applyPickerAppearance(picker);

            picker.setOnValueChangedListener((numberPicker, oldVal, newVal) ->
                    applyPickerAppearance(numberPicker));

            picker.setOnScrollListener((numberPicker, scrollState) -> {
                if (scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                    applyPickerAppearance(numberPicker);
                }
            });

            picker.post(() -> applyPickerAppearance(picker));
        }
    }

    private void applyPickerAppearance(NumberPicker picker) {
        hideNumberPickerDivider(picker);
        setNumberPickerTextSize(picker, 62f);
        picker.requestLayout();
        picker.invalidate();
    }



    private void setNumberPickerTextSize(NumberPicker picker, float textSizeSp) {
        // reflective access error 방지 (내용 제거)
    }

    private void hideNumberPickerDivider(NumberPicker picker) {
        // reflective access error 방지 (내용 제거)
    }

    private void openWheelTimeDialog(TimeCardAdapter.TimeItem editItem) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_wheel_time);
        dialog.setCancelable(true);

        NumberPicker pickerAmPm = dialog.findViewById(R.id.pickerAmPm);
        NumberPicker pickerHour = dialog.findViewById(R.id.pickerHour);
        NumberPicker pickerMinute = dialog.findViewById(R.id.pickerMinute);
        Button btnClose = dialog.findViewById(R.id.btnClose);
        Button btnSelect = dialog.findViewById(R.id.btnSelect);

        pickerAmPm.setScaleX(1.53f);
        pickerAmPm.setScaleY(1.53f);

        pickerHour.setScaleX(1.53f);
        pickerHour.setScaleY(1.53f);

        pickerMinute.setScaleX(1.53f);
        pickerMinute.setScaleY(1.53f);

        pickerAmPm.setMinValue(0);
        pickerAmPm.setMaxValue(1);
        pickerAmPm.setDisplayedValues(new String[]{"오전", "오후"});
        pickerAmPm.setWrapSelectorWheel(false);

        pickerHour.setMinValue(1);
        pickerHour.setMaxValue(12);
        pickerHour.setWrapSelectorWheel(false);

        String[] minuteTexts = new String[60];
        for (int i = 0; i < 60; i++) {
            minuteTexts[i] = String.format(Locale.getDefault(), "%02d", i);
        }
        pickerMinute.setMinValue(0);
        pickerMinute.setMaxValue(59);
        pickerMinute.setDisplayedValues(minuteTexts);
        pickerMinute.setWrapSelectorWheel(false);

        int hour24 = editItem != null ? editItem.hour : 8;
        int minute = editItem != null ? editItem.minute : 0;
        int amPmIndex = hour24 < 12 ? 0 : 1;
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;

        pickerAmPm.setValue(amPmIndex);
        pickerHour.setValue(hour12);
        pickerMinute.setValue(minute);

        styleTimePickers(pickerAmPm, pickerHour, pickerMinute);

        dialog.setOnShowListener(d -> {
            applyPickerAppearance(pickerAmPm);
            applyPickerAppearance(pickerHour);
            applyPickerAppearance(pickerMinute);
        });

        pickerAmPm.post(() -> {
            adjustPickerOverlay(dialog);
            drawPickerLines(dialog);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSelect.setOnClickListener(v -> {
            int selectedAmPm = pickerAmPm.getValue();
            int selectedHour12 = pickerHour.getValue();
            int selectedMinute = pickerMinute.getValue();

            int convertedHour24;
            if (selectedAmPm == 0) {
                convertedHour24 = selectedHour12 == 12 ? 0 : selectedHour12;
            } else {
                convertedHour24 = selectedHour12 == 12 ? 12 : selectedHour12 + 12;
            }

            if (editItem != null) {
                timeItems.remove(editItem);
            }
            timeItems.add(new TimeCardAdapter.TimeItem(convertedHour24, selectedMinute));
            sortTimes();
            refreshTimeList();
            dialog.dismiss();
        });

        showCenteredDialog(dialog);
    }

    private void showLargeMessageDialog(String line1, String line2) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_large_message);
        dialog.setCancelable(true);

        TextView tvLine1 = dialog.findViewById(R.id.tvLine1);
        TextView tvLine2 = dialog.findViewById(R.id.tvLine2);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirm);

        tvLine1.setText(line1);

        if (line2 != null && !line2.trim().isEmpty()) {
            tvLine2.setText(line2);
            tvLine2.setVisibility(View.VISIBLE);
        } else {
            tvLine2.setVisibility(View.GONE);
        }

        btnConfirm.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void showNoPillDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_large_message);
        dialog.setCancelable(false);

        TextView tvLine1 = dialog.findViewById(R.id.tvLine1);
        TextView tvLine2 = dialog.findViewById(R.id.tvLine2);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirm);

        tvLine1.setText("복용 알약 리스트에");
        tvLine2.setText("먼저 약을 추가해주세요");

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, PillListActivity.class));
            finish();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void showCenteredDialog(Dialog dialog) {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.89f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private boolean checkAndRequestAlarmPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notificationGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;

            if (!notificationGranted) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
                Toast.makeText(this, "알림 권한을 허용해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Toast.makeText(this, "정확한 알람 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return false;
            }
        }
        return true;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
            }
        }
    }

    private void zeroDate(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int dpToPx(int dp) {
        return Math.round(getResources().getDisplayMetrics().density * dp);
    }

    private void drawPickerLines(Dialog dialog) {
        View frame = dialog.findViewById(R.id.timePickerFrame);
        if (frame == null) return;

        View topLine = dialog.findViewById(R.id.topDividerLine);
        View bottomLine = dialog.findViewById(R.id.bottomDividerLine);

        if (topLine == null || bottomLine == null) return;

        int frameHeight = frame.getHeight();
        if (frameHeight <= 0) return;

        int centerY = frameHeight / 2;
        int gapHalf = dpToPx(37);  // 선택 영역 절반 높이
        int lineHeight = dpToPx(2);

        topLine.setY(centerY - gapHalf - (lineHeight / 2f));
        bottomLine.setY(centerY + gapHalf - (lineHeight / 2f));
    }

    private void adjustPickerOverlay(Dialog dialog) {
        View frame = dialog.findViewById(R.id.timePickerFrame);
        if (frame == null) return;

        View topMask = dialog.findViewById(R.id.topDividerMask);
        View bottomMask = dialog.findViewById(R.id.bottomDividerMask);

        if (topMask == null || bottomMask == null) return;

        int frameHeight = frame.getHeight();
        if (frameHeight <= 0) return;

        int centerY = frameHeight / 2;
        int gapHalf = dpToPx(37);   // 선택 영역 높이 절반
        int maskHeight = dpToPx(12); // 기존 검정선 가릴 흰색 띠 높이

        topMask.setY(centerY - gapHalf - (maskHeight / 2f));
        bottomMask.setY(centerY + gapHalf - (maskHeight / 2f));
    }
}