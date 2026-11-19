package android.transition;

import android.animation.TimeInterpolator;
import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/* transitions are not animated; they keep what the app sets so it can read it back */
public class Transition {

	public interface TransitionListener {}

	public static abstract class EpicenterCallback {
		public abstract Rect onGetEpicenter(Transition transition);
	}

	private ArrayList<Integer> targetIds = new ArrayList<>();
	private ArrayList<View> targets = new ArrayList<>();
	private ArrayList<String> targetNames = new ArrayList<>();
	private ArrayList<Class> targetTypes = new ArrayList<>();
	private ArrayList<TransitionListener> listeners = new ArrayList<>();
	private EpicenterCallback epicenterCallback;
	private long duration = -1;
	private TimeInterpolator interpolator;

	public Transition clone() {
		return new Transition();
	}

	public Transition addListener(TransitionListener listener) {
		listeners.add(listener);
		return this;
	}

	public Transition removeListener(TransitionListener listener) {
		listeners.remove(listener);
		return this;
	}

	public Transition excludeTarget(int targetId, boolean exclude) {
		return this;
	}

	public Transition addTarget(View target) {
		targets.add(target);
		return this;
	}

	public Transition addTarget(String targetName) {
		targetNames.add(targetName);
		return this;
	}

	public Transition addTarget(int targetId) {
		if (targetId != 0)
			targetIds.add(targetId);
		return this;
	}

	public Transition addTarget(Class targetType) {
		targetTypes.add(targetType);
		return this;
	}

	public Transition removeTarget(View target) {
		targets.remove(target);
		return this;
	}

	public Transition removeTarget(String targetName) {
		targetNames.remove(targetName);
		return this;
	}

	public Transition removeTarget(int targetId) {
		targetIds.remove((Integer)targetId);
		return this;
	}

	public List<Integer> getTargetIds() {
		return targetIds;
	}

	public List<View> getTargets() {
		return targets;
	}

	public List<String> getTargetNames() {
		return targetNames;
	}

	public List<Class> getTargetTypes() {
		return targetTypes;
	}

	public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
		this.epicenterCallback = epicenterCallback;
	}

	public EpicenterCallback getEpicenterCallback() {
		return epicenterCallback;
	}

	public Transition setDuration(long duration) {
		this.duration = duration;
		return this;
	}

	public long getDuration() {
		return duration;
	}

	public Transition setInterpolator(TimeInterpolator interpolator) {
		this.interpolator = interpolator;
		return this;
	}

	public TimeInterpolator getInterpolator() {
		return interpolator;
	}
}
