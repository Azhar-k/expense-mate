package com.example.expensemate.util;

import androidx.annotation.Nullable;

import com.example.expensemate.data.BillPayment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Every bill's paid and skipped months, to look up by bill and month. */
public final class BillLedger {
    public static final BillLedger EMPTY = new BillLedger(Collections.emptyList());

    private final Map<Long, TreeMap<Integer, BillPayment>> byBill = new HashMap<>();

    private BillLedger(List<BillPayment> payments) {
        for (BillPayment payment : payments) {
            byBill.computeIfAbsent(payment.getBillId(), id -> new TreeMap<>()).put(payment.getPeriod(), payment);
        }
    }

    public static BillLedger of(@Nullable List<BillPayment> payments) {
        return payments == null ? EMPTY : new BillLedger(payments);
    }

    @Nullable
    public BillPayment get(long billId, int period) {
        TreeMap<Integer, BillPayment> months = byBill.get(billId);
        return months == null ? null : months.get(period);
    }

    /** Newest month first. */
    public List<BillPayment> history(long billId) {
        TreeMap<Integer, BillPayment> months = byBill.get(billId);
        return months == null ? new ArrayList<>() : new ArrayList<>(months.descendingMap().values());
    }

    /** The earliest month with a row, or 0. */
    public int firstPeriod(long billId) {
        TreeMap<Integer, BillPayment> months = byBill.get(billId);
        return months == null || months.isEmpty() ? 0 : months.firstKey();
    }
}
