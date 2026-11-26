package android.appwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Slog;

public class AppWidgetProvider extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Slog.i("AppWidgetProvider", "onReceive(" + intent + ") called");
	}

	public void onEnabled(Context context) {}

	public void onDisabled(Context context) {}

	public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {}

	public void onDeleted(Context context, int[] appWidgetIds) {}

	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {}

	public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {}
}
