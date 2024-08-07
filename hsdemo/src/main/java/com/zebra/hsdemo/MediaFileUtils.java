package com.zebra.hsdemo;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MediaFileUtils {

    public static Uri encodePCMtoWavThenTransferFileToMediaStore(Context context, File sourceFile, int sampleRate, int channels, int bitDepth, float gain) throws IOException {
        // Convert PCM to WAV
        File wavFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "converted_sound_file.wav");
        if(wavFile.exists())
            wavFile.delete();

        convertPcmToWav(sourceFile, wavFile, sampleRate, channels, bitDepth, gain);

        // Insert the WAV file into the MediaStore
        return insertFileIntoMediaStore(context, wavFile);
    }

    private static void convertPcmToWav(File pcmFile, File wavFile, int sampleRate, int channels, int bitDepth, float gain) throws IOException {
        byte[] pcmData = new byte[(int) pcmFile.length()];
        try (FileInputStream fis = new FileInputStream(pcmFile)) {
            fis.read(pcmData);
        }

        pcmData = applyGain(pcmData, pcmData.length, gain);

        try (FileOutputStream fos = new FileOutputStream(wavFile)) {
            // Write WAV header
            writeWavHeader(fos, pcmData.length, sampleRate, channels, bitDepth);
            // Write PCM data
            fos.write(pcmData);
        }
    }

    private static void writeWavHeader(FileOutputStream fos, int pcmDataLength, int sampleRate, int channels, int bitDepth) throws IOException {
        int byteRate = sampleRate * channels * bitDepth / 8;
        int blockAlign = channels * bitDepth / 8;
        int dataSize = pcmDataLength;
        int chunkSize = 36 + dataSize;

        fos.write(new byte[] {
                'R', 'I', 'F', 'F', // ChunkID
                (byte) (chunkSize & 0xff), (byte) ((chunkSize >> 8) & 0xff), (byte) ((chunkSize >> 16) & 0xff), (byte) ((chunkSize >> 24) & 0xff), // ChunkSize
                'W', 'A', 'V', 'E', // Format
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat (PCM)
                (byte) channels, 0, // NumChannels
                (byte) (sampleRate & 0xff), (byte) ((sampleRate >> 8) & 0xff), (byte) ((sampleRate >> 16) & 0xff), (byte) ((sampleRate >> 24) & 0xff), // SampleRate
                (byte) (byteRate & 0xff), (byte) ((byteRate >> 8) & 0xff), (byte) ((byteRate >> 16) & 0xff), (byte) ((byteRate >> 24) & 0xff), // ByteRate
                (byte) blockAlign, 0, // BlockAlign
                (byte) bitDepth, 0, // BitsPerSample
                'd', 'a', 't', 'a', // Subchunk2ID
                (byte) (dataSize & 0xff), (byte) ((dataSize >> 8) & 0xff), (byte) ((dataSize >> 16) & 0xff), (byte) ((dataSize >> 24) & 0xff) // Subchunk2Size
        });
    }

    private static Uri insertFileIntoMediaStore(Context context, File file) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri externalContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        // Check if a file with the same name already exists and delete it
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{file.getName()};
        Cursor cursor = contentResolver.query(externalContentUri, null, selection, selectionArgs, null);

        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            Uri existingFileUri = ContentUris.withAppendedId(externalContentUri, id);
            contentResolver.delete(existingFileUri, null, null);
            cursor.close();
        }

        // Insert the new file
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MyMediaFiles");

        Uri fileUri = contentResolver.insert(externalContentUri, values);

        try (OutputStream out = contentResolver.openOutputStream(fileUri)) {
            FileInputStream in = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileUri;
    }


    public static byte[] applyGain(byte[] buffer, int read, float gain) {
        for (int i = 0; i < read; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sample = (short) Math.min(Math.max(sample * gain, Short.MIN_VALUE), Short.MAX_VALUE);
            buffer[i] = (byte) (sample & 0xFF);
            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return buffer;
    }

    public static byte[] normalizeAndAdjustPcmGain(byte[] pcmData, float targetDb) {
        // Step 1: Find the maximum amplitude in the PCM data
        float maxAmplitude = 0;
        for (int i = 0; i < pcmData.length; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            maxAmplitude = Math.max(maxAmplitude, Math.abs(sample));
        }

        // Step 2: Calculate the normalization factor
        float normalizationFactor = 32767 / maxAmplitude;

        // Step 3: Normalize the PCM data
        for (int i = 0; i < pcmData.length; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sample = (short) (sample * normalizationFactor);
            pcmData[i] = (byte) (sample & 0xFF);
            pcmData[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        // Step 4: Calculate current dB level after normalization
        float currentDb = 20 * (float) Math.log10(maxAmplitude / 32767);

        // Step 5: Calculate gain factor to reach target dB
        float gainFactor = (float) Math.pow(10, (targetDb - currentDb) / 20);

        // Step 6: Apply gain factor and clip
        for (int i = 0; i < pcmData.length; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sample = (short) (sample * gainFactor);
            if (sample > 32767) sample = 32767;
            if (sample < -32768) sample = -32768;
            pcmData[i] = (byte) (sample & 0xFF);
            pcmData[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcmData;
    }


}
