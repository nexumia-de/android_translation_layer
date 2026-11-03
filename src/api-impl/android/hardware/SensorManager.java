package android.hardware;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import java.util.Arrays;
import java.util.List;

public class SensorManager {

	public static float GRAVITY_EARTH = 9.81f;
	public static final int SENSOR_STATUS_NO_CONTACT = -1;
	public static final int SENSOR_STATUS_UNRELIABLE = 0;
	public static final int SENSOR_STATUS_ACCURACY_LOW = 1;
	public static final int SENSOR_STATUS_ACCURACY_MEDIUM = 2;
	public static final int SENSOR_STATUS_ACCURACY_HIGH = 3;

	/* rotation vector (x, y, z[, w]) to a 3x3 or 4x4 rotation matrix */
	public static void getRotationMatrixFromVector(float[] R, float[] rotationVector) {
		float q1 = rotationVector[0], q2 = rotationVector[1], q3 = rotationVector[2], q0;
		if (rotationVector.length >= 4) {
			q0 = rotationVector[3];
		} else {
			q0 = 1 - q1 * q1 - q2 * q2 - q3 * q3;
			q0 = q0 > 0 ? (float)Math.sqrt(q0) : 0;
		}
		float sq_q1 = 2 * q1 * q1, sq_q2 = 2 * q2 * q2, sq_q3 = 2 * q3 * q3;
		float q1_q2 = 2 * q1 * q2, q3_q0 = 2 * q3 * q0, q1_q3 = 2 * q1 * q3;
		float q2_q0 = 2 * q2 * q0, q2_q3 = 2 * q2 * q3, q1_q0 = 2 * q1 * q0;
		float[] m = {1 - sq_q2 - sq_q3, q1_q2 - q3_q0, q1_q3 + q2_q0,
		             q1_q2 + q3_q0, 1 - sq_q1 - sq_q3, q2_q3 - q1_q0,
		             q1_q3 - q2_q0, q2_q3 + q1_q0, 1 - sq_q1 - sq_q2};
		if (R.length == 9) {
			System.arraycopy(m, 0, R, 0, 9);
		} else if (R.length == 16) {
			for (int row = 0; row < 3; row++) {
				System.arraycopy(m, row * 3, R, row * 4, 3);
				R[row * 4 + 3] = 0;
			}
			R[12] = R[13] = R[14] = 0;
			R[15] = 1;
		}
	}

	/* azimuth, pitch and roll in radians from a 3x3 or 4x4 rotation matrix */
	public static float[] getOrientation(float[] R, float[] values) {
		if (R.length == 9) {
			values[0] = (float)Math.atan2(R[1], R[4]);
			values[1] = (float)Math.asin(-R[7]);
			values[2] = (float)Math.atan2(-R[6], R[8]);
		} else {
			values[0] = (float)Math.atan2(R[1], R[5]);
			values[1] = (float)Math.asin(-R[9]);
			values[2] = (float)Math.atan2(-R[8], R[10]);
		}
		return values;
	}

	public Sensor getDefaultSensor(int type) {
		return new Sensor(type);
	}

	public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs, Handler handler) {
		return true; // we could try saying that the sensor doesn't exist and hope the app just doesn't use it then, but as long as we never call the handler the app should leave this alone
	}

	public boolean registerListener(final SensorEventListener listener, final Sensor sensor, int samplingPeriodUs) {
		switch (sensor.getType()) {
			case Sensor.TYPE_ORIENTATION:
				new LocationManager().requestLocationUpdates(null, 0, 0, new LocationListener() {
					@Override
					public void onLocationChanged(Location location) {
						listener.onSensorChanged(new SensorEvent(new float[] {location.getBearing()}, sensor));
					}
				});
				return true;
			case Sensor.TYPE_ACCELEROMETER:
				register_accelerometer_listener_native(listener, sensor, samplingPeriodUs);
				return true;
			default:
				return false;
		}
	}

	public void unregisterListener(final SensorEventListener listener) {
		unregisterListener(listener, null);
	}

	public void unregisterListener(final SensorEventListener listener, Sensor sensor) {
		System.out.println("STUB: andoroid.hw.SensorManager.unregisterListener");
	}

	native void register_accelerometer_listener_native(SensorEventListener listener, Sensor sensor, int sampling_period);

	public List<Sensor> getSensorList(int type) {
		return Arrays.asList(getDefaultSensor(type));
	}
}
