package com.obddroid.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Helper for exporting diagnostic reports to user-visible storage.
 *
 * Handles MediaStore operations for Android 10+ and legacy file I/O for older devices,
 * ensuring all reports end up under Documents/OBD-Droid.
 */
public final class ReportFileWriter {

    private static final String EXPORT_DIRECTORY = Environment.DIRECTORY_DOCUMENTS + "/OBD-Droid";

    private ReportFileWriter() {
        // Utility class
    }

    public interface WriterCallback {
        void write(BufferedWriter writer) throws IOException;
    }

    /**
     * Write text content to a report file.
     *
     * @param context  application context
     * @param filename desired filename (including extension)
     * @param mimeType MIME type to register with MediaStore
     * @param content  text content to write
     * @return display path or filename
     */
    public static String writeText(Context context, String filename, String mimeType, String content) throws IOException {
        return write(context, filename, mimeType, writer -> writer.write(content));
    }

    /**
     * Write report content using a streaming callback.
     *
     * @param context   application context
     * @param filename  desired filename (including extension)
     * @param mimeType  MIME type to register with MediaStore
     * @param callback  writer callback for streaming content
     * @return display path or filename (depending on Android version)
     */
    public static String write(Context context, String filename, String mimeType, WriterCallback callback) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeWithMediaStore(context, filename, mimeType, callback);
        }
        return writeLegacy(context, filename, callback);
    }

    private static String writeWithMediaStore(Context context,
                                              String filename,
                                              String mimeType,
                                              WriterCallback callback) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIRECTORY);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
        if (uri == null) {
            throw new IOException("Unable to create export file");
        }

        OutputStream outputStream = resolver.openOutputStream(uri);
        if (outputStream == null) {
            throw new IOException("Unable to open export file for writing");
        }

        try (OutputStream out = outputStream;
             OutputStreamWriter osWriter = new OutputStreamWriter(out);
             BufferedWriter writer = new BufferedWriter(osWriter)) {
            callback.write(writer);
        } finally {
            ContentValues finishValues = new ContentValues();
            finishValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, finishValues, null, null);
        }

        return filename;
    }

    private static String writeLegacy(Context context,
                                      String filename,
                                      WriterCallback callback) throws IOException {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File obdroidDir = new File(documentsDir, "OBD-Droid");
        if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
            throw new IOException("Unable to create export directory");
        }

        File reportFile = new File(obdroidDir, filename);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
            callback.write(writer);
        }
        return reportFile.getAbsolutePath();
    }
}
