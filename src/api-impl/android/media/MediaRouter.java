package android.media;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Display;
import java.util.ArrayList;
import java.util.List;

/* the routes of a phone without cast devices: one system route for local playback; apps may add
 * user routes of their own. The state is shared by all instances, as with the system service. */
public class MediaRouter {
	public static final int ROUTE_TYPE_LIVE_AUDIO = 1 << 0;
	public static final int ROUTE_TYPE_LIVE_VIDEO = 1 << 1;
	public static final int ROUTE_TYPE_REMOTE_DISPLAY = 1 << 2;
	public static final int ROUTE_TYPE_USER = 1 << 23;

	public static final int CALLBACK_FLAG_PERFORM_ACTIVE_SCAN = 1 << 0;
	public static final int CALLBACK_FLAG_UNFILTERED_EVENTS = 1 << 1;
	public static final int CALLBACK_FLAG_REQUEST_DISCOVERY = 1 << 2;
	public static final int CALLBACK_FLAG_PASSIVE_DISCOVERY = 1 << 3;

	private static final Object lock = new Object();
	private static final RouteCategory systemCategory = new RouteCategory("System", ROUTE_TYPE_LIVE_AUDIO | ROUTE_TYPE_LIVE_VIDEO, false);
	private static final RouteInfo defaultRoute = new RouteInfo(systemCategory);
	private static final List<RouteInfo> routes = new ArrayList<>();
	private static final List<RouteCategory> categories = new ArrayList<>();
	private static final List<CallbackRecord> callbacks = new ArrayList<>();
	private static RouteInfo selectedRoute = defaultRoute;

	static {
		systemCategory.system = true;
		defaultRoute.name = "Phone";
		defaultRoute.supportedTypes = ROUTE_TYPE_LIVE_AUDIO | ROUTE_TYPE_LIVE_VIDEO;
		defaultRoute.volumeHandling = RouteInfo.PLAYBACK_VOLUME_VARIABLE;
		routes.add(defaultRoute);
		categories.add(systemCategory);
	}

	private static class CallbackRecord {
		final int types;
		final Callback callback;

		CallbackRecord(int types, Callback callback) {
			this.types = types;
			this.callback = callback;
		}
	}

	public MediaRouter() {}

	public MediaRouter(Context context) {}

	public RouteInfo getDefaultRoute() {
		return defaultRoute;
	}

	public RouteCategory getSystemCategory() {
		return systemCategory;
	}

	public RouteInfo getSelectedRoute(int type) {
		synchronized (lock) {
			return (selectedRoute.supportedTypes & type) != 0 ? selectedRoute : defaultRoute;
		}
	}

	public void addCallback(int types, Callback cb) {
		addCallback(types, cb, 0);
	}

	public void addCallback(int types, Callback cb, int flags) {
		synchronized (lock) {
			for (CallbackRecord record : callbacks)
				if (record.callback == cb)
					return;
			callbacks.add(new CallbackRecord(types, cb));
		}
	}

	public void removeCallback(Callback cb) {
		synchronized (lock) {
			for (java.util.Iterator<CallbackRecord> it = callbacks.iterator(); it.hasNext();)
				if (it.next().callback == cb)
					it.remove();
		}
	}

	private List<CallbackRecord> callbacksFor(RouteInfo route) {
		List<CallbackRecord> list = new ArrayList<>();
		synchronized (lock) {
			for (CallbackRecord record : callbacks)
				if ((record.types & route.supportedTypes) != 0)
					list.add(record);
		}
		return list;
	}

	public void selectRoute(int types, RouteInfo route) {
		if (route == null)
			throw new IllegalArgumentException("Route cannot be null.");
		RouteInfo old;
		synchronized (lock) {
			old = selectedRoute;
			if (old == route)
				return;
			selectedRoute = route;
		}
		for (CallbackRecord record : callbacksFor(old))
			record.callback.onRouteUnselected(this, types, old);
		for (CallbackRecord record : callbacksFor(route))
			record.callback.onRouteSelected(this, types, route);
	}

	public void addUserRoute(UserRouteInfo info) {
		synchronized (lock) {
			if (routes.contains(info))
				return;
			routes.add(info);
			if (!categories.contains(info.category))
				categories.add(info.category);
		}
		for (CallbackRecord record : callbacksFor(info))
			record.callback.onRouteAdded(this, info);
	}

