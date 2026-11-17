package com.obddroid.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReportFileWriterTest {

    private static final int ANDROID_9 = 28;
    private static final int ANDROID_10 = 29;
    private static final int ANDROID_14 = 34;

    @Test
    public void android10AndUpGoThroughMediaStore() {
        assertTrue(ReportFileWriter.plan(ANDROID_10, "report.csv", ReportFileWriter.MIME_CSV).useMediaStore);
        assertTrue(ReportFileWriter.plan(ANDROID_14, "report.csv", ReportFileWriter.MIME_CSV).useMediaStore);
    }

    @Test
    public void android9AndBelowWriteTheFileDirectly() {
        assertFalse(ReportFileWriter.plan(ANDROID_9, "report.csv", ReportFileWriter.MIME_CSV).useMediaStore);
        assertFalse(ReportFileWriter.plan(21, "report.csv", ReportFileWriter.MIME_CSV).useMediaStore);
    }

    @Test
    public void everyExportGoesToDocumentsObdDroid() {
        assertEquals("Documents/OBD-Droid", ReportFileWriter.EXPORT_DIRECTORY);
        ReportFileWriter.Plan modern = ReportFileWriter.plan(ANDROID_14, "a.json", ReportFileWriter.MIME_JSON);
        ReportFileWriter.Plan legacy = ReportFileWriter.plan(ANDROID_9, "a.json", ReportFileWriter.MIME_JSON);
        assertEquals("Documents/OBD-Droid", modern.relativePath);
        assertEquals("OBD-Droid", legacy.legacyFolder);
        assertEquals("a.json", modern.filename);
        assertEquals("Documents/OBD-Droid/a.json", ReportFileWriter.displayPathFor("a.json"));
    }

    @Test
    public void givenMimeTypeIsKept() {
        assertEquals(ReportFileWriter.MIME_PDF,
                ReportFileWriter.plan(ANDROID_14, "history.pdf", ReportFileWriter.MIME_PDF).mimeType);
        assertEquals(ReportFileWriter.MIME_CSV,
                ReportFileWriter.plan(ANDROID_9, "odd-name.dat", ReportFileWriter.MIME_CSV).mimeType);
    }

    @Test
    public void missingMimeTypeComesFromTheExtension() {
        assertEquals("text/csv", ReportFileWriter.plan(ANDROID_14, "x.csv", null).mimeType);
        assertEquals("application/json", ReportFileWriter.plan(ANDROID_14, "x.JSON", "").mimeType);
        assertEquals("application/pdf", ReportFileWriter.mimeTypeFor("x.pdf"));
        assertEquals("text/plain", ReportFileWriter.mimeTypeFor("x.txt"));
        assertEquals("application/octet-stream", ReportFileWriter.mimeTypeFor("x.bin"));
        assertEquals("application/octet-stream", ReportFileWriter.mimeTypeFor("noextension"));
        assertEquals("application/octet-stream", ReportFileWriter.mimeTypeFor("trailingdot."));
    }

    @Test(expected = IllegalArgumentException.class)
    public void filenameWithAPathIsRefused() {
        ReportFileWriter.plan(ANDROID_14, "../escape.csv", ReportFileWriter.MIME_CSV);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyFilenameIsRefused() {
        ReportFileWriter.plan(ANDROID_14, " ", ReportFileWriter.MIME_CSV);
    }
}
