package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class Preference implements Comparable<Preference> {
	public interface OnPreferenceChangeListener {
		boolean onPreferenceChange(Preference preference, Object newValue);
	}

	public interface OnPreferenceClickListener {
		boolean onPreferenceClick(Preference preference);
	}

	interface OnPreferenceChangeInternalListener {
		void onPreferenceChange(Preference preference);
		void onPreferenceHierarchyChange(Preference preference);
	}

	private long id;
	private Context context;
	private PreferenceManager preferenceManager;
	private PreferenceGroup parent;
	private String key;
	private CharSequence title;
	private CharSequence summary;
	private Intent intent;
	private String fragment;
	private Drawable icon;
	private int iconResId;
	private int order = DEFAULT_ORDER;
	private int layoutResId = com.android.internal.R.layout.preference;
	private int widgetLayoutResId;
	private boolean enabled = true;
	private boolean selectable = true;
	private boolean persistent = true;
	private String dependencyKey;
	private boolean dependencyMet = true;
	private boolean parentDependencyMet = true;
	private Object defaultValue;
	private boolean recycleEnabled = true;
	private OnPreferenceChangeListener onChangeListener;
	private OnPreferenceClickListener onClickListener;
	private OnPreferenceChangeInternalListener listener;

	public static final int DEFAULT_ORDER = Integer.MAX_VALUE;

	public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		this.context = context;
		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.Preference, defStyleAttr, defStyleRes);
			try {
				for (int i = a.getIndexCount() - 1; i >= 0; i--) {
					int attr = a.getIndex(i);
					switch (attr) {
						case com.android.internal.R.styleable.Preference_key:
							key = a.getString(attr);
							break;
						case com.android.internal.R.styleable.Preference_title:
							title = a.getText(attr);
							break;
						case com.android.internal.R.styleable.Preference_summary:
							summary = a.getText(attr);
							break;
						case com.android.internal.R.styleable.Preference_order:
							order = a.getInt(attr, order);
							break;
						case com.android.internal.R.styleable.Preference_fragment:
							fragment = a.getString(attr);
							break;
						case com.android.internal.R.styleable.Preference_layout:
							layoutResId = a.getResourceId(attr, layoutResId);
							break;
						case com.android.internal.R.styleable.Preference_widgetLayout:
							widgetLayoutResId = a.getResourceId(attr, widgetLayoutResId);
							break;
						case com.android.internal.R.styleable.Preference_enabled:
							enabled = a.getBoolean(attr, enabled);
							break;
						case com.android.internal.R.styleable.Preference_selectable:
							selectable = a.getBoolean(attr, selectable);
							break;
						case com.android.internal.R.styleable.Preference_persistent:
							persistent = a.getBoolean(attr, persistent);
							break;
						case com.android.internal.R.styleable.Preference_dependency:
							dependencyKey = a.getString(attr);
							break;
						case com.android.internal.R.styleable.Preference_defaultValue:
							defaultValue = onGetDefaultValue(a, attr);
							break;
						case com.android.internal.R.styleable.Preference_recycleEnabled:
							recycleEnabled = a.getBoolean(attr, recycleEnabled);
							break;

						default:
							break;
					}
				}
				iconResId = a.getResourceId(com.android.internal.R.styleable.Preference_icon, 0);
			} finally {
				a.recycle();
			}
		}
	}

	public Preference(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public Preference(Context context, AttributeSet attrs) {
		this(context, attrs, com.android.internal.R.attr.preferenceStyle);
	}

	public Preference(Context context) {
		this(context, null);
	}

	public Context getContext() {
		return context;
	}

	public void setIntent(Intent intent) {
		this.intent = intent;
	}

	public Intent getIntent() {
		return intent;
	}

	public void setFragment(String fragment) {
		this.fragment = fragment;
	}

	public String getFragment() {
		return fragment;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public boolean hasKey() {
		return key != null && !key.isEmpty();
	}

	public void setTitle(CharSequence title) {
		if ((title == null && this.title != null) || (title != null && !title.equals(this.title))) {
			this.title = title;
			notifyChanged();
		}
	}

	public void setTitle(int titleResId) {
		if (context != null)
			setTitle(context.getString(titleResId));
	}

	public CharSequence getTitle() {
		return title;
	}

	public void setSummary(CharSequence summary) {
		if ((summary == null && this.summary != null) || (summary != null && !summary.equals(this.summary))) {
			this.summary = summary;
			notifyChanged();
		}
	}

	public void setSummary(int summaryResId) {
		if (context != null)
			setSummary(context.getString(summaryResId));
	}

	public CharSequence getSummary() {
		return summary;
	}

	public void setIcon(Drawable icon) {
		this.icon = icon;
		notifyChanged();
	}

	public void setIcon(int iconResId) {
		this.iconResId = iconResId;
		this.icon = null;
		notifyChanged();
	}

	public Drawable getIcon() {
		return icon;
	}

	public void setOrder(int order) {
		if (order != this.order) {
			this.order = order;
			notifyHierarchyChanged();
		}
	}

	public int getOrder() {
		return order;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled != enabled) {
			this.enabled = enabled;
			notifyDependencyChange(shouldDisableDependents());
			notifyChanged();
		}
	}

	public boolean isEnabled() {
		return enabled && dependencyMet && parentDependencyMet;
	}

	public void setSelectable(boolean selectable) {
		if (this.selectable != selectable) {
			this.selectable = selectable;
			notifyChanged();
		}
	}

	public boolean isSelectable() {
		return selectable;
	}

	public boolean isRecycleEnabled() {
		return recycleEnabled;
	}

	public void setLayoutResource(int layoutResId) {
		this.layoutResId = layoutResId;
	}

	public int getLayoutResource() {
		return layoutResId;
	}

	public void setWidgetLayoutResource(int widgetLayoutResId) {
		this.widgetLayoutResId = widgetLayoutResId;
	}

	public int getWidgetLayoutResource() {
		return widgetLayoutResId;
	}

	public PreferenceGroup getParent() {
		return parent;
	}

	void assignParent(PreferenceGroup parent) {
		this.parent = parent;
	}

	public void setOnPreferenceChangeListener(OnPreferenceChangeListener onPreferenceChangeListener) {
		this.onChangeListener = onPreferenceChangeListener;
	}

	public OnPreferenceChangeListener getOnPreferenceChangeListener() {
		return onChangeListener;
	}

	public void setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener) {
		this.onClickListener = onPreferenceClickListener;
	}

	long getId() {
		return id;
	}

	public void performClick(PreferenceScreen preferenceScreen) {
		if (!isEnabled())
			return;
		onClick();
		if (onClickListener != null && onClickListener.onPreferenceClick(this))
			return;
		if (intent != null && context != null)
			context.startActivity(intent);
	}

	protected void onClick() {
	}

	public boolean callChangeListener(Object newValue) {
		return onChangeListener == null || onChangeListener.onPreferenceChange(this, newValue);
	}

	public SharedPreferences getSharedPreferences() {
		return preferenceManager != null ? preferenceManager.getSharedPreferences() : null;
	}

	protected boolean persistString(String value) {
		if (!shouldPersist())
			return false;
		if (value != null && value.equals(getPersistedString(null)))
			return true;
		SharedPreferences prefs = getSharedPreferences();
		if (prefs == null)
			return false;
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(key, value);
		editor.apply();
		return true;
	}

	protected String getPersistedString(String defaultReturnValue) {
		if (!shouldPersist())
			return defaultReturnValue;
		SharedPreferences prefs = getSharedPreferences();
		return prefs != null ? prefs.getString(key, defaultReturnValue) : defaultReturnValue;
	}

	protected boolean persistBoolean(boolean value) {
		if (!shouldPersist())
			return false;
		if (value == getPersistedBoolean(!value))
			return true;
		SharedPreferences prefs = getSharedPreferences();
		if (prefs == null)
			return false;
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(key, value);
		editor.apply();
		return true;
	}

	protected boolean getPersistedBoolean(boolean defaultReturnValue) {
		if (!shouldPersist())
			return defaultReturnValue;
		SharedPreferences prefs = getSharedPreferences();
		return prefs != null ? prefs.getBoolean(key, defaultReturnValue) : defaultReturnValue;
	}

	public boolean shouldPersist() {
		return preferenceManager != null && persistent && hasKey();
	}

	protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;

		id = preferenceManager.getNextId();

		dispatchSetInitialValue();
	}

	protected void onAttachedToActivity() {
	}

	protected void onPrepareForRemoval() {
	}

	private void dispatchSetInitialValue() {
		// By now, we know if we are persistent.
		final boolean shouldPersist = shouldPersist();
		if (!shouldPersist || !getSharedPreferences().contains(key)) {
			if (defaultValue != null) {
				onSetInitialValue(false, defaultValue);
			}
		} else {
			onSetInitialValue(true, null);
		}
	}

	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
	}

	protected Object onGetDefaultValue(TypedArray a, int index) {
		return null;
	}

	public void notifyDependencyChange(boolean disableDependents) {
		/* notify registered dependents - not tracked yet */
	}

	public void onDependencyChanged(Preference dependency, boolean disableDependent) {
		if (this.dependencyMet == disableDependent) {
			this.dependencyMet = !disableDependent;
			notifyDependencyChange(shouldDisableDependents());
			notifyChanged();
		}
	}

	public void onParentChanged(Preference parent, boolean disableChild) {
		if (this.parentDependencyMet == disableChild) {
			this.parentDependencyMet = !disableChild;
			notifyDependencyChange(shouldDisableDependents());
			notifyChanged();
		}
	}

	public boolean shouldDisableDependents() {
		return !isEnabled();
	}

	public void setDependency(String dependencyKey) {
		this.dependencyKey = dependencyKey;
	}

	public String getDependency() {
		return dependencyKey;
	}

	void setOnPreferenceChangeInternalListener(OnPreferenceChangeInternalListener listener) {
		this.listener = listener;
	}

	protected void notifyChanged() {
		if (listener != null)
			listener.onPreferenceChange(this);
	}

	protected void notifyHierarchyChanged() {
		if (listener != null)
			listener.onPreferenceHierarchyChange(this);
	}

	public View getView(View convertView, ViewGroup parent) {
		if (convertView == null && context != null) {
			convertView = LayoutInflater.from(context).inflate(layoutResId, parent, false);
			if (convertView != null && widgetLayoutResId != 0) {
				ViewGroup widgetFrame = (ViewGroup)convertView.findViewById(com.android.internal.R.id.widget_frame);
				if (widgetFrame != null) {
					LayoutInflater.from(context).inflate(widgetLayoutResId, widgetFrame);
				}
			}
		}
		if (convertView == null)
			return convertView;
		onBindView(convertView);
		return convertView;
	}

	protected void onBindView(View view) {
		TextView titleView = (TextView)view.findViewById(com.android.internal.R.id.title);
		if (titleView != null) {
			CharSequence title = getTitle();
			if (title != null && title.length() > 0) {
				titleView.setText(title);
				titleView.setVisibility(View.VISIBLE);
			} else {
				titleView.setVisibility(View.GONE);
			}
		}
		TextView summaryView = (TextView)view.findViewById(com.android.internal.R.id.summary);
		if (summaryView != null) {
			CharSequence summary = getSummary();
			if (summary != null && summary.length() > 0) {
				summaryView.setText(summary);
				summaryView.setVisibility(View.VISIBLE);
			} else {
				summaryView.setVisibility(View.GONE);
			}
		}
		ImageView imageView = (ImageView)view.findViewById(com.android.internal.R.id.icon);
		if (imageView != null) {
			if (icon != null || iconResId != 0) {
				if (icon == null && context != null)
					icon = context.getDrawable(iconResId);
				imageView.setImageDrawable(icon);
				imageView.setVisibility(icon != null ? View.VISIBLE : View.GONE);
			} else {
				imageView.setVisibility(View.GONE);
			}
		}
		view.setEnabled(isEnabled());
	}

	public PreferenceManager getPreferenceManager() {
		return preferenceManager;
	}

	@Override
	public int compareTo(Preference another) {
		if (another == null)
			return -1;
		if (order != another.order)
			return order - another.order;
		if (title == another.title)
			return 0;
		if (title == null)
			return 1;
		if (another.title == null)
			return -1;
		return title.toString().compareToIgnoreCase(another.title.toString());
	}
}
