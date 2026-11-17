package com.obddroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Guards the Play-relevant manifest declarations made for targetSdk 36 (obd-c0s): scoped storage,
 * Bluetooth scan without location, connectedDevice foreground services and predictive back.
 * Reads the source manifest; Gradle runs unit tests with the module directory as working directory.
 */
public class ManifestPolicyTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static Document manifest;

    @BeforeClass
    public static void parseManifest() throws Exception {
        File file = new File("src/AndroidManifest.xml");
        assertTrue("manifest not found from " + new File(".").getAbsolutePath(), file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        manifest = factory.newDocumentBuilder().parse(file);
    }

    private static Element permission(String name) {
        NodeList nodes = manifest.getElementsByTagName("uses-permission");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (name.equals(element.getAttributeNS(ANDROID_NS, "name"))) {
                return element;
            }
        }
        return null;
    }

    private static Element service(String name) {
        NodeList nodes = manifest.getElementsByTagName("service");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (name.equals(element.getAttributeNS(ANDROID_NS, "name"))) {
                return element;
            }
        }
        return null;
    }

    @Test
    public void storagePermissionsAreScoped() {
        assertNull("READ_EXTERNAL_STORAGE", permission("android.permission.READ_EXTERNAL_STORAGE"));
        Element write = permission("android.permission.WRITE_EXTERNAL_STORAGE");
        assertNotNull("WRITE_EXTERNAL_STORAGE (still needed on Android 9 and below)", write);
        assertEquals("28", write.getAttributeNS(ANDROID_NS, "maxSdkVersion"));
    }

    @Test
    public void bluetoothScanNeverDerivesLocation() {
        Element scan = permission("android.permission.BLUETOOTH_SCAN");
        assertNotNull(scan);
        assertEquals("neverForLocation", scan.getAttributeNS(ANDROID_NS, "usesPermissionFlags"));
    }

    @Test
    public void foregroundServicesUseConnectedDevice() {
        assertNull("dataSync has a 6 h daily limit on Android 15",
                permission("android.permission.FOREGROUND_SERVICE_DATA_SYNC"));
        assertNotNull(permission("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"));
        assertNotNull("connectedDevice prerequisite for WiFi adapters",
                permission("android.permission.CHANGE_NETWORK_STATE"));
        for (String name : new String[]{
                "com.obddroid.services.CsvLoggingService", "com.obddroid.scan.ScanOrchestrator"}) {
            Element element = service(name);
            assertNotNull(name, element);
            assertEquals(name, "connectedDevice", element.getAttributeNS(ANDROID_NS, "foregroundServiceType"));
        }
    }

    @Test
    public void applicationOptsIntoPredictiveBackAndInstallsInsets() {
        Element application = (Element) manifest.getElementsByTagName("application").item(0);
        assertEquals("true", application.getAttributeNS(ANDROID_NS, "enableOnBackInvokedCallback"));
        assertEquals(ObdDroidApplication.class.getName(), application.getAttributeNS(ANDROID_NS, "name"));
    }

    private static void assertNull(String what, Object value) {
        org.junit.Assert.assertNull(what + " must not be declared", value);
    }
}
