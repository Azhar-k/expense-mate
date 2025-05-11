package com.example.expensemate.functional;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.matcher.BoundedMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class RecyclerViewItemCountMatcher {
    public static Matcher<View> withItemCount(final int expectedCount) {
        return new BoundedMatcher<View, RecyclerView>(RecyclerView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("RecyclerView should have " + expectedCount + " items");
            }

            @Override
            protected boolean matchesSafely(RecyclerView recyclerView) {
                RecyclerView.Adapter adapter = recyclerView.getAdapter();
                return adapter != null && adapter.getItemCount() == expectedCount;
            }
        };
    }
}