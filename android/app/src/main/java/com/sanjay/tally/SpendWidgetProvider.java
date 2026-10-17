package com.sanjay.tally;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;

public final class SpendWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, SpendWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) update(context, manager, id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        double total = 0;
        String currency = "INR";
        try {
            String raw = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                    .getString(MainActivity.STATE, "");
            if (!raw.isEmpty()) {
                JSONObject state = new JSONObject(raw);
                currency = state.optString("cur", "INR");
                JSONArray tx = state.optJSONArray("tx");
                String month = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
                if (tx != null) for (int i = 0; i < tx.length(); i++) {
                    JSONObject item = tx.optJSONObject(i);
                    if (item != null && item.optString("date", "").startsWith(month))
                        total += item.optDouble("amt", 0);
                }
            }
        } catch (Exception ignored) { }

        Locale locale = "INR".equals(currency) ? new Locale("en", "IN") : Locale.getDefault();
        String formatted;
        try {
            NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
            formatter.setCurrency(Currency.getInstance(currency));
            formatted = formatter.format(total);
        } catch (Exception ignored) {
            formatted = String.format(Locale.getDefault(), "%.2f %s", total, currency);
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.spend_widget);
        views.setTextViewText(R.id.widget_total, formatted);
        views.setTextViewText(R.id.widget_label, "THIS MONTH");
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        views.setOnClickPendingIntent(R.id.widget_total, pending);
        views.setOnClickPendingIntent(R.id.widget_label, pending);
        views.setOnClickPendingIntent(R.id.widget_hint, pending);
        manager.updateAppWidget(id, views);
    }
}