	public void removeUserRoute(UserRouteInfo info) {
		synchronized (lock) {
			if (!routes.remove(info))
				return;
		}
		if (getSelectedRoute(info.supportedTypes) == info || selectedRoute == info)
			selectRoute(ROUTE_TYPE_LIVE_AUDIO | ROUTE_TYPE_LIVE_VIDEO, defaultRoute);
		for (CallbackRecord record : callbacksFor(info))
			record.callback.onRouteRemoved(this, info);
	}

	public void clearUserRoutes() {
		List<RouteInfo> user = new ArrayList<>();
		synchronized (lock) {
			for (RouteInfo route : routes)
				if (route instanceof UserRouteInfo)
					user.add(route);
		}
		for (RouteInfo route : user)
			removeUserRoute((UserRouteInfo)route);
	}

	public int getRouteCount() {
		synchronized (lock) {
			return routes.size();
		}
	}

	public RouteInfo getRouteAt(int index) {
		synchronized (lock) {
			return routes.get(index);
		}
	}

	public int getCategoryCount() {
		synchronized (lock) {
			return categories.size();
		}
	}

	public RouteCategory getCategoryAt(int index) {
		synchronized (lock) {
			return categories.get(index);
		}
	}

	public RouteCategory createRouteCategory(CharSequence name, boolean isGroupable) {
		return new RouteCategory(name, ROUTE_TYPE_USER, isGroupable);
	}

	public RouteCategory createRouteCategory(int nameResId, boolean isGroupable) {
		RouteCategory category = new RouteCategory(null, ROUTE_TYPE_USER, isGroupable);
		category.nameResId = nameResId;
		return category;
	}

	public UserRouteInfo createUserRoute(RouteCategory category) {
		return new UserRouteInfo(category);
	}

	void routeChanged(RouteInfo route) {
		for (CallbackRecord record : callbacksFor(route))
			record.callback.onRouteChanged(this, route);
	}

	void routeVolumeChanged(RouteInfo route) {
		for (CallbackRecord record : callbacksFor(route))
			record.callback.onRouteVolumeChanged(this, route);
	}

