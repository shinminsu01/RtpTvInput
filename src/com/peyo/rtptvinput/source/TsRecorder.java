package com.peyo.rtptvinput.source;

import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.extractor.ExtractorMetaData;
import com.google.android.exoplayer2.extractor.ts.TsMetaDataRetriever;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class TsRecorder {
    private static final String TAG = "TsRecorder";

    private final TsStreamWriter mTsStreamWriter;
    private boolean mRecording;
    private TsDataSource mTsSource;
    private RecordingThread mRecordingThread;
    private TsMetaDataRetriever mRetriever;
    private String mRecordingFile;

    public TsRecorder(TsStreamWriter writer) {
        mTsStreamWriter = writer;
        mTsSource = (TsDataSource) TsDataSourceFactory.createSourceFactory().createDataSource();
        mRetriever = new TsMetaDataRetriever();
    }

    public void startRecording(String addr) {
        try {
            mTsSource.open(new DataSpec(Uri.parse(addr)));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        mTsSource.shiftStartPosition(mTsSource.getBufferedPosition());
        mRetriever.startFeeding();

        mRecording = true;
        mRecordingThread = new RecordingThread();
        mRecordingThread.start();
        Log.i(TAG, "Recording started");
    }

    private class RecordingThread extends Thread {
        @Override
        public void run() {
            byte[] dataBuffer = new byte[188 * 7];
            while (true) {
                if (!mRecording) {
                    break;
                }

                int bytesWritten;
                try {
                    bytesWritten = mTsSource.read(dataBuffer, 0, dataBuffer.length);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                //Log.i(TAG, "RecoridngThread got " + bytesWritten);
                if (mTsStreamWriter != null) {
                    mTsStreamWriter.writeToFile(dataBuffer, bytesWritten);
                }
                mRetriever.feedData(dataBuffer, bytesWritten);
            }
            Log.i(TAG, "Recording stopped");
        }
    }

    public void stopRecording() {
        mRecording = false;
        try {
            if (mRecordingThread != null) {
                mRecordingThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            mTsSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mRetriever.finishFeeding();
        saveMetaData();
    }

    private void saveMetaData() {
        String metaDataFile = mTsStreamWriter.getFileUri().toString().replace("file://", "").replace(".ts", ".dat");
        Log.e("shinms", "meta data file = " + metaDataFile);
        ExtractorMetaData metaData = mRetriever.getMetaData();
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(metaDataFile));
            out.writeObject(metaData);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
