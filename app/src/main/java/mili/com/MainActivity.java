package mili.com;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;


    private Button mRecordButton = null;
    private MediaRecorder mRecorder = null;

    private Button mPlayButton = null;
    private MediaPlayer mPlayer = null;

    private TextView mDecibelView = null;

    // Decibel calculation
    Handler handler = new Handler();
    int delay = 100; //milliseconds
    private static double mEMA = 0.0;
    private static final double EMA_FILTER = 0.6;
    double ampl = 0.8;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    // Sensor control
    private boolean mShouldStartRecording = true;
    private boolean mShouldStartPlaying = true;

    // Motion sensors
    private final static int SENSOR_DELAY = 2500;
    private SensorManager mSensorManager;
    private Sensor mAccSensor;
    private Sensor mGyroSensor;


    // Play/Save sensor data
    private static String mFileDir = null;
    private static String mCurrentTime = null;

    private static String mPlayFileName = "/storage/emulated/0/Download/Eaves/20190305164445712audio.3gp";
    private static String mRecordFileName = null;

    private FileWriter mFileWriter = null;
    private float[] mAccData = new float[3];
    private float[] mGyroData = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Record to the external cache directory for visibility
        // getExternalCacheDir().getAbsolutePath();
        mFileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        mFileDir = mFileDir + "/" + getString(R.string.app_name) + "/";

        File mydir = new File(mFileDir);
        if(!mydir.exists())
            mydir.mkdirs();
        else
            Log.d(LOG_TAG, mFileDir + " already exists");


        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);


        mRecordButton = findViewById(R.id.recBtn);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRecord(mShouldStartRecording);
                if (mShouldStartRecording) {
                    mRecordButton.setText("Stop recording");
                    mRecordButton.setBackgroundColor(Color.GRAY);
                } else {
                    mRecordButton.setText("Start recording");
                    mRecordButton.setBackgroundColor(Color.LTGRAY);

                    Toast.makeText(getApplicationContext(), mRecordFileName, Toast.LENGTH_LONG).show();
                }
                mShouldStartRecording = !mShouldStartRecording;
            }
        });
        mPlayButton = findViewById(R.id.playBtn);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlay(mShouldStartPlaying);
                if (mShouldStartPlaying) {
                    mPlayButton.setText("Stop playing");
                    mPlayButton.setBackgroundColor(Color.GRAY);
                } else {
                    mPlayButton.setText("Start playing");
                    mPlayButton.setBackgroundColor(Color.LTGRAY);
                }
                mShouldStartPlaying = !mShouldStartPlaying;
            }
        });



        mDecibelView = findViewById(R.id.dbTxv);



        //Sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // Success! There's a magnetometer.
            mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Log.i(LOG_TAG, "AccMinDelay = " + mAccSensor.getMinDelay());
        } else {
            // Failure! No magnetometer.
            Log.i(LOG_TAG, "There's no accelerometer sensors!");
        }

        if (mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            // Success! There's a magnetometer.
            mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            Log.i(LOG_TAG, "GyroMinDelay = " + mGyroSensor.getMinDelay());
        } else {
            // Failure! No magnetometer.
            Log.i(LOG_TAG, "There's no gyroscope sensors!");
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();

    }

    private void onRecord(boolean shouldStart) {
        if (shouldStart) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void onPlay(boolean shouldStart) {
        if (shouldStart) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        Log.i(LOG_TAG, "PlayFileName = " + mPlayFileName);
        try {
            mPlayer.setDataSource(mPlayFileName);
            mPlayer.setVolume(1.0f, 1.0f);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() "+ mPlayFileName +" failed");
        }
    }

    private void stopPlaying() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void startRecording() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS"); //"yyyy-MM-dd-HH:mm:ss.SSS"
        mCurrentTime = sdf.format(new Date());
        mRecordFileName = mFileDir + mCurrentTime;
        Log.i(LOG_TAG, "RecordFileName = " + mRecordFileName);

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mRecordFileName + "audio.3gp");
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();

        try {
            mFileWriter = new FileWriter(mRecordFileName + "motion.txt", true);
        } catch (IOException e) {
            e.printStackTrace();
        }





        handler.postDelayed(new Runnable(){
            public void run(){
                mDecibelView.setText("SoundMeter" + String.format("%1$"+25+ "s", soundDb(ampl)) + "dB");
                handler.postDelayed(this, delay);
            }
        }, delay);

    }

    private void stopRecording() {
        mPlayFileName = mRecordFileName + "audio.3gp";

        mDecibelView.setText("SoundMeter");
        handler.removeCallbacksAndMessages(null);

        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;

        if (mFileWriter != null) {
            try {
                mFileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onStop() {
        super.onStop();
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccSensor, SENSOR_DELAY);
        mSensorManager.registerListener(this, mGyroSensor, SENSOR_DELAY);
//        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_FASTEST);
//        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (!mShouldStartRecording) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                for (int i = 0; i < 3; i++) {
                    mAccData[i] = sensorEvent.values[i];
                }
            }

            if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                for (int i = 0; i < 3; i++) {
                    mGyroData[i] = sensorEvent.values[i];
                }
                try {
                    mFileWriter.write(System.nanoTime() + "," + mAccData[0] + "," +
                            mAccData[1] + "," +
                            mAccData[2] + "," +
                            mGyroData[0] + "," +
                            mGyroData[1] + "," +
                            mGyroData[2] + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


    public double soundDb(double ampl) {
        return 20 * Math.log10(getAmplitudeEMA() / ampl);
    }

    public double getAmplitude() {
        if (mRecorder != null)
            return (mRecorder.getMaxAmplitude());
        else
            return 0;

    }

    public double getAmplitudeEMA() {
        double amp = getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }
}
