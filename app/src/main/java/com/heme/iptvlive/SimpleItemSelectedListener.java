package com.heme.iptvlive;

import android.view.View;
import android.widget.AdapterView;

final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    interface OnPositionSelected { void accept(int position); }
    private final OnPositionSelected consumer;
    SimpleItemSelectedListener(OnPositionSelected consumer) { this.consumer = consumer; }
    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { consumer.accept(position); }
    @Override public void onNothingSelected(AdapterView<?> parent) {}
}
