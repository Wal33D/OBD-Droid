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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The one place reports are exported to user-visible storage.
 *
 * Every export lands under Documents/OBD-Droid: through MediaStore on Android 10+ and through
 * the public Documents directory on Android 9 and below.
 */
public final class ReportFileWriter {

    /** Folder inside the public Documents directory that holds every export. */
    public static final String EXPORT_FOLDER = "OBD-Droid";

    /**
     * MediaStore RELATIVE_PATH of the export folder, also how it is shown to the user.
     * "Documents" is the value of Environment.DIRECTORY_DOCUMENTS, which is not a compile-time
     * constant (and is null in JVM unit tests), so it is spelled out here.
     */
    public static final String EXPORT_DIRECTORY = "Documents/" + EXPORT_FOLDER;

    public static final String MIME_CSV = "text/csv";
    public static final String MIME_JSON = "application/json";
    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_TEXT = "text/plain";
    public static final String MIME_BINARY = "application/octet-stream";

    private ReportFileWriter() {
        // Utility class
    }

    /** Writes text content. */
    public interface WriterCallback {
        void write(BufferedWriter writer) throws IOException;
    }

    /** Writes raw bytes (PDFs, copies of existing files). */
    public interface StreamCallback {
        void write(OutputStream out) throws IOException;
    }

    /** Where an export went. Exactly one of {@link #uri} and {@link #file} is set. */
    public static final class Result {
        /** MediaStore entry (Android 10+), else null. */
        public final Uri uri;
        /** File on disk (Android 9 and below), else null. */
        public final File file;
        public final String filename;
        public final String mimeType;

        Result(Uri uri, File file, String filename, String mimeType) {
            this.uri = uri;
            this.file = file;
            this.filename = filename;
            this.mimeType = mimeType;
        }

        /** "Documents/OBD-Droid/&lt;filename&gt;", for messages to the user. */
        public String displayPath() {
            return displayPathFor(filename);
        }

        /** Absolute path on Android 9 and below, the file name on Android 10+. */
        public String location() {
            return file != null ? file.getAbsolutePath() : filename;
        }
    }

    /**
     * Where and how a file will be written. Pure, so the choice can be unit tested.
     */
    static final class Plan {
        final boolean useMediaStore;
        final String relativePath;
        final String legacyFolder;
        final String filename;
        final String mimeType;

        private Plan(boolean useMediaStore, String filename, String mimeType) {
            this.useMediaStore = useMediaStore;
            this.relativePath = EXPORT_DIRECTORY;
            this.legacyFolder = EXPORT_FOLDER;
            this.filename = filename;
            this.mimeType = mimeType;
        }
    }

    static Plan plan(int sdkInt, String filename, String mimeType) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Export filename is empty");
        }
        if (filename.contains("/") || filename.contains("\\") || filename.equals(".") || filename.equals("..")) {
            throw new IllegalArgumentException("Export filename must not contain a path: " + filename);
        }
        String mime = (mimeType == null || mimeType.isEmpty()) ? mimeTypeFor(filename) : mimeType;
        return new Plan(sdkInt >= Build.VERSION_CODES.Q, filename, mime);
    }

    /** MIME type for an export file name, from its extension. */
    public static String mimeTypeFor(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return MIME_BINARY;
        }
        switch (filename.substring(dot + 1).toLowerCase(Locale.US)) {
            case "csv":
                return MIME_CSV;
            case "json":
                return MIME_JSON;
            case "pdf":
                return MIME_PDF;
            case "txt":
                return MIME_TEXT;
            default:
                return MIME_BINARY;
        }
    }

    /** "Documents/OBD-Droid/&lt;filename&gt;". */
    public static String displayPathFor(String filename) {
        return EXPORT_DIRECTORY + "/" + filename;
    }

    /**
     * Write text content to a report file.
     *
     * @return the file name on Android 10+, the absolute path on older versions
     */
    public static String writeText(Context context, String filename, String mimeType, String content) throws IOException {
        return write(context, filename, mimeType, writer -> writer.write(content));
    }

    /**
     * Write report content using a streaming text callback.
     *
     * @return the file name on Android 10+, the absolute path on older versions
     */
    public static String write(Context context, String filename, String mimeType, WriterCallback callback) throws IOException {
        return export(context, filename, mimeType, callback).location();
    }

    /** Write text content and return where it went. */
    public static Result export(Context context, String filename, String mimeType, WriterCallback callback) throws IOException {
        return exportStream(context, filename, mimeType, out -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            callback.write(writer);
            writer.flush();
        });
    }

    /** Write raw bytes and return where they went. */
    public static Result exportStream(Context context, String filename, String mimeType, StreamCallback callback) throws IOException {
        Plan plan = plan(Build.VERSION.SDK_INT, filename, mimeType);
        if (plan.useMediaStore) {
            return writeWithMediaStore(context, plan, callback);
        }
        return writeLegacy(plan, callback);
    }

    private static Result writeWithMediaStore(Context context, Plan plan, StreamCallback callback) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, plan.filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, plan.mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, plan.relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
        if (uri == null) {
            throw new IOException("Unable to create export file");
        }

        boolean written = false;
        try {
            OutputStream outputStream = resolver.openOutputStream(uri);
            if (outputStream == null) {
                throw new IOException("Unable to open export file for writing");
            }
            try (OutputStream out = outputStream) {
                callback.write(out);
                out.flush();
            }
            written = true;
        } finally {
            if (written) {
                ContentValues finishValues = new ContentValues();
                finishValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, finishValues, null, null);
            } else {
                // Do not leave a half-written, still-pending entry behind.
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException ignored) {
                    // Keep the original failure.
                }
            }
        }

        return new Result(uri, null, plan.filename, plan.mimeType);
    }

    private static Result writeLegacy(Plan plan, StreamCallback callback) throws IOException {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File exportDir = new File(documentsDir, plan.legacyFolder);
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("Unable to create export directory");
        }

        File reportFile = new File(exportDir, plan.filename);
        try (OutputStream out = new FileOutputStream(reportFile)) {
            callback.write(out);
            out.flush();
        }
        return new Result(null, reportFile, plan.filename, plan.mimeType);
    }
}
