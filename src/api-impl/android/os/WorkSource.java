package android.os;

import java.util.ArrayList;

/**
 * The apps (uid and package) on whose behalf work is done. ATL has one app per process, so it
 * only carries the entries; the parcel layout follows AOSP (count, uids, names, no work chains)
 * because Google's client libraries put it into their location requests.
 */
public class WorkSource implements Parcelable {
	private final ArrayList<Integer> uids = new ArrayList<>();
	private final ArrayList<String> names = new ArrayList<>();

	public WorkSource() {}

	public WorkSource(int uid) {
		add(uid);
	}

	public WorkSource(int uid, String name) {
		add(uid, name);
	}

	public WorkSource(WorkSource other) {
		if (other != null) {
			uids.addAll(other.uids);
			names.addAll(other.names);
		}
	}

	public int size() {
		return uids.size();
	}

	public boolean isEmpty() {
		return uids.isEmpty();
	}

	public int getUid(int index) {
		return uids.get(index);
	}

	public String getPackageName(int index) {
		return names.get(index);
	}

	public void clear() {
		uids.clear();
		names.clear();
	}

	public boolean add(int uid) {
		return add(uid, null);
	}

	public boolean add(int uid, String name) {
		for (int i = 0; i < uids.size(); i++)
			if (uids.get(i) == uid && java.util.Objects.equals(names.get(i), name))
				return false;
		uids.add(uid);
		names.add(name);
		return true;
	}

	public boolean add(WorkSource other) {
		boolean changed = false;
		for (int i = 0; other != null && i < other.size(); i++)
			changed |= add(other.uids.get(i), other.names.get(i));
		return changed;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof WorkSource && uids.equals(((WorkSource)o).uids) && names.equals(((WorkSource)o).names);
	}

	@Override
	public int hashCode() {
		return uids.hashCode() * 31 + names.hashCode();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		int n = uids.size();
		dest.writeInt(n);
		int[] u = new int[n];
		for (int i = 0; i < n; i++)
			u[i] = uids.get(i);
		dest.writeIntArray(u);
		boolean named = false;
		for (String name : names)
			named |= name != null;
		dest.writeStringArray(named ? names.toArray(new String[0]) : null);
		dest.writeInt(-1); // no work chains
	}

	public static final Parcelable.Creator<WorkSource> CREATOR = new Parcelable.Creator<WorkSource>() {
		@Override
		public WorkSource createFromParcel(Parcel in) {
			WorkSource ws = new WorkSource();
			int n = in.readInt();
			int[] u = in.createIntArray();
			String[] s = in.createStringArray();
			for (int i = 0; u != null && i < u.length && i < n; i++) {
				ws.uids.add(u[i]);
				ws.names.add(s != null && i < s.length ? s[i] : null);
			}
			in.readInt(); // work chains
			return ws;
		}

		@Override
		public WorkSource[] newArray(int size) {
			return new WorkSource[size];
		}
	};
}
