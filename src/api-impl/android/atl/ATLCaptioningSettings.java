package android.atl;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.accessibility.CaptioningManager;

/* What Android's Settings app shows for Settings.ACTION_CAPTIONING_SETTINGS ("Caption
 * preferences"): show captions, text size and caption style, as dialogs over the app. Apps such
 * as Crunchyroll only offer their caption style entry when this action resolves. */
public final class ATLCaptioningSettings {
	public static final String ACTION = "android.settings.CAPTIONING_SETTINGS";

	/* Android's choices (Settings app, captioning_font_size_selector and captioning_preset_selector) */
	private static final String[] SIZE_TITLES = {"Very small", "Small", "Default", "Large", "Very large"};
	private static final float[] SIZE_VALUES = {0.25f, 0.5f, 1.0f, 1.5f, 2.0f};
	private static final String[] STYLE_TITLES = {"White on black", "Black on white", "Yellow on black", "Yellow on blue"};

	private ATLCaptioningSettings() {}

	/* onDone runs when the screen is left with Done */
	public static void show(final Context context, final Runnable onDone) {
		final boolean enabled = CaptioningManager.isEnabledSetting();
		final float scale = CaptioningManager.getFontScaleSetting();
		final int preset = CaptioningManager.getPresetSetting();
		String[] rows = {
		    "Show captions: " + (enabled ? "On" : "Off"),
		    "Text size: " + SIZE_TITLES[sizeIndex(scale)],
		    "Caption style: " + STYLE_TITLES[preset],
		};
		new AlertDialog.Builder(context)
		    .setTitle("Caption preferences")
		    .setItems(rows, new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    dialog.dismiss();
				    switch (which) {
					    case 0:
						    CaptioningManager.putSettings(!enabled, scale, preset);
						    show(context, onDone);
						    break;
					    case 1:
						    choose(context, "Text size", SIZE_TITLES, new Choice() {
							    public void chosen(int i) {
								    CaptioningManager.putSettings(enabled, SIZE_VALUES[i], preset);
							    }
						    }, onDone);
						    break;
					    case 2:
						    choose(context, "Caption style", STYLE_TITLES, new Choice() {
							    public void chosen(int i) {
								    CaptioningManager.putSettings(enabled, scale, i);
							    }
						    }, onDone);
						    break;
				    }
			    }
		    })
		    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    if (onDone != null)
					    onDone.run();
			    }
		    })
		    .show();
	}

	private interface Choice {
		void chosen(int index);
	}

	private static void choose(final Context context, String title, String[] items, final Choice choice, final Runnable onDone) {
		new AlertDialog.Builder(context)
		    .setTitle(title)
		    .setItems(items, new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    dialog.dismiss();
				    choice.chosen(which);
				    show(context, onDone);
			    }
		    })
		    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				    show(context, onDone);
			    }
		    })
		    .show();
	}

	private static int sizeIndex(float scale) {
		int best = 2;
		for (int i = 0; i < SIZE_VALUES.length; i++) {
			if (Math.abs(SIZE_VALUES[i] - scale) < Math.abs(SIZE_VALUES[best] - scale))
				best = i;
		}
		return best;
	}
}
