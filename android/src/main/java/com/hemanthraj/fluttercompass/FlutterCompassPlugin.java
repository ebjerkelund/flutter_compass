package com.hemanthraj.fluttercompass;

import java.util.Map;
import java.util.TreeMap;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;


import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public final class FlutterCompassPlugin implements StreamHandler {
    // A static variable which will retain the value across Isolates.
    //private static final String TAG = "FlutterCompass";

    private static Double currentAzimuth;    
    private double newAzimuth;
    private double filter;
    private int lastAccuracy;
    
    private SensorEventListener sensorEventListener;

    private final SensorManager sensorManager;
    private Map<Integer, Sensor> sensorList;
    enum CompassType {
        ROTATION,
        ACCELERATOR,
        NONE
        }
    private CompassType compassType;

    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    private float[] R = new float[9];
    private float[] I = new float[9];
    private final float[] orientation;
    private final float[] rMat;

    public static void registerWith(Registrar registrar) {
        EventChannel channel = new EventChannel(registrar.messenger(), "hemanthraj/flutter_compass");
        channel.setStreamHandler(new FlutterCompassPlugin(registrar.context()));
    }


    public void onListen(Object arguments, EventSink events) {
        // Added check for the sensor, if null then the device does not have the TYPE_ROTATION_VECTOR or TYPE_GEOMAGNETIC_ROTATION_VECTOR sensor
        if(compassType != CompassType.NONE) {
            this.sensorEventListener = createSensorEventListener(events);
            if (this.sensorList.get(0) != null) {
                this.sensorManager.registerListener(sensorEventListener, this.sensorList.get(0), SensorManager.SENSOR_DELAY_GAME);
            }
            if (this.sensorList.get(1) != null) {
                this.sensorManager.registerListener(sensorEventListener, this.sensorList.get(1), SensorManager.SENSOR_DELAY_GAME);
            }

        } else {
            // Send null to Flutter side
            events.success(null);
//                events.error("Sensor Null", "No sensor was found", "The device does not have any sensor");
        }
    }

    public void onCancel(Object arguments) {
        this.sensorManager.unregisterListener(this.sensorEventListener);
    }

    private SensorEventListener createSensorEventListener(final EventSink events) {
        return new SensorEventListener() {
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                lastAccuracy = accuracy;
            }

            public void onSensorChanged(SensorEvent event) {

                final float alpha = 0.97f;

                // Log.e(TAG + ": SensorType", String.valueOf(event.sensor.getType()));

                if (compassType == CompassType.ROTATION) {

                    SensorManager.getRotationMatrixFromVector(rMat, event.values);
                    newAzimuth = (Math.toDegrees((double) SensorManager.getOrientation(rMat, orientation)[0]) + (double) 360) % (double) 360;
                    if (currentAzimuth == null || Math.abs(currentAzimuth - newAzimuth) >= filter) {
                        currentAzimuth = newAzimuth;

                        double azimuthForCameraMode = (Math.toDegrees((double) SensorManager.getOrientation(rMat, orientation)[0]) - Math.toDegrees((double) SensorManager.getOrientation(rMat, orientation)[2]) + (double) 360) % (double) 360;
                        double[] v = new double[4];
                        v[0] = newAzimuth;
                        v[1] = azimuthForCameraMode;
                        v[2] = setAccuracy();
                        v[3] = -1;
                        events.success(v);
                    }
                }
                else if (compassType == CompassType.ACCELERATOR) {

                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                        mGravity[0] = alpha * mGravity[0] + (1 - alpha)
                                * event.values[0];
                        mGravity[1] = alpha * mGravity[1] + (1 - alpha)
                                * event.values[1];
                        mGravity[2] = alpha * mGravity[2] + (1 - alpha)
                                * event.values[2];
        
                        // mGravity = event.values;
        
                        // Log.e(TAG + ": mGravity", Float.toString(mGravity[0]));
        
                    }
        
                    if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        // mGeomagnetic = event.values;
        
                        mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha)
                                * event.values[0];
                        mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha)
                                * event.values[1];
                        mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha)
                                * event.values[2];
                        
                        // Log.e(TAG + ": mGeomagnetic", Float.toString(mGeomagnetic[0]));
        
                    }
        
                    boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
                    // Log.e(TAG + ": success", String.valueOf(success));
                    if (success) {
                        float orientation[] = new float[3];
                        SensorManager.getOrientation(R, orientation);
                        // Log.d(TAG, "azimuth (rad): " + azimuth);
                        newAzimuth = ((float) Math.toDegrees(orientation[0]) + (double) 360) % (double) 360; // orientation
                        // Log.d(TAG, "azimuth (deg): " + azimuth);
                        if (currentAzimuth == null || Math.abs(currentAzimuth - newAzimuth) >= filter) {
                            currentAzimuth = newAzimuth;
                            double[] v = new double[4];
                            v[0] = newAzimuth;
                            v[1] = newAzimuth;
                            v[2] = setAccuracy();
                            v[3] = 1;
                            events.success(v);
                        }    
                    }

                }
            }
        };
    }

    private FlutterCompassPlugin(Context context) {
        filter = 0.1F;
        lastAccuracy = 1; // SENSOR_STATUS_ACCURACY_LOW
        R = new float[9];
        orientation = new float[3];
        rMat = new float[9];

        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        compassType = CompassType.NONE;
        this.sensorList = new TreeMap<Integer, Sensor>();
        this.sensorList.put(0, this.sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
        if (this.sensorList.get(0) == null) {
            this.sensorList.put(0, this.sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR));
        }
        if (this.sensorList.get(0) != null) {
            compassType = CompassType.ROTATION;
        } else {
            this.sensorList.put(0, this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            this.sensorList.put(1, this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
            if (this.sensorList.get(0) != null && this.sensorList.get(1) != null) {
                compassType = CompassType.ACCELERATOR;
            }
        }
    }

    private int setAccuracy() {

        if (lastAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            return 15;
        } else if (lastAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            return 30;
        } else if (lastAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            return 45;
        }
        return -1; // unknown

    }

}
