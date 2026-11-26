package android.appwidget;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.RemoteViews;
import java.util.Collections;
import java.util.List;

/* there is no home screen that hosts Android widgets, so no widget is ever placed */
public class AppWidgetManager {

	public static AppWidgetManager getInstance(Context context) {
		return new AppWidgetManager();
	}

	public int[] getAppWidgetIds(ComponentName provider) {
		return new int[0];
	}

	public List<AppWidgetProviderInfo> getInstalledProviders() {
		return Collections.emptyList();
	}

	public List<AppWidgetProviderInfo> getInstalledProvidersForPackage(String packageName, UserHandle profile) {
		return Collections.emptyList();
	}

	public AppWidgetProviderInfo getAppWidgetInfo(int appWidgetId) {
		return null;
	}

	public Bundle getAppWidgetOptions(int appWidgetId) {
		return new Bundle();
	}

	public void updateAppWidgetOptions(int appWidgetId, Bundle options) {}

	public void updateAppWidget(int appWidgetId, RemoteViews views) {}

	public void updateAppWidget(int[] appWidgetIds, RemoteViews views) {}

	public void updateAppWidget(ComponentName provider, RemoteViews views) {}

	public void partiallyUpdateAppWidget(int appWidgetId, RemoteViews views) {}

	public void partiallyUpdateAppWidget(int[] appWidgetIds, RemoteViews views) {}

	public void updateAppWidgetProviderInfo(ComponentName provider, String metaDataKey) {}

	public boolean isRequestPinAppWidgetSupported() {
		return false;
	}

	public boolean requestPinAppWidget(ComponentName provider, Bundle extras, PendingIntent successCallback) {
		return false;
	}

	public boolean setWidgetPreview(ComponentName provider, int widgetCategories, RemoteViews preview) {
		return false;
	}

	public void notifyAppWidgetViewDataChanged(int appWidgetId, int viewId) {}

	public void notifyAppWidgetViewDataChanged(int[] appWidgetIds, int viewId) {}
}
