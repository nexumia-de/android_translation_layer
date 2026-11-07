package android.os;

import java.util.ArrayList;

/* uids with optional package names, as apps pass it along with location and wake lock requests */
public class WorkSource implements Parcelable {

	private final ArrayList<Integer> uids = new ArrayList<>();
	private final ArrayList<String> names = new ArrayList<>();

	public WorkSource() {}

	public WorkSource(WorkSource other) {
		if (other != null)
			add(other);
	}

	public WorkSource(int uid) {
		add(uid);
	}

	public WorkSource(int uid, String name) {
		add(uid, name);
	}

	public int size() {
		return uids.size();
	}

	public int get(int index) {
		return uids.get(index);
	}

	public String getName(int index) {
		return names.get(index);
	}

	public boolean isEmpty() {
		return uids.isEmpty();
	}

	public void clear() {
		uids.clear();
		names.clear();
	}

	public void set(WorkSource other) {
		clear();
		if (other != null)
			add(other);
	}

	public void set(int uid) {
		clear();
		add(uid);
	}

	public void set(int uid, String name) {
		clear();
		add(uid, name);
	}

	public boolean add(WorkSource other) {
		boolean changed = false;
		for (int i = 0; i < other.size(); i++)
			changed |= add(other.get(i), other.getName(i));
		return changed;
	}

	public boolean add(int uid) {
		return add(uid, null);
	}

	public boolean add(int uid, String name) {
		for (int i = 0; i < uids.size(); i++)
			if (uids.get(i) == uid && (name == null ? names.get(i) == null : name.equals(names.get(i))))
				return false;
		uids.add(uid);
		names.add(name);
		return true;
	}

	public boolean remove(WorkSource other) {
		boolean changed = false;
		for (int i = 0; i < other.size(); i++) {
			for (int j = 0; j < uids.size(); j++) {
				String n = names.get(j);
				if (uids.get(j) == other.get(i) && (n == null ? other.getName(i) == null : n.equals(other.getName(i)))) {
					uids.remove(j);
					names.remove(j);
					changed = true;
					break;
				}
			}
		}
		return changed;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof WorkSource))
			return false;
		WorkSource other = (WorkSource)o;
		return uids.equals(other.uids) && names.equals(other.names);
	}

	@Override
	public int hashCode() {
		return uids.hashCode() * 31 + names.hashCode();
	}

	@Override
	public String toString() {
		return "WorkSource{" + uids + " " + names + "}";
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(uids.size());
		for (int i = 0; i < uids.size(); i++) {
			dest.writeInt(uids.get(i));
			dest.writeString(names.get(i));
		}
	}

	public static final Parcelable.Creator<WorkSource> CREATOR = new Parcelable.Creator<WorkSource>() {
		@Override
		public WorkSource createFromParcel(Parcel in) {
			WorkSource ws = new WorkSource();
			int n = in.readInt();
			for (int i = 0; i < n; i++)
				ws.add(in.readInt(), in.readString());
			return ws;
		}

		@Override
		public WorkSource[] newArray(int size) {
			return new WorkSource[size];
		}
	};
}
