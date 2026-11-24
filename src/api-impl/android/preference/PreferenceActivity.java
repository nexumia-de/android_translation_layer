package android.preference;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class PreferenceActivity extends ListActivity {

	public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";
	public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":android:show_fragment_args";
	public static final String EXTRA_SHOW_FRAGMENT_TITLE = ":android:show_fragment_title";

	private PreferenceScreen preferenceScreen;
	private final ArrayList<Header> headers = new ArrayList<>();
	private HeaderAdapter headerAdapter;

	public static final class Header {
		public CharSequence title;
		public CharSequence summary;
		public int iconRes;
		public String fragment;
		public Bundle fragmentArguments;
		public Intent intent;
		public Bundle extras;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			setContentView(com.android.internal.R.layout.preference_list_content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Intent intent = getIntent();
		String initialFragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
		Bundle initialArguments = intent.getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
		CharSequence initialTitle = intent.getCharSequenceExtra(EXTRA_SHOW_FRAGMENT_TITLE);
		if (initialFragment != null && !initialFragment.isEmpty()) {
			if (isValidFragment(initialFragment)) {
				switchToHeader(initialFragment, initialArguments);
				if (initialTitle != null)
					setTitle(initialTitle);
			}
		} else if (hasHeaders()) {
			onBuildHeaders(headers);
			if (!headers.isEmpty()) {
				showHeaders();
				onHeaderLoaded(headers);
			}
		}
	}

	protected boolean hasHeaders() {
		return true;
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	public void loadHeadersFromResource(int resid, List<Header> target) {
		try (XmlResourceParser parser = getResources().getXml(resid)) {
			AttributeSet attrs = Xml.asAttributeSet(parser);
			int type;
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG) {
			}
			String nodeName = parser.getName();
			if (!"preference-headers".equals(nodeName))
				throw new RuntimeException("XML document must start with <preference-headers> tag; found " + nodeName);

			final int outerDepth = parser.getDepth();
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
			       && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
				if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT)
					continue;
				nodeName = parser.getName();
				if ("header".equals(nodeName)) {
					Header header = new Header();
					TypedArray sa = obtainStyledAttributes(attrs, com.android.internal.R.styleable.PreferenceHeader);
					try {
						header.title = sa.getText(com.android.internal.R.styleable.PreferenceHeader_title);
						header.summary = sa.getText(com.android.internal.R.styleable.PreferenceHeader_summary);
						header.iconRes = sa.getResourceId(com.android.internal.R.styleable.PreferenceHeader_icon, 0);
						header.fragment = sa.getString(com.android.internal.R.styleable.PreferenceHeader_fragment);
					} finally {
						sa.recycle();
					}
					final int innerDepth = parser.getDepth();
					while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
					       && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
						if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT)
							continue;
						String innerName = parser.getName();
						if ("extra".equals(innerName)) {
							getResources().parseBundleExtra("extra", attrs, header.fragmentArguments == null ? (header.fragmentArguments = new Bundle()) : header.fragmentArguments);
							com.android.internal.util.XmlUtils.skipCurrentTag(parser);
						} else if ("intent".equals(innerName)) {
							header.intent = Intent.parseIntent(getResources(), parser, attrs);
						} else {
							com.android.internal.util.XmlUtils.skipCurrentTag(parser);
						}
					}
					target.add(header);
				} else {
					com.android.internal.util.XmlUtils.skipCurrentTag(parser);
				}
			}
		} catch (XmlPullParserException | IOException e) {
			throw new RuntimeException("Error parsing headers", e);
		}
	}

	public void onBuildHeaders(List<Header> target) {
	}

	protected void onHeaderLoaded(List<Header> headers) {
	}

	protected boolean isValidFragment(String fragmentName) {
		return true;
	}

	public Header onGetInitialHeader() {
		return headers.isEmpty() ? null : headers.get(0);
	}

	public Header onGetNewHeader() {
		return null;
	}

	private void showHeaders() {
		if (headerAdapter == null)
			headerAdapter = new HeaderAdapter(this, headers);
		ListView listView = getListView();
		if (listView != null)
			setListAdapter(headerAdapter);
	}

	public void switchToHeader(String fragmentName, Bundle args) {
		switchToHeaderInner(fragmentName, args);
	}

	public void switchToHeader(Header header) {
		if (header.fragment == null && header.intent != null) {
			startActivity(header.intent);
			return;
		}
		if (header.fragment != null) {
			switchToHeaderInner(header.fragment, header.fragmentArguments);
			if (header.title != null)
				setTitle(header.title);
		}
	}

	private void switchToHeaderInner(String fragmentName, Bundle args) {
		if (!isValidFragment(fragmentName))
			throw new IllegalArgumentException("Invalid fragment for this activity: " + fragmentName);
		Fragment f = Fragment.instantiate(this, fragmentName, args);
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		int containerId = com.android.internal.R.id.prefs;
		View prefs = findViewById(containerId);
		if (prefs == null) {
			containerId = android.R.id.content;
		} else {
			View prefsContainer = findViewById(com.android.internal.R.id.prefs_container);
			if (prefsContainer != null)
				prefsContainer.setVisibility(View.VISIBLE);
		}
		transaction.replace(containerId, f);
		transaction.commit();
	}

	public void addPreferencesFromResource(int preferencesResId) {
		preferenceScreen = new PreferenceManager(this).inflateFromResource(this, preferencesResId, preferenceScreen);
		bindPreferences();
	}

	public Preference findPreference(CharSequence key) {
		return preferenceScreen == null ? null : preferenceScreen.findPreference(key);
	}

	private void bindPreferences() {
		if (preferenceScreen != null) {
			ListView lv = getListView();
			if (lv != null)
				preferenceScreen.bind(lv);
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		if (headerAdapter != null && l.getAdapter() == headerAdapter) {
			Object item = headerAdapter.getItem(position);
			if (item instanceof Header)
				onHeaderClick((Header)item, position);
		}
	}

	public void onHeaderClick(Header header, int position) {
		switchToHeader(header);
	}

	private static class HeaderAdapter extends BaseAdapter {
		private final LayoutInflater inflater;
		private final List<Header> headers;
		private final Context context;

		HeaderAdapter(Context context, List<Header> headers) {
			this.context = context;
			this.inflater = LayoutInflater.from(context);
			this.headers = headers;
		}

		@Override
		public int getCount() {
			return headers.size();
		}

		@Override
		public Object getItem(int position) {
			return headers.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if (view == null)
				view = inflater.inflate(com.android.internal.R.layout.preference_header_item, parent, false);
			Header header = headers.get(position);
			TextView title = (TextView)view.findViewById(com.android.internal.R.id.title);
			TextView summary = (TextView)view.findViewById(com.android.internal.R.id.summary);
			ImageView icon = (ImageView)view.findViewById(com.android.internal.R.id.icon);
			title.setText(header.title);
			if (!TextUtils.isEmpty(header.summary)) {
				summary.setVisibility(View.VISIBLE);
				summary.setText(header.summary);
			} else {
				summary.setVisibility(View.GONE);
			}
			if (header.iconRes != 0) {
				icon.setVisibility(View.VISIBLE);
				icon.setImageResource(header.iconRes);
			} else {
				icon.setVisibility(View.GONE);
			}
			return view;
		}

		@Override
		public boolean isEnabled(int position) {
			return true;
		}
	}
}
