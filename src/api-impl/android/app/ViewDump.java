package android.app;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import java.io.File;
import java.io.FileWriter;

/* DEBUG ONLY (not for merge): when the file $ATL_VIEWDUMP.req appears, write the view tree of the
 * resumed activity to $ATL_VIEWDUMP and delete the request */
class ViewDump {
	static Activity current;
	static java.util.ArrayList<Dialog> dialogs = new java.util.ArrayList<>();
	static boolean started;

	static void start() {
		final String base = System.getenv("ATL_VIEWDUMP");
		if (base == null || started)
			return;
		started = true;
		final Handler h = new Handler(Looper.getMainLooper());
		Thread t = new Thread(new Runnable() { public void run() {
			while (true) {
				try { Thread.sleep(500); } catch (InterruptedException e) {}
				File req = new File(base + ".req");
				if (req.exists()) {
					req.delete();
					h.post(new Runnable() { public void run() { dump(base); } });
				}
			}
		}});
		t.setDaemon(true);
		t.start();
	}

	static void dump(String base) {
		try (FileWriter w = new FileWriter(base)) {
			if (current == null) { w.write("no activity\n"); return; }
			w.write(current.getClass().getName() + "\n");
			walk(w, current.getWindow().getDecorView(), 0, 0, 0);
			for (Dialog d : dialogs) {
				w.write("=== dialog " + d.getClass().getName() + "\n");
				walk(w, d.getWindow().getDecorView(), 0, 0, 0);
			}
		} catch (Throwable e) { e.printStackTrace(); }
	}

	static String descr(android.graphics.drawable.Drawable d) {
		if (d == null) return "null";
		String r = d.getClass().getSimpleName() + d.getBounds().toShortString() + "i" + d.getIntrinsicWidth() + "x" + d.getIntrinsicHeight() + "a" + d.getAlpha() + (d.isVisible() ? "" : "INVIS");
		if (d instanceof android.graphics.drawable.LayerDrawable) {
			android.graphics.drawable.LayerDrawable l = (android.graphics.drawable.LayerDrawable)d;
			r += "{";
			for (int i = 0; i < l.getNumberOfLayers(); i++) r += descr(l.getDrawable(i)) + ";";
			r += "}";
		}
		if (d instanceof android.graphics.drawable.DrawableContainer)
			try { r += "<" + descr(d.getCurrent() != d ? d.getCurrent() : null) + ">"; } catch (Throwable e) {}
		return r;
	}

	static void walk(FileWriter w, View v, int depth, int px, int py) throws Exception {
		int x = px + v.getLeft() - (v.getParent() instanceof View ? ((View)v.getParent()).getScrollX() : 0);
		int y = py + v.getTop() - (v.getParent() instanceof View ? ((View)v.getParent()).getScrollY() : 0);
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < depth; i++) s.append("  ");
		s.append(v.getClass().getName());
		if (v.getId() > 0) {
			try { s.append(" #").append(v.getResources().getResourceEntryName(v.getId())); } catch (Throwable e) { s.append(" #").append(Integer.toHexString(v.getId())); }
		}
		s.append(" [").append(x).append(',').append(y).append(' ').append(v.getWidth()).append('x').append(v.getHeight()).append(']');
		if (v.getVisibility() != View.VISIBLE) s.append(v.getVisibility() == View.GONE ? " GONE" : " INVISIBLE");
		if (v.getAlpha() != 1) s.append(" a=").append(v.getAlpha());
		s.append(" pad=").append(v.getPaddingLeft()).append(',').append(v.getPaddingTop()).append(',').append(v.getPaddingRight()).append(',').append(v.getPaddingBottom());
		if (v.getBackground() != null) s.append(" bg=").append(v.getBackground().getClass().getSimpleName());
		if (v instanceof TextView) {
			TextView tv = (TextView)v;
			s.append(" text=\"").append(String.valueOf(tv.getText()).replace("\n", "\\n")).append("\" hint=\"").append(tv.getHint()).append("\" size=").append(tv.getTextSize()).append(" color=").append(Integer.toHexString(tv.getCurrentTextColor()));
		}
		if (v instanceof ImageView) {
			ImageView iv = (ImageView)v;
			s.append(" img=").append(iv.getDrawable() == null ? "null" : iv.getDrawable().getClass().getSimpleName());
			if (iv.getImageTintList() != null) s.append(" tint=").append(Integer.toHexString(iv.getImageTintList().getDefaultColor()));
			try {
				Object d = iv.getDrawable();
				java.lang.reflect.Field f = d.getClass().getDeclaredField("mVectorState"); f.setAccessible(true);
				Object st = f.get(d);
				java.lang.reflect.Field t = st.getClass().getDeclaredField("mTint"); t.setAccessible(true);
				android.content.res.ColorStateList c = (android.content.res.ColorStateList)t.get(st);
				s.append(" vtint=").append(c == null ? "null" : Integer.toHexString(c.getColorForState(iv.getDrawableState(), 0)));
			} catch (Throwable e) {}
			try {
				android.util.TypedValue tv2 = new android.util.TypedValue();
				boolean ok = v.getContext().getTheme().resolveAttribute(0x01010429, tv2, true);
				s.append(" ctx=").append(v.getContext().getClass().getSimpleName()).append(" ccn=").append(ok ? Integer.toHexString(tv2.data) + "/t" + tv2.type + "/r" + Integer.toHexString(tv2.resourceId) : "unresolved");
			} catch (Throwable e) { s.append(" ccnErr=" + e); }
		}
		try {
			java.lang.reflect.Method m = v.getClass().getMethod("getThumbDrawable");
			android.graphics.drawable.Drawable d = (android.graphics.drawable.Drawable)m.invoke(v);
			s.append(" thumb=").append(descr(d));
			m = v.getClass().getMethod("getTrackDrawable");
			s.append(" track=").append(descr((android.graphics.drawable.Drawable)m.invoke(v)));
		} catch (Throwable e) {}
		w.write(s.append('\n').toString());
		if (v instanceof ViewGroup && v.getVisibility() != View.GONE) {
			ViewGroup g = (ViewGroup)v;
			for (int i = 0; i < g.getChildCount(); i++)
				walk(w, g.getChildAt(i), depth + 1, x, y);
		}
	}
}
