package com.heme.iptvlive;

import android.view.View;
import android.widget.AdapterView;
import java.util.function.IntConsumer;

final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final IntConsumer consumer;
    SimpleItemSelectedListener(IntConsumer consumer) { this.consumer = consumer; }
    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { consumer.accept(position); }
    @Override public void onNothingSelected(AdapterView<?> parent) {}
}
