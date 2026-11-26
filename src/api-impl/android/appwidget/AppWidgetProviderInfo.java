package android.appwidget;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

public class AppWidgetProviderInfo implements Parcelable {
	public ComponentName provider;
	public ComponentName configure;
	public int minWidth;
	public int minHeight;
	public int minResizeWidth;
	public int minResizeHeight;
	public int maxResizeWidth;
	public int maxResizeHeight;
	public int targetCellWidth;
	public int targetCellHeight;
	public int updatePeriodMillis;
	public int initialLayout;
	public int initialKeyguardLayout;
	public int previewImage;
	public int previewLayout;
	public int icon;
	public int resizeMode;
	public int widgetCategory;
	public int widgetFeatures;
	public String label;

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {}

	public static final Parcelable.Creator<AppWidgetProviderInfo> CREATOR = new Parcelable.Creator<AppWidgetProviderInfo>() {
		public AppWidgetProviderInfo createFromParcel(Parcel in) {
			return new AppWidgetProviderInfo();
		}

		public AppWidgetProviderInfo[] newArray(int size) {
			return new AppWidgetProviderInfo[size];
		}
	};
}
