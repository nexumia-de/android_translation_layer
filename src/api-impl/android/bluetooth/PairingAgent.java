package android.bluetooth;

import android.app.AlertDialog;
import android.atl.ATLLoadedApp;
import android.atl.annotation.Export;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;
import android.widget.EditText;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ATL internal, not Android API: a BlueZ pairing agent (org.bluez.Agent1),
 * so pairing works without a desktop agent such as the Bluetooth panel of
 * GNOME Settings.
 *
 * Like Android's stack, a request first goes to the app as the ordered
 * broadcast ACTION_PAIRING_REQUEST; an app that handles it calls
 * abortBroadcast() and answers with setPairingConfirmation() or setPin().
 * Otherwise ATL shows a dialog like Android's pairing dialog.
 *
 * The agent is registered only while this process pairs (createBond) or
 * connects, and removed a while later. BlueZ asks the agent of the client
 * that called Pair; requests the device starts itself go to the default
 * agent, which is this one only when no other agent is registered, so the
 * desktop's agent stays in charge when there is one.
 */
final class PairingAgent {
	private static final String TAG = "BluetoothPairing";
	static final String PATH = "/org/atl/bluetooth/agent";
	private static final long LINGER_MS = 30000;
	private static final String REJECTED = "org.bluez.Error.Rejected";
	private static final String CANCELED = "org.bluez.Error.Canceled";

	private static native boolean nativeExport(String path);
	private static native void nativeReply(long invocation, String error, Object value);

	private static final class Request {
		final long invocation;
		final String method;
		final String devicePath;
		final String address;
		AlertDialog dialog;
		boolean answered;

		Request(long invocation, String method, String devicePath, String address) {
			this.invocation = invocation;
			this.method = method;
			this.devicePath = devicePath;
			this.address = address;
		}
	}

	private static boolean exported;
	private static boolean registered;
	private static int users;
	private static int generation;
	private static Handler handler;
	/* pending requests by device address */
	private static final Map<String, Request> pending = new HashMap<>();
	/* the dialog showing the code to type on a device (DisplayPasskey), one per device address */
	private static final Map<String, AlertDialog> codeDialogs = new HashMap<>();

	private static synchronized Handler handler() {
		if (handler == null)
			handler = new Handler(Looper.getMainLooper());
		return handler;
	}

	/* called before Pair or Connect; pairs with release() */
	static void acquire() {
		synchronized (PairingAgent.class) {
			users++;
			generation++;
			if (registered)
				return;
			if (!exported) {
				exported = nativeExport(PATH);
				if (!exported)
					return;
			}
			try {
				BlueZ.call("/org/bluez", "org.bluez.AgentManager1", "RegisterAgent", "os", PATH, "KeyboardDisplay");
				registered = true;
			} catch (IOException e) {
				Slog.w(TAG, "RegisterAgent: " + e.getMessage());
			}
		}
	}

	static void release() {
		final int gen;
		synchronized (PairingAgent.class) {
			if (users > 0)
				users--;
			gen = generation;
		}
		handler().postDelayed(new Runnable() {
			public void run() {
				unregisterIfIdle(gen);
			}
		}, LINGER_MS);
	}

	private static void unregisterIfIdle(int gen) {
		synchronized (PairingAgent.class) {
			if (!registered || users > 0 || gen != generation || !pending.isEmpty())
				return;
			registered = false;
			try {
				BlueZ.call("/org/bluez", "org.bluez.AgentManager1", "UnregisterAgent", "o", PATH);
			} catch (IOException e) {
				Slog.w(TAG, "UnregisterAgent: " + e.getMessage());
			}
		}
	}

	/* answer from the app or the dialog; value is the PIN or passkey for RequestPinCode and RequestPasskey */
	static boolean answer(String address, boolean accept, String value) {
		Request request;
		synchronized (PairingAgent.class) {
			request = pending.get(address);
		}
		return request != null && answer(request, accept, value);
	}

