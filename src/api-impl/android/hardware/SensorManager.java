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

	public static final float PRESSURE_STANDARD_ATMOSPHERE = 1013.25f;

	/* altitude in metres from the pressure p and the pressure at sea level p0, both in hPa */
	public static float getAltitude(float p0, float p) {
		final float coef = 1.0f / 5.255f;
		return 44330.0f * (1.0f - (float)Math.pow(p / p0, coef));
	}

	/* rotation matrix R and inclination matrix I (3x3 or 4x4, I may be null) from the gravity
	 * and geomagnetic vectors; false in free fall or close to the magnetic north pole */
	public static boolean getRotationMatrix(float[] R, float[] I, float[] gravity, float[] geomagnetic) {
		float Ax = gravity[0], Ay = gravity[1], Az = gravity[2];
		final float normsqA = (Ax * Ax + Ay * Ay + Az * Az);
		final float g = 9.81f;
		final float freeFallGravitySquared = 0.01f * g * g;
		if (normsqA < freeFallGravitySquared)
			return false;
		final float Ex = geomagnetic[0], Ey = geomagnetic[1], Ez = geomagnetic[2];
		float Hx = Ey * Az - Ez * Ay;
		float Hy = Ez * Ax - Ex * Az;
		float Hz = Ex * Ay - Ey * Ax;
		final float normH = (float)Math.sqrt(Hx * Hx + Hy * Hy + Hz * Hz);
		if (normH < 0.1f)
			return false;
		final float invH = 1.0f / normH;
		Hx *= invH;
		Hy *= invH;
		Hz *= invH;
		final float invA = 1.0f / (float)Math.sqrt(Ax * Ax + Ay * Ay + Az * Az);
		Ax *= invA;
		Ay *= invA;
		Az *= invA;
		final float Mx = Ay * Hz - Az * Hy;
		final float My = Az * Hx - Ax * Hz;
		final float Mz = Ax * Hy - Ay * Hx;
		if (R != null) {
			if (R.length == 9) {
				R[0] = Hx; R[1] = Hy; R[2] = Hz;
				R[3] = Mx; R[4] = My; R[5] = Mz;
				R[6] = Ax; R[7] = Ay; R[8] = Az;
			} else if (R.length == 16) {
				R[0] = Hx; R[1] = Hy; R[2] = Hz; R[3] = 0;
				R[4] = Mx; R[5] = My; R[6] = Mz; R[7] = 0;
				R[8] = Ax; R[9] = Ay; R[10] = Az; R[11] = 0;
				R[12] = 0; R[13] = 0; R[14] = 0; R[15] = 1;
			}
		}
		if (I != null) {
			final float invE = 1.0f / (float)Math.sqrt(Ex * Ex + Ey * Ey + Ez * Ez);
			final float c = (Ex * Mx + Ey * My + Ez * Mz) * invE;
			final float s = (Ex * Ax + Ey * Ay + Ez * Az) * invE;
			if (I.length == 9) {
				I[0] = 1; I[1] = 0; I[2] = 0;
				I[3] = 0; I[4] = c; I[5] = s;
				I[6] = 0; I[7] = -s; I[8] = c;
			} else if (I.length == 16) {
				I[0] = 1; I[1] = 0; I[2] = 0;
				I[4] = 0; I[5] = c; I[6] = s;
				I[8] = 0; I[9] = -s; I[10] = c;
				I[3] = I[7] = I[11] = I[12] = I[13] = I[14] = 0;
				I[15] = 1;
			}
		}
		return true;
	}

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
