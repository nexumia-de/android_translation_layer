package android.util;

import java.util.Objects;

public final class Range<T extends Comparable<? super T>> {

	private final T lower;
	private final T upper;

	public Range(T lower, T upper) {
		if (lower == null || upper == null)
			throw new NullPointerException("lower and upper must not be null");
		if (lower.compareTo(upper) > 0)
			throw new IllegalArgumentException("lower must be less than or equal to upper");
		this.lower = lower;
		this.upper = upper;
	}

	public static <T extends Comparable<? super T>> Range<T> create(T lower, T upper) {
		return new Range<T>(lower, upper);
	}

	public T getLower() {
		return lower;
	}

	public T getUpper() {
		return upper;
	}

	public boolean contains(T value) {
		if (value == null)
			throw new NullPointerException("value must not be null");
		return value.compareTo(lower) >= 0 && value.compareTo(upper) <= 0;
	}

	public boolean contains(Range<T> range) {
		if (range == null)
			throw new NullPointerException("range must not be null");
		return range.lower.compareTo(lower) >= 0 && range.upper.compareTo(upper) <= 0;
	}

	public T clamp(T value) {
		if (value == null)
			throw new NullPointerException("value must not be null");
		if (value.compareTo(lower) < 0)
			return lower;
		if (value.compareTo(upper) > 0)
			return upper;
		return value;
	}

	public Range<T> intersect(Range<T> range) {
		if (range == null)
			throw new NullPointerException("range must not be null");
		int cmpLower = range.lower.compareTo(lower);
		int cmpUpper = range.upper.compareTo(upper);
		if (cmpLower <= 0 && cmpUpper >= 0)
			return this;
		if (cmpLower >= 0 && cmpUpper <= 0)
			return range;
		return create(cmpLower <= 0 ? lower : range.lower, cmpUpper >= 0 ? upper : range.upper);
	}

	public Range<T> intersect(T lower, T upper) {
		return intersect(create(lower, upper));
	}

	public Range<T> extend(Range<T> range) {
		if (range == null)
			throw new NullPointerException("range must not be null");
		int cmpLower = range.lower.compareTo(lower);
		int cmpUpper = range.upper.compareTo(upper);
		if (cmpLower <= 0 && cmpUpper >= 0)
			return range;
		if (cmpLower >= 0 && cmpUpper <= 0)
			return this;
		return create(cmpLower >= 0 ? lower : range.lower, cmpUpper <= 0 ? upper : range.upper);
	}

	public Range<T> extend(T lower, T upper) {
		return extend(create(lower, upper));
	}

	public Range<T> extend(T value) {
		if (value == null)
			throw new NullPointerException("value must not be null");
		return extend(value, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Range))
			return false;
		Range<?> other = (Range<?>)obj;
		return lower.equals(other.lower) && upper.equals(other.upper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lower, upper);
	}

	@Override
	public String toString() {
		return "[" + lower + ", " + upper + "]";
	}
}
