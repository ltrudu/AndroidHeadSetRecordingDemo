package com.zebra.hsdemo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.criticalpermissionshelper.CriticalPermissionsHelper;
import com.zebra.criticalpermissionshelper.EPermissionType;
import com.zebra.criticalpermissionshelper.IResultCallbacks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "hsdemo";
    private AudioManager audioManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    private List<BluetoothDevice> devices;
    private boolean targetBt = false;


    AudioManager recAudioManager = null;
    AudioManager playAudioManager = null;
    AudioRecord recorder = null;
    Thread recordingThread = null;
    int bufSize = 0;
    boolean isRecording = false;
    float recordingGain = 1.0f;
    float replayGain = 10.0f;

    final static int[] sampleRatevalues = {8000, 12000, 16000, 22000, 32000, 44000};
    public static int sampleRate = sampleRatevalues[0];
    public static final int channelInConfig = AudioFormat.CHANNEL_IN_MONO;
    public static final int channelOutConfig = AudioFormat.CHANNEL_OUT_MONO;
    public static final int channelNumber = 1;
    public static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    public static final int bitDepth = 16;

    private boolean isBluetoothConnected = false;
    private BroadcastReceiver scoConnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, ">>> BT SCO state changed !!! ");
            if(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED.equals(action)) {
                int status = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR );
                Log.d(TAG, "BT SCO state changed : " + status);
                audioManager.setBluetoothScoOn(targetBt);
                if(status == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    isBluetoothConnected = true;
                }else if(status == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                    isBluetoothConnected = false;
                }
            }
        }
    };

    public void registerStateReceiver() {
        Log.d(TAG, "Register BT media receiver");
        registerReceiver(scoConnectReceiver, new
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
    }

    final BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG,"BT Onservice Connected");
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset) proxy;
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = null;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setButtonVisibility(false);

        CriticalPermissionsHelper.grantPermission(this, EPermissionType.ALL_DANGEROUS_PERMISSIONS, new IResultCallbacks() {
            @Override
            public void onSuccess(String message, String resultXML) {
                Log.d(TAG, EPermissionType.ALL_DANGEROUS_PERMISSIONS.toString() + " granted with success.");
                CriticalPermissionsHelper.grantPermission(MainActivity.this, EPermissionType.MANAGE_EXTERNAL_STORAGE, new IResultCallbacks() {
                    @Override
                    public void onSuccess(String message, String resultXML) {
                        Log.d(TAG, EPermissionType.MANAGE_EXTERNAL_STORAGE.toString() + " granted with success.");
                        initHSDemo();
                    }

                    @Override
                    public void onError(String message, String resultXML) {
                        Log.d(TAG, "Error granting " + EPermissionType.MANAGE_EXTERNAL_STORAGE.toString() + " permission.\n" + message);
                    }

                    @Override
                    public void onDebugStatus(String message) {
                        Log.d(TAG, "Debug Grant Permission " + EPermissionType.MANAGE_EXTERNAL_STORAGE.toString() + ": " + message);
                    }
                });
            }

            @Override
            public void onError(String message, String resultXML) {
                Log.d(TAG, "Error granting " + EPermissionType.ALL_DANGEROUS_PERMISSIONS.toString() + " permission.\n" + message);
            }

            @Override
            public void onDebugStatus(String message) {
                Log.d(TAG, "Debug Grant Permission " + EPermissionType.ALL_DANGEROUS_PERMISSIONS.toString() + ": " + message);
            }
        });

    }

    private void setButtonVisibility(boolean visible)
    {
        findViewById(R.id.llGlobal).setVisibility(visible == true ? View.VISIBLE : View.GONE);
        findViewById(R.id.tvMessage).setVisibility(visible == true ? View.GONE : View.VISIBLE);
    }

    private void initHSDemo()
    {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Start Bluetooth SCO
        audioManager.startBluetoothSco();

        // Request audio focus
        audioManager.requestAudioFocus(focusChange -> {
            // Handle focus change
        }, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        registerStateReceiver();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        bluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);

        findViewById(R.id.btStartRecording).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecording();
            }
        });
        findViewById(R.id.btStopRecording).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecording();
            }
        });
        findViewById(R.id.btPlayWithMP).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    playWithMediaPlayer();
            }
        });

        findViewById(R.id.btPlayWithAT).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                playPcmFileWithAudioTrack();
            }
        });
        setButtonVisibility(true);

        TextView tvRecordingGain = findViewById(R.id.tvRecordingGain);
        tvRecordingGain.setText(String.format("%.1f", recordingGain));

        ((SeekBar)findViewById(R.id.sbRecordingGain)).setProgress((int)(recordingGain * 10), false);
        ((SeekBar)findViewById(R.id.sbRecordingGain)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                recordingGain = progress / 10.0f;
                tvRecordingGain.setText(String.format("%.1f", recordingGain));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        TextView tvReplayGain = findViewById(R.id.tvReplayGain);
        tvReplayGain.setText(String.format("%.1f", replayGain));

        ((SeekBar)findViewById(R.id.sbReplayGain)).setProgress((int)(replayGain * 10), false);
        ((SeekBar)findViewById(R.id.sbReplayGain)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                replayGain = progress / 10.0f;
                tvReplayGain.setText(String.format("%.1f", replayGain));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        SeekBar sbSampleRate = findViewById(R.id.sbSampleRate);
        TextView tvSampleRate = findViewById(R.id.tvSampleRate);
        tvSampleRate.setText(String.format("%05d", sampleRatevalues[0]) + " Hz");
        sbSampleRate.setProgress(0);
        sbSampleRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sampleRate = sampleRatevalues[progress];
                String formattedValue = String.format("%05d", sampleRate);
                tvSampleRate.setText(String.valueOf(formattedValue) + " Hz");
                // Do something with the selected value
                Log.d("SeekBar", "Selected value: " + sampleRate);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional: handle start of touch
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional: handle stop of touch
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop Bluetooth SCO
        if(audioManager != null)
            audioManager.stopBluetoothSco();

        if(bluetoothAdapter != null && mBluetoothHeadset != null)
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);

        // Unregister the BroadcastReceiver
        try {
            Log.d(TAG, "Unregister BT media receiver");
            unregisterReceiver(scoConnectReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister media state receiver",e);
        }
    }

    private void startBluetoothSCOAudio(boolean state) {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        targetBt = state;
        if (state != isBluetoothConnected) {
            if(state) {
                Log.d(TAG, "BT SCO on >>>"); // First we try to connect
                audioManager.startBluetoothSco();
            } else {
                Log.d(TAG, "BT SCO off >>>"); // We stop to use BT SCO
                audioManager.setBluetoothScoOn(false);
                // And we stop BT SCO connection
                audioManager.stopBluetoothSco();
            }
        } else if (state != audioManager.isBluetoothScoOn()) {
            // BT SCO is already in desired connection state, we only have to use it
            audioManager.setBluetoothScoOn(state);
        }
    }



    @SuppressLint("MissingPermission")
    private void startRecording(){
        recAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        // Setting Mode is not mandatory, but based on the usecase (VoIP)
        //recAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (isHeadsetConnected()) {
            startBluetoothSCOAudio(true);
        } // To check if BT Headset is available to connect SCO and record via BT


        bufSize = AudioRecord.getMinBufferSize(sampleRate, channelInConfig, audioFormat);
        try {
            recorder = new AudioRecord( MediaRecorder.AudioSource.MIC ,
                    sampleRate, channelInConfig, audioFormat, bufSize);

        } catch (Exception e) {
            // handle exception
            Log.e(TAG,"UNSUPPORTED Input Parameter : " + e);
            return;
        }
        int recordstate = recorder.getState();
        if (recordstate == AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG,"startRecording recorder instance created");
            recorder.startRecording();
            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    writeAudioDataToFile();
                }
            },"AudioRecorder Thread");
            Log.w(TAG,"Recording thread to start");
            recordingThread.start();
        }
        else {
            Log.e(TAG,"UNSUPPORTED Input Parameter, recorder instance NOT created");
        }
    }

    private String getFilename()
    {
        File myCacheFile = new File(getCacheDir(), "hsdemo_audio.pcm");
        return myCacheFile.getPath();
    }

    public byte[] applyGain(byte[] buffer, int read, float gain) {
        for (int i = 0; i < read; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sample = (short) Math.min(Math.max(sample * gain, Short.MIN_VALUE), Short.MAX_VALUE);
            buffer[i] = (byte) (sample & 0xFF);
            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return buffer;
    }


    private void writeAudioDataToFile() {
        byte[] audioData = new byte[bufSize];
        String filePath = getFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            int read = recorder.read(audioData, 0, bufSize);
            audioData = applyGain(audioData, read, recordingGain);
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                try {
                    os.write(audioData);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void stopRecording(){
        isRecording = false;
        if(null != recorder){
            Log.w(TAG, "StopRecording");
            //recAudioManager.setMode(AudioManager.MODE_NORMAL);
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
        if (recAudioManager.isBluetoothScoOn()) {
            Log.w(TAG, "Disconnect BTSCO Record");
            startBluetoothSCOAudio(false);
        }
        else {
            Log.d(TAG, "BTSCO is not connected");
        }
        File recordedFile = new File(getFilename());
        if(recordedFile.exists())
        {
            Log.d(TAG, "File exists:" + recordedFile.getPath());
        }
    }

    public void playWithMediaPlayer() {
        if (isHeadsetConnected()) {
            startBluetoothSCOAudio(true);
        }

        routeAudioToHeadset();

        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume,0);

        File recordedFile = new File(getFilename());
        if(recordedFile.exists()) {
            Uri fileAsUri = null;
            try {
                fileAsUri = MediaFileUtils.encodePCMtoWavThenTransferFileToMediaStore(this, recordedFile, sampleRate, channelNumber, bitDepth, replayGain);
            } catch (IOException e) {
                Log.e(TAG, "Exception: " + e);
                e.printStackTrace();
                return;
            }

            // Create AudioAttributes
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();


            MediaPlayer mediaPlayer = new MediaPlayer();

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });


            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    playAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (playAudioManager.isBluetoothScoOn()) {
                        Log.w(TAG, "Stop play Disconnect BTSCO play");
                        startBluetoothSCOAudio(false);
                    } else
                        Log.w(TAG, "play BTSCO is not connected");

                }
            });
            try {
                mediaPlayer.setVolume(1.0f,1.0f);
                mediaPlayer.setAudioAttributes(audioAttributes);
                mediaPlayer.setDataSource(this, fileAsUri);
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isHeadsetConnected() {
        boolean hasConnectedDevice = false;
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                hasConnectedDevice =  true;
            }
        }
        boolean retVal = hasConnectedDevice && audioManager.isBluetoothScoAvailableOffCall();
        Log.d(TAG, "Can I do BT ? "+retVal);
        return retVal;
    }

    private void routeAudioToHeadset() {
        if (isHeadsetConnected()) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);
        } else {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(true);
        }
    }

    private void playPcmFileWithAudioTrack() {
        byte[] audioData = null;
        File fileToPlay = new File(getFilename());
        if(fileToPlay.exists())
        {
            try {
                audioData = FileUtils.readFileToByteArray(fileToPlay);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if(audioData == null)
        {
            Toast.makeText(this, "Error while playing pcm file : audioData == null", Toast.LENGTH_LONG).show();
            return;
        }

        audioData = applyGain(audioData, audioData.length, replayGain);

        if (isHeadsetConnected()) {
            startBluetoothSCOAudio(true);
        }

        routeAudioToHeadset();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelOutConfig)
                .build();

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelOutConfig, audioFormat);
        AudioTrack audioTrack = new AudioTrack(
                audioAttributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        audioTrack.setVolume(1.0f);
        audioTrack.play();
        audioTrack.write(audioData, 0, audioData.length);
        audioTrack.stop();
        audioTrack.release();

        playAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (playAudioManager.isBluetoothScoOn()) {
            startBluetoothSCOAudio(false);
        } // To check if BT Headset is available to connect SCO and record via BT

    }


}
