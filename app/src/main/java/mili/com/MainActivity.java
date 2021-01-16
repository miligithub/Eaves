package mili.com;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;


    private Button mRecordButton = null;
    private MediaRecorder mRecorder = null;

    private Button mPlayButton = null;
    private MediaPlayer mPlayer = null;

    private TextView mTextView = null;

    private LinearLayout mSourceGroup = null;
    private RadioGroup mRepetitionGroup = null;

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

    private static String mClassificationInfo = null;

    private static final String mAudioPattern = ".wav";
    private static final String mAudioExample = "FAC_1A_normalized";
    private static int mPlayFileIndex = 0;
    private static int mPlayFileNum = 0;
    private static String mPlayFileDir = "";
    private static String mDefaultPlayFileDirName = "";
    private static List<String> mPlayFileDirName = new ArrayList<>();
    private List<String> mPlaySingleFolderList = null;
    private List<String> mPlayFileList = null;


    private static String mPlayFileName = "/storage/emulated/0/Download/Eaves/20190305164445712audio.3gp";
    private static String mRecordFileName = null;

    private FileWriter mFileWriter = null;
    private float[] mAccData = new float[3];
    private float[] mGyroData = new float[3];

    // Volume
    private static AudioManager mAudioManager = null;
    private static int mMaxVolume = 0;
    private static int mCurrentVolume = 0;
    private static int mRepetitionNum = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Record to the external cache directory for visibility
        // getExternalCacheDir().getAbsolutePath();
        mFileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        mFileDir = mFileDir + "/" + getString(R.string.app_name) + "/";


        File mydir = new File(mFileDir);
        if (!mydir.exists())
            mydir.mkdirs();
        else
            Log.d(LOG_TAG, mFileDir + " already exists");

        createBuiltInFiles();


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


        mTextView = findViewById(R.id.textView);


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


        mSourceGroup = (LinearLayout) findViewById(R.id.sourceRadioList);
        String[] sourceLists = getResources().getStringArray(R.array.sourceList);
        int sourceNum = sourceLists.length;
        for (int i = 0; i < sourceNum; i++) {
            CheckBox btn = new CheckBox(this);
            mSourceGroup.addView(btn);
            btn.setText(sourceLists[i]);
            btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Log.i(LOG_TAG, "Number of Folders: " + mPlayFileDirName.size());
                    if (isChecked) {
                        mPlayFileDirName.add(buttonView.getText().toString());
                    } else {
                        mPlayFileDirName.remove(buttonView.getText().toString());
                    }
                    Log.i(LOG_TAG, "Number of Folders: " + mPlayFileDirName.size());
                }
            });
        }
        ((CheckBox) mSourceGroup.getChildAt(0)).setChecked(true);


        mRepetitionGroup = (RadioGroup) findViewById(R.id.repeatRadio);
        ((RadioButton) mRepetitionGroup.getChildAt(0)).callOnClick();

        //Volume
        // Get the AudioManager instance
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        /*
           int getStreamVolume (int streamType)
                        Returns the current volume index for a particular stream.

           Parameters
                        streamType int : The stream type whose volume index is returned.

           Returns
                        int : The current volume index for the stream.
        */

        // Get the music current volume level
        mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // Get the device music maximum volume level
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // Display the media volume level stats on text view
        mTextView.setText("Media Max Volume : " + mMaxVolume
                + "\nMedia Current Volume : " + mCurrentVolume);
    }

    private void createBuiltInFiles() {
        mDefaultPlayFileDirName = mFileDir + getResources().getStringArray(R.array.sourceList)[0];
        File mydir = new File(mDefaultPlayFileDirName);
        if (!mydir.exists()) {
            mydir.mkdirs();
            copyAssets();
        } else {
            Log.d(LOG_TAG, mDefaultPlayFileDirName + " already exists");
        }
    }

    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        if (files != null) {
            for (String filename : files) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = assetManager.open(filename);
                    File outFile = new File(mDefaultPlayFileDirName, filename);
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                } catch (IOException e) {
                    Log.e("tag", "Failed to copy asset file: " + filename, e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            // NOOP
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            // NOOP
                        }
                    }
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private void refreshFileList() {
        Log.i(LOG_TAG, "refreshFileList() is running...");
        if (mPlayFileList != null) {
            mPlayFileList.clear();
        }
        mPlayFileList = new ArrayList<>();

        for (String dir : mPlayFileDirName) {
            Log.i(LOG_TAG, dir);
            mPlayFileDir = mFileDir + dir + "/";
            Log.i(LOG_TAG, "Folder of Audio Files: " + mPlayFileDir);

            // List audio files
            if (mPlaySingleFolderList != null) {
                mPlaySingleFolderList.clear();
            }
            mPlaySingleFolderList = new ArrayList<>();

            getPlayList();

            Collections.sort(mPlaySingleFolderList);


            mPlayFileList.addAll(mPlaySingleFolderList);
        }


        mPlayFileNum = mPlayFileList.size();
        Log.i(LOG_TAG, "Number of Audio Files: " + mPlayFileNum);
        //        for (int i = 0; i<mPlayFileNum; i++){
        //            Log.i(LOG_TAG, mPlaySingleFolderList.get(i));
        //        }
    }


    public void onRepetitionRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();
        if (checked) {
            mCurrentVolume = 16 - Integer.valueOf(((RadioButton) view).getText().toString());
            mRepetitionNum = Integer.valueOf(((RadioButton) view).getText().toString());
            Log.d(LOG_TAG, "Repetition " + mCurrentVolume + " " + mRepetitionNum);
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
            // mPlayButton.setEnabled(false);
            startRecording();
        } else {
            // mPlayButton.setEnabled(true);
            stopRecording();
        }
    }

    private void onPlay(boolean shouldStart) {
        if (shouldStart) {
            refreshFileList();
            for (int i = 0; i < mSourceGroup.getChildCount(); i++) {
                mSourceGroup.getChildAt(i).setEnabled(false);
            }
            mRecordButton.setEnabled(false);
            startPlaying();
        } else {

            for (int i = 0; i < mSourceGroup.getChildCount(); i++) {
                mSourceGroup.getChildAt(i).setEnabled(true);
            }
            mRecordButton.setEnabled(true);
            stopPlaying();
        }
    }

    private void startPlaying() {
        try {
            loopPlay(mCurrentVolume);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "prepare() " +
                    mPlayFileList.get(mPlayFileIndex) + " failed");
        }
    }


    private void loopPlay(final int volume) throws IOException {
        if (mPlayFileList.isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "The Audio Source Folder Does Not Exist", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        if (volume > mMaxVolume && mRepetitionNum == 0) {
            mPlayButton.callOnClick();
            return;
        }

        //TODO
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        //mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mMaxVolume, 0);
        mTextView.setText("Media Max Volume : " + mMaxVolume
                + "\nMedia Current Volume : " + mCurrentVolume);


        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
        } else {
            mPlayer.reset();
        }
        String tmpFileName = mPlayFileList.get(mPlayFileIndex);
        mPlayer.setDataSource(tmpFileName);

//        mRecordFileName = tmpFileName.substring(0, tmpFileName.length() - mAudioPattern.length());
//                + "motion.txt";


        int tmpEnd = tmpFileName.length() - mAudioPattern.length();
        mClassificationInfo = tmpFileName.substring(tmpEnd - mAudioExample.length(), tmpEnd);

        Log.d(LOG_TAG, "Index" + mPlayFileIndex + ": " + mRecordFileName);

        mPlayer.prepare();

        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onRecord(mShouldStartRecording);
                mShouldStartRecording = !mShouldStartRecording;

                mPlayer.stop();
                mPlayFileIndex++;
                Log.i(LOG_TAG, "nextPlayFileIndex = " + mPlayFileIndex);

                if (mPlayFileIndex < mPlayFileNum) {
                    // nothing, loop to next file;
                } else {
                    mPlayFileIndex = 0; // restart to first file;
                    mCurrentVolume += 1; // increase volume;
                    mRepetitionNum -= 1;
                }
                try {
                    loopPlay(mCurrentVolume);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mPlayer.start();

        onRecord(mShouldStartRecording);
        mShouldStartRecording = !mShouldStartRecording;
    }

    private void stopPlaying() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

//        ProcessPhoenix.triggerRebirth(getBaseContext());
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

        //TODO
//        handler.postDelayed(new Runnable() {
//            public void run() {
//                mTextView.setText("SoundMeter" + String.format("%1$" + 25 + "s", soundDb(ampl)) + "dB");
//                handler.postDelayed(this, delay);
//            }
//        }, delay);

    }

    private void stopRecording() {
        mPlayFileName = mRecordFileName + "audio.3gp";

        //TODO
//        mTextView.setText("SoundMeter");
//        handler.removeCallbacksAndMessages(null);

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
                    mFileWriter.write(mClassificationInfo + "," +
                            System.nanoTime() + "," +
                            mAccData[0] + "," +
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


    private void getPlayList() {
        Log.i(LOG_TAG, mPlayFileDir);

        if (mPlayFileDir != null) {
            File home = new File(mPlayFileDir);
            File[] listFiles = home.listFiles();
            if (listFiles != null && listFiles.length > 0) {
                for (File file : listFiles) {
                    Log.d(LOG_TAG, file.getAbsolutePath());
                    if (file.isDirectory()) {
                        scanDirectory(file);
                    } else {
                        addSongToList(file);
                    }
                }
            }
        }
    }

    private void scanDirectory(File directory) {
        if (directory != null) {
            File[] listFiles = directory.listFiles();
            if (listFiles != null && listFiles.length > 0) {
                for (File file : listFiles) {
                    if (file.isDirectory()) {
                        scanDirectory(file);
                    } else {
                        addSongToList(file);
                    }
                }
            }
        }
    }

    private void addSongToList(File song) {
        if (song.getName().endsWith(mAudioPattern)) {
            mPlaySingleFolderList.add(song.getAbsolutePath());
        }
    }
}

