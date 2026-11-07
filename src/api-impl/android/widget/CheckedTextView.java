package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public class CheckedTextView extends TextView {

	public CheckedTextView(Context context) {
		super(context);
	}

	public CheckedTextView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	private boolean checked;

	public void setChecked(boolean checked) {
		if (this.checked != checked) {
			this.checked = checked;
			refreshDrawableState();
		}
	}

	public boolean isChecked() {
		return checked;
	}

	public void toggle() {
		setChecked(!checked);
	}

	public void setCheckMarkDrawable(Drawable d) {}
}