	public static abstract class Callback {
		public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {}
		public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {}
		public void onRouteAdded(MediaRouter router, RouteInfo info) {}
		public void onRouteRemoved(MediaRouter router, RouteInfo info) {}
		public void onRouteChanged(MediaRouter router, RouteInfo info) {}
		public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group, int index) {}
		public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {}
		public void onRouteVolumeChanged(MediaRouter router, RouteInfo info) {}
		public void onRoutePresentationDisplayChanged(MediaRouter router, RouteInfo info) {}
	}

	public static class SimpleCallback extends Callback {}

	public static abstract class VolumeCallback {
		public abstract void onVolumeUpdateRequest(RouteInfo info, int direction);
		public abstract void onVolumeSetRequest(RouteInfo info, int volume);
	}

	public static class RouteCategory {
		CharSequence name;
		int nameResId;
		final int types;
		final boolean groupable;
		boolean system;

		RouteCategory(CharSequence name, int types, boolean groupable) {
			this.name = name;
			this.types = types;
			this.groupable = groupable;
		}

		public CharSequence getName() {
			return name;
		}

		public CharSequence getName(Context context) {
			if (nameResId != 0 && context != null)
				return context.getText(nameResId);
			return name;
		}

		public List<RouteInfo> getRoutes(List<RouteInfo> out) {
			if (out == null)
				out = new ArrayList<>();
			else
				out.clear();
			synchronized (lock) {
				for (RouteInfo route : routes)
					if (route.category == this)
						out.add(route);
			}
			return out;
		}

		public int getSupportedTypes() {
			return types;
		}

		public boolean isGroupable() {
			return groupable;
		}

		public boolean isSystem() {
			return system;
		}
	}

	public static class RouteInfo {
		public static final int PLAYBACK_TYPE_LOCAL = 0;
		public static final int PLAYBACK_TYPE_REMOTE = 1;
		public static final int PLAYBACK_VOLUME_FIXED = 0;
		public static final int PLAYBACK_VOLUME_VARIABLE = 1;
		public static final int DEVICE_TYPE_UNKNOWN = 0;
		public static final int DEVICE_TYPE_TV = 1;
		public static final int DEVICE_TYPE_SPEAKER = 2;
		public static final int DEVICE_TYPE_BLUETOOTH = 3;

		final RouteCategory category;
		CharSequence name;
		int nameResId;
		CharSequence description;
		CharSequence status;
		Drawable icon;
		int supportedTypes;
		int playbackType = PLAYBACK_TYPE_LOCAL;
		int playbackStream = AudioManager.STREAM_MUSIC;
		int volume = 15;
		int volumeMax = 15;
		int volumeHandling = PLAYBACK_VOLUME_FIXED;
		int deviceType = DEVICE_TYPE_UNKNOWN;
		Object tag;
		VolumeCallback volumeCallback;

		RouteInfo(RouteCategory category) {
			this.category = category;
		}

		public CharSequence getName() {
			return name;
		}

		public CharSequence getName(Context context) {
			if (nameResId != 0 && context != null)
				return context.getText(nameResId);
			return name;
		}

		public CharSequence getDescription() {
			return description;
		}

		public CharSequence getStatus() {
			return status;
		}

		public int getSupportedTypes() {
			return supportedTypes;
		}

		public boolean matchesTypes(int types) {
			return (supportedTypes & types) != 0;
		}

		public RouteGroup getGroup() {
			return null;
		}

		public RouteCategory getCategory() {
			return category;
		}

		public Drawable getIconDrawable() {
			return icon;
		}

		public void setTag(Object tag) {
			this.tag = tag;
		}

		public Object getTag() {
			return tag;
		}

		public int getPlaybackType() {
			return playbackType;
		}

		public int getPlaybackStream() {
			return playbackStream;
		}

		public int getVolume() {
			return volume;
		}

		public void requestSetVolume(int volume) {
			if (volumeCallback != null)
				volumeCallback.onVolumeSetRequest(this, volume);
		}

		public void requestUpdateVolume(int direction) {
			if (volumeCallback != null)
				volumeCallback.onVolumeUpdateRequest(this, direction);
		}

		public int getVolumeMax() {
			return volumeMax;
		}

		public int getVolumeHandling() {
			return volumeHandling;
		}

		public int getDeviceType() {
			return deviceType;
		}

		public Display getPresentationDisplay() {
			return new Display();
		}

		public boolean isEnabled() {
			return true;
		}

		public boolean isConnecting() {
			return false;
		}

		@Override
		public String toString() {
			return "RouteInfo{ name=" + name + ", types=" + supportedTypes + " }";
		}
	}

	public static class UserRouteInfo extends RouteInfo {
		UserRouteInfo(RouteCategory category) {
			super(category);
			supportedTypes = ROUTE_TYPE_USER;
			playbackType = PLAYBACK_TYPE_REMOTE;
		}

		private void changed() {
			if (routes.contains(this))
				new MediaRouter().routeChanged(this);
		}

		public void setName(CharSequence name) {
			this.name = name;
			nameResId = 0;
			changed();
		}

		public void setName(int resId) {
			nameResId = resId;
			name = null;
			changed();
		}

		public void setDescription(CharSequence description) {
			this.description = description;
			changed();
		}

		public void setStatus(CharSequence status) {
			this.status = status;
			changed();
		}

		public void setRemoteControlClient(Object rcc) {}

		public Object getRemoteControlClient() {
			return null;
		}

		public void setIconDrawable(Drawable icon) {
			this.icon = icon;
		}

		public void setIconResource(int resId) {}

		public void setVolumeCallback(VolumeCallback vcb) {
			volumeCallback = vcb;
		}

		public void setPlaybackType(int type) {
			playbackType = type;
		}

		public void setVolumeHandling(int volumeHandling) {
			this.volumeHandling = volumeHandling;
		}

		public void setVolume(int volume) {
			volume = Math.max(0, Math.min(volume, volumeMax));
			if (this.volume != volume) {
				this.volume = volume;
				if (routes.contains(this))
					new MediaRouter().routeVolumeChanged(this);
			}
		}

		public void setVolumeMax(int volumeMax) {
			this.volumeMax = volumeMax;
		}

		public void setPlaybackStream(int stream) {
			playbackStream = stream;
		}
	}

	public static class RouteGroup extends RouteInfo {
		private final List<RouteInfo> members = new ArrayList<>();

		RouteGroup(RouteCategory category) {
			super(category);
		}

		public void addRoute(RouteInfo route) {
			members.add(route);
		}

		public void addRoute(RouteInfo route, int insertAt) {
			members.add(insertAt, route);
		}

		public void removeRoute(RouteInfo route) {
			members.remove(route);
		}

		public void removeRoute(int index) {
			members.remove(index);
		}

		public int getRouteCount() {
			return members.size();
		}

		public RouteInfo getRouteAt(int index) {
			return members.get(index);
		}

		public void setIconDrawable(Drawable icon) {
			this.icon = icon;
		}

		public void setIconResource(int resId) {}
	}
}
