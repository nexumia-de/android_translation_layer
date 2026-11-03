package android.hardware;

import android.atl.annotation.Export;

public class SensorEvent {

	public final float[] values;

	public Sensor sensor;
	public int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
	public long timestamp = System.nanoTime();

	@Export
	public SensorEvent(float[] values, Sensor sensor) {
		this.values = values;
		this.sensor = sensor;
	}
}
