package com.example.expensemate.util;

import androidx.annotation.Nullable;

import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.Transaction;

import java.util.List;

/** Finds where a pattern being written repeats or overlaps the ones already saved. */
public final class PatternChecks {
    private PatternChecks() {
    }

    /** A saved pattern that reads a message, and what it reads from it. */
    public static final class Overlap {
        public final MessagePattern pattern;
        public final Transaction reading;
        /** True if it is tried before the pattern being written, so it wins for this message. */
        public final boolean winsOverThis;

        Overlap(MessagePattern pattern, Transaction reading, boolean winsOverThis) {
            this.pattern = pattern;
            this.reading = reading;
            this.winsOverThis = winsOverThis;
        }
    }

    /**
     * @param saved  every saved pattern, in the order they are tried
     * @param selfId the id of the pattern being edited, or 0 for a new one
     * @return another pattern with the same expression, or null
     */
    @Nullable
    public static MessagePattern sameExpression(List<MessagePattern> saved, String regex, long selfId) {
        String text = regex.trim();
        for (MessagePattern other : saved) {
            if (other.getId() != selfId && other.getPattern().trim().equals(text)) {
                return other;
            }
        }
        return null;
    }

    /** @return another pattern with the same name, ignoring case, or null */
    @Nullable
    public static MessagePattern sameName(List<MessagePattern> saved, String name, long selfId) {
        String text = name.trim();
        for (MessagePattern other : saved) {
            if (other.getId() != selfId && other.getName().trim().equalsIgnoreCase(text)) {
                return other;
            }
        }
        return null;
    }

    /**
     * The first other pattern that is on and reads the sample. A new pattern goes first, so for
     * one the overlap never wins; an edited one is beaten by any pattern tried before it.
     *
     * @return the overlap, or null if no other pattern reads the sample
     */
    @Nullable
    public static Overlap firstOtherReader(List<MessagePattern> saved, String sample, long selfId) {
        if (sample == null || sample.trim().isEmpty()) {
            return null;
        }
        boolean beforeSelf = selfId != 0;
        for (MessagePattern other : saved) {
            if (other.getId() == selfId) {
                beforeSelf = false;
                continue;
            }
            if (!other.isActive()) {
                continue;
            }
            Transaction reading = SmsTransactionHandler.readWith(other, sample, "");
            if (reading != null) {
                return new Overlap(other, reading, beforeSelf);
            }
        }
        return null;
    }
}