	/* answers this request only; a newer request for the same device stays pending */
	private static boolean answer(Request request, boolean accept, String value) {
		synchronized (PairingAgent.class) {
			if (request.answered)
				return false;
			request.answered = true;
			if (pending.get(request.address) == request)
				pending.remove(request.address);
		}
		Object result = null;
		String error = accept ? null : REJECTED;
		if (accept && "RequestPinCode".equals(request.method)) {
			if (value == null || value.isEmpty())
				error = REJECTED;
			else
				result = value;
		} else if (accept && "RequestPasskey".equals(request.method)) {
			try {
				result = Integer.valueOf(Integer.parseInt(value == null ? "" : value.trim()));
			} catch (NumberFormatException e) {
				error = REJECTED;
			}
		}
		Slog.i(TAG, request.method + " for " + request.address + (error == null ? ": accepted" : ": rejected"));
		nativeReply(request.invocation, error, error == null ? result : null);
		dismiss(request);
		return true;
	}

	private static void dismiss(Request request) {
		final AlertDialog dialog;
		synchronized (PairingAgent.class) {
			dialog = request.dialog;
			request.dialog = null;
		}
		if (dialog != null)
			handler().post(new Runnable() {
				public void run() {
					dialog.dismiss();
				}
			});
	}

	/* pairing with the device ended (or BlueZ cancelled it): close its code dialog */
	static void pairingEnded(final String address) {
		handler().post(new Runnable() {
			public void run() {
				AlertDialog dialog;
				synchronized (PairingAgent.class) {
					dialog = address != null ? codeDialogs.remove(address) : null;
					if (address == null) {
						for (AlertDialog d : codeDialogs.values())
							d.dismiss();
						codeDialogs.clear();
					}
				}
				if (dialog != null)
					dialog.dismiss();
			}
		});
	}

	private static String name(String devicePath) {
		Object alias = BlueZ.getProperty(devicePath, BlueZ.DEVICE, "Alias");
		return alias instanceof String ? (String)alias : devicePath;
	}

	/* BlueZ calls this on the D-Bus thread; every invocation gets exactly one nativeReply */
	@Export
	private static void onRequest(final long invocation, final String method, final String devicePath, Object arg) {
		Slog.i(TAG, "agent: " + method + " " + devicePath);
		final BluetoothDevice device = devicePath != null ? BluetoothDevice.fromPath(devicePath) : null;
		switch (method) {
			case "RequestConfirmation":
				request(invocation, method, device, devicePath, BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION, arg instanceof Integer ? (Integer)arg : -1);
				return;
			case "RequestAuthorization":
				request(invocation, method, device, devicePath, BluetoothDevice.PAIRING_VARIANT_CONSENT, -1);
				return;
			case "RequestPinCode":
				request(invocation, method, device, devicePath, BluetoothDevice.PAIRING_VARIANT_PIN, -1);
				return;
			case "RequestPasskey":
				request(invocation, method, device, devicePath, BluetoothDevice.PAIRING_VARIANT_PASSKEY, -1);
				return;
			case "AuthorizeService":
				/* services of a paired device are allowed, as on Android */
				nativeReply(invocation, BlueZ.getBoolean(BlueZ.getProperty(devicePath, BlueZ.DEVICE, "Paired")) ? null : REJECTED, null);
				return;
			case "DisplayPasskey":
			case "DisplayPinCode":
				/* the code to type on the device; shown in a dialog until pairing ends */
				nativeReply(invocation, null, null);
				if (device != null && arg != null)
					showCode(device, devicePath, arg instanceof Integer ? String.format(Locale.ROOT, "%06d", (Integer)arg) : String.valueOf(arg));
				return;
			case "Cancel":
				nativeReply(invocation, null, null);
				Request[] all;
				synchronized (PairingAgent.class) {
					all = pending.values().toArray(new Request[0]);
				}
				for (Request r : all)
					answer(r, false, null);
				pairingEnded(null);
				return;
			default: /* Release */
				nativeReply(invocation, null, null);
		}
	}

