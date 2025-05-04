package com.example.expensemate.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DatePickerHelper {
    private final Context context;
    private final SimpleDateFormat dateFormat;
    private Date selectedDate;

    public DatePickerHelper(Context context) {
        this.context = context;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    public void showDatePicker(TextView textView, Date initialDate) {
        Calendar calendar = Calendar.getInstance();
        if (initialDate != null) {
            calendar.setTime(initialDate);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
            context,
            (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                selectedDate = calendar.getTime();
                textView.setText(dateFormat.format(selectedDate));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    public Date getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(Date date) {
        this.selectedDate = date;
    }
} 