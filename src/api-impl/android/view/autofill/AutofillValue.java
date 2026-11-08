package android.view.autofill;

import android.view.View;

public final class AutofillValue {
	private final int type;
	private final Object value;

	private AutofillValue(int type, Object value) {
		this.type = type;
		this.value = value;
	}

	public static AutofillValue forText(CharSequence text) {
		return text == null ? null : new AutofillValue(View.AUTOFILL_TYPE_TEXT, text);
	}

	public static AutofillValue forToggle(boolean enabled) {
		return new AutofillValue(View.AUTOFILL_TYPE_TOGGLE, enabled);
	}

	public static AutofillValue forList(int value) {
		return new AutofillValue(View.AUTOFILL_TYPE_LIST, value);
	}

	public static AutofillValue forDate(long value) {
		return new AutofillValue(View.AUTOFILL_TYPE_DATE, value);
	}

	public CharSequence getTextValue() {
		if (!isText())
			throw new IllegalStateException("value not a text: " + this);
		return (CharSequence)value;
	}

	public boolean getToggleValue() {
		if (!isToggle())
			throw new IllegalStateException("value not a toggle: " + this);
		return (Boolean)value;
	}

	public int getListValue() {
		if (!isList())
			throw new IllegalStateException("value not a list: " + this);
		return (Integer)value;
	}

	public long getDateValue() {
		if (!isDate())
			throw new IllegalStateException("value not a date: " + this);
		return (Long)value;
	}

	public boolean isText() {
		return type == View.AUTOFILL_TYPE_TEXT;
	}

	public boolean isToggle() {
		return type == View.AUTOFILL_TYPE_TOGGLE;
	}

	public boolean isList() {
		return type == View.AUTOFILL_TYPE_LIST;
	}

	public boolean isDate() {
		return type == View.AUTOFILL_TYPE_DATE;
	}

	@Override
	public String toString() {
		return "[type=" + type + ", value=" + (isText() ? ((CharSequence)value).length() + "_chars" : value) + "]";
	}
}