	private static void request(long invocation, String method, final BluetoothDevice device, final String devicePath, final int variant, final int key) {
		if (device == null) {
			nativeReply(invocation, REJECTED, null);
			return;
		}
		final Request request = new Request(invocation, method, devicePath, device.getAddress());
		Request old;
		synchronized (PairingAgent.class) {
			old = pending.put(device.getAddress(), request);
			if (old != null && !old.answered)
				old.answered = true;
			else
				old = null;
		}
		if (old != null) {
			/* the device asked again: the old invocation is cancelled and its dialog closed */
			nativeReply(old.invocation, CANCELED, null);
			dismiss(old);
		}
		handler().post(new Runnable() {
			public void run() {
				ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
				if (app == null) {
					answer(request, false, null);
					return;
				}
				Context context = app.getApplication();
				Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
				intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
				intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, variant);
				if (key >= 0)
					intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, key);
				boolean handled = context.atlSendOrderedBroadcast(intent);
				synchronized (PairingAgent.class) {
					if (request.answered)
						return;
				}
				if (handled) {
					Slog.i(TAG, "the app handles the pairing request of " + device.getAddress());
					return;
				}
				showDialog(context, device, devicePath, request, variant, key);
			}
		});
	}

	private static void showDialog(Context context, final BluetoothDevice device, String devicePath, final Request request, final int variant, int key) {
		final AlertDialog dialog = new AlertDialog(context);
		final EditText input;
		String message;
		if (variant == BluetoothDevice.PAIRING_VARIANT_PIN || variant == BluetoothDevice.PAIRING_VARIANT_PASSKEY) {
			message = "Enter the code shown on " + name(devicePath) + " or in its manual.";
			input = new EditText(context);
			input.setInputType(variant == BluetoothDevice.PAIRING_VARIANT_PASSKEY ? 2 /* TYPE_CLASS_NUMBER */ : 1 /* TYPE_CLASS_TEXT */);
			dialog.setView(input);
		} else if (variant == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) {
			message = "Pair with " + name(devicePath) + "?\n\nCode: " + String.format(Locale.ROOT, "%06d", key) + "\n\nMake sure the device shows the same code.";
			input = null;
		} else {
			message = "Pair with " + name(devicePath) + "?";
			input = null;
		}
		dialog.setTitle("Bluetooth pairing request");
		dialog.setMessage(message);
		/* as Android's pairing dialog: only the buttons answer, a stray touch next to it does not reject */
		dialog.setCanceledOnTouchOutside(false);
		dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Pair", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface d, int which) {
				answer(request, true, input != null ? input.getText().toString() : null);
			}
		});
		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface d, int which) {
				answer(request, false, null);
			}
		});
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface d) {
				answer(request, false, null);
			}
		});
		synchronized (PairingAgent.class) {
			if (request.answered)
				return;
			request.dialog = dialog;
		}
		dialog.show();
	}

	private static void showCode(final BluetoothDevice device, final String devicePath, final String code) {
		handler().post(new Runnable() {
			public void run() {
				ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
				if (app == null)
					return;
				Context context = app.getApplication();
				Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
				intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
				intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY);
				if (context.atlSendOrderedBroadcast(intent))
					return;
				String message = "Type " + code + " on " + name(devicePath) + ", then confirm there.";
				/* BlueZ calls DisplayPasskey again for every key typed on the device: update the one dialog */
				AlertDialog dialog;
				synchronized (PairingAgent.class) {
					dialog = codeDialogs.get(device.getAddress());
				}
				if (dialog != null) {
					dialog.setMessage(message);
					return;
				}
				dialog = new AlertDialog(context);
				dialog.setTitle("Bluetooth pairing request");
				dialog.setMessage(message);
				dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", (DialogInterface.OnClickListener)null);
				final AlertDialog shown = dialog;
				dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface d) {
						synchronized (PairingAgent.class) {
							if (codeDialogs.get(device.getAddress()) == shown)
								codeDialogs.remove(device.getAddress());
						}
					}
				});
				synchronized (PairingAgent.class) {
					codeDialogs.put(device.getAddress(), dialog);
				}
				dialog.show();
			}
		});
	}
}
