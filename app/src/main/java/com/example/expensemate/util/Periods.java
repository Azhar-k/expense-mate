package com.example.expensemate.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** A month as one number, year * 100 + month, so November 2026 is 202611. Sorting them sorts the months. */
public final class Periods {
    private Periods() {
    }

    public static int of(Calendar day) {
        return day.get(Calendar.YEAR) * 100 + day.get(Calendar.MONTH) + 1;
    }

    public static int of(Date date) {
        Calendar day = Calendar.getInstance();
        day.setTime(date);
        return of(day);
    }

    public static int current() {
        return of(Calendar.getInstance());
    }

    /** Midnight on the first day of the month, in the phone's time zone. */
    public static Calendar firstDay(int period) {
        Calendar day = Calendar.getInstance();
        day.clear();
        day.set(period / 100, period % 100 - 1, 1);
        return day;
    }

    public static int plus(int period, int months) {
        int index = period / 100 * 12 + period % 100 - 1 + months;
        return index / 12 * 100 + index % 12 + 1;
    }

    /** How many months {@code to} is after {@code from}; negative when it is before. */
    public static int between(int from, int to) {
        return (to / 100 * 12 + to % 100) - (from / 100 * 12 + from % 100);
    }

    /** "November", or "November 2025" when it is not this year. */
    public static String name(int period) {
        boolean thisYear = period / 100 == current() / 100;
        return new SimpleDateFormat(thisYear ? "MMMM" : "MMMM yyyy", Locale.getDefault())
                .format(firstDay(period).getTime());
    }

    /** "Nov". */
    public static String shortName(int period) {
        return new SimpleDateFormat("MMM", Locale.getDefault()).format(firstDay(period).getTime());
    }
}
