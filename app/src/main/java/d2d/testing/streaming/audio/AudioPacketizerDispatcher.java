package d2d.testing.streaming.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import d2d.testing.streaming.rtp.AbstractPacketizer;
import d2d.testing.streaming.rtp.ByteBufferInputStream;
import d2d.testing.streaming.rtp.MediaCodecInputStream;
import d2d.testing.streaming.video.VideoQuality;
import d2d.testing.streaming.rtp.MediaCodecBufferReader;

public class AudioPacketizerDispatcher {

    private static String TAG = "AudioPacketizerDispatcher";
    private static Thread mReaderThread;
    private static Thread mWriterThread;
    private static AudioPacketizerDispatcher mInstance;

    private final AudioQuality mQuality = new AudioQuality(8000,32000);

    private final int mBufferSize;

    private final AudioRecord mAudioRecord;
    private final MediaCodec mMediaCodec;
    private final ByteBuffer[] mMediaCodecsBuffers;
    private final MediaCodecInputStream mMediaCodecInputStream;
    private final Map<AbstractPacketizer, InputStream> mPacketizersInputsMap = new HashMap<>();

    @SuppressLint("NewApi")
    private AudioPacketizerDispatcher() throws IOException {
        this.mBufferSize = AudioRecord.getMinBufferSize(mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*2;
        this.mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);

        if(this.mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG,"An error occurred with the AudioRecord API while initialization!");
        }

        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mBufferSize);

        mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mAudioRecord.startRecording();
        if(mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG,"An error occurred with the AudioRecord API while starting recording!");
        }

        mMediaCodec.start();
        mMediaCodecInputStream = new MediaCodecInputStream(mMediaCodec);
        mMediaCodecsBuffers = mMediaCodec.getInputBuffers();
        mReaderThread = new Thread(new MediaCodecBufferReader(mBufferSize,mMediaCodecInputStream,mPacketizersInputsMap));
        mWriterThread = new Thread(new AudioPacketizerDispatcher.MediaCodecBufferWriter());
        mReaderThread.start();
        mWriterThread.start();

        Log.e(TAG,"Constructor finished");
    }

    public static boolean isRunning() {
        return mInstance != null;
    }

    public static AudioPacketizerDispatcher start() throws IOException {
        if(mInstance == null) {
            mInstance = new AudioPacketizerDispatcher();

            Log.e(TAG,"Thread started!");
        }
        return mInstance;
    }

    public static void stop() {
        if(mInstance == null) {
            Log.e(TAG,"Stopping dispatcher...");
            mReaderThread.interrupt();
            mWriterThread.interrupt();

            mReaderThread = null;
            mWriterThread = null;

            mInstance = null;
        }
    }

    public static void subscribe(AbstractPacketizer packetizer) throws IOException {
            AudioPacketizerDispatcher.start().addInternalPacketizer(packetizer);
    }

    public static void unsubscribe(AbstractPacketizer packetizer) {
        if(mInstance != null) {
            mInstance.removeInternalMediaCodec(packetizer);
        }
    }

    @SuppressLint("NewApi")
    private void addInternalPacketizer(AbstractPacketizer packetizer) {
        synchronized (mPacketizersInputsMap) {
            InputStream packetizerInput = new ByteBufferInputStream();
            packetizer.setInputStream(packetizerInput);

            mPacketizersInputsMap.put(packetizer, packetizerInput);
            Log.e(TAG,"Added internal packetizer to inputStreamMap!");
        }
    }

    @SuppressLint("NewApi")
    private void removeInternalMediaCodec(AbstractPacketizer packetizer){
        synchronized (mPacketizersInputsMap) {
            mPacketizersInputsMap.remove(packetizer);
            Log.e(TAG,"Removed internal media codec from map!");
            if(mPacketizersInputsMap.size() == 0) {
                Log.e(TAG,"No more elements in map lets finish this!");

                stop();
            }
        }
    }

    class MediaCodecBufferWriter implements Runnable {

        private String TAG = "MediaCodecBufferWriter";

        @SuppressLint("NewApi")
        @Override
        public void run() {
            int len;

            while (!Thread.interrupted()) {
                int bufferIndex = mMediaCodec.dequeueInputBuffer(10000);
                if (bufferIndex >= 0) {
                    mMediaCodecsBuffers[bufferIndex].clear();
                    len = mAudioRecord.read(mMediaCodecsBuffers[bufferIndex], mBufferSize);

                    if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "An error occurred with the AudioRecord API !");
                    } else {
                        //Log.v(TAG, "pushing raw data to media encoder");
                        mMediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime() / 1000, 0);

                    }
                }
            }

            try {
                mAudioRecord.stop();
                mAudioRecord.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

