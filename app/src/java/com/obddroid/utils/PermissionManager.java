package com.obddroid.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.obddroid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized permission management for Android 14+ compatibility
 * Handles all runtime permissions including Bluetooth, Storage, and Notifications
 */
public class PermissionManager {

    // Permission request codes
    public static final int PERMISSION_REQUEST_BLUETOOTH = 100;
    public static final int PERMISSION_REQUEST_STORAGE = 101;
    public static final int PERMISSION_REQUEST_NOTIFICATION = 102;
    public static final int PERMISSION_REQUEST_ALL = 103;
    public static final int PERMISSION_REQUEST_LOCATION = 104;

    // Bluetooth permissions for Android 12+ (API 31+)
    private static final String[] BLUETOOTH_PERMISSIONS_S = new String[]{
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    };

    // Legacy Bluetooth permissions for Android 11 and below
    private static final String[] BLUETOOTH_PERMISSIONS_LEGACY = new String[]{
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    };

    // Storage permission for Android 9 and below only (manifest: maxSdkVersion 28). WRITE implies READ there;
    // READ_EXTERNAL_STORAGE is no longer declared, so it must not be requested.
    private static final String[] STORAGE_PERMISSIONS = new String[]{
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final String[] LOCATION_PERMISSIONS = new String[]{
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /**
     * Check if Bluetooth permissions are granted
     */
    public static boolean hasBluetoothPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires new permissions
            return hasPermissions(context, BLUETOOTH_PERMISSIONS_S);
        } else {
            // Android 11 and below use legacy permissions
            return hasPermissions(context, BLUETOOTH_PERMISSIONS_LEGACY);
        }
    }

    /**
     * Check if storage permissions are granted
     * Note: Android 10+ uses scoped storage, permissions may not be needed
     */
    public static boolean hasStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ uses scoped storage, no permissions needed for app-specific files
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 with scoped storage (but can be opted out)
            return true;
        } else {
            // Android 9 and below need storage permissions
            return hasPermissions(context, STORAGE_PERMISSIONS);
        }
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(context, Manifest.permission.POST_NOTIFICATIONS);
        }
        // Below Android 13, notifications don't need runtime permission
        return true;
    }

    /**
     * Request Bluetooth permissions
     */
    public static void requestBluetoothPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(activity, BLUETOOTH_PERMISSIONS_S, PERMISSION_REQUEST_BLUETOOTH);
        } else {
            requestPermissions(activity, BLUETOOTH_PERMISSIONS_LEGACY, PERMISSION_REQUEST_BLUETOOTH);
        }
    }

    /**
     * Request storage permissions (only if needed)
     */
    public static void requestStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissions(activity, STORAGE_PERMISSIONS, PERMISSION_REQUEST_STORAGE);
        }
    }

    /**
     * Request notification permission (Android 13+)
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                PERMISSION_REQUEST_NOTIFICATION);
        }
    }

    public static boolean hasLocationPermission(Context context) {
        return hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static void requestLocationPermission(Activity activity) {
        requestPermissions(activity, LOCATION_PERMISSIONS, PERMISSION_REQUEST_LOCATION);
    }

    /**
     * Request all necessary permissions at once
     */
    public static void requestAllPermissions(Activity activity) {
        List<String> permissionsNeeded = new ArrayList<>();

        // Check Bluetooth permissions
        if (!hasBluetoothPermissions(activity)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                for (String perm : BLUETOOTH_PERMISSIONS_S) {
                    if (!hasPermission(activity, perm)) {
                        permissionsNeeded.add(perm);
                    }
                }
            } else {
                for (String perm : BLUETOOTH_PERMISSIONS_LEGACY) {
                    if (!hasPermission(activity, perm)) {
                        permissionsNeeded.add(perm);
                    }
                }
            }
        }

        // Check storage permissions (only for older Android)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasStoragePermissions(activity)) {
            for (String perm : STORAGE_PERMISSIONS) {
                if (!hasPermission(activity, perm)) {
                    permissionsNeeded.add(perm);
                }
            }
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(activity)) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(activity,
                permissionsNeeded.toArray(new String[0]),
                PERMISSION_REQUEST_ALL);
        }
    }

    /**
     * Show rationale dialog for Bluetooth permissions
     */
    public static void showBluetoothRationale(final Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("Bluetooth Permission Required")
            .setMessage("OBD-Droid needs Bluetooth access to connect to your OBD adapter and read vehicle data.")
            .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestBluetoothPermissions(activity);
                }
            })
            .setNegativeButton("Cancel", null)
            .create()
            .show();
    }

    /**
     * Show rationale dialog for storage permissions
     */
    public static void showStorageRationale(final Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("Storage Permission Required")
            .setMessage("OBD-Droid needs storage access to save data logs, export CSV files, and capture screenshots.")
            .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestStoragePermissions(activity);
                }
            })
            .setNegativeButton("Cancel", null)
            .create()
            .show();
    }

    /**
     * Show rationale dialog for notification permission
     */
    public static void showNotificationRationale(final Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("Notification Permission")
            .setMessage("OBD-Droid can show notifications for connection status and data logging progress.")
            .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestNotificationPermission(activity);
                }
            })
            .setNegativeButton("Not Now", null)
            .create()
            .show();
    }

    public static void showLocationRationale(final Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle(R.string.gps_telemetry_start)
            .setMessage(R.string.gps_permission_rationale)
            .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestLocationPermission(activity);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show();
    }

    /**
     * Show settings dialog when permission is permanently denied
     */
    public static void showSettingsDialog(final Activity activity, String message) {
        new AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(message + "\n\nPlease enable it in Settings.")
            .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivity(intent);
                }
            })
            .setNegativeButton("Cancel", null)
            .create()
            .show();
    }

    /**
     * Check if we should show rationale for Bluetooth permissions
     */
    public static boolean shouldShowBluetoothRationale(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.BLUETOOTH_CONNECT) ||
                ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.BLUETOOTH_SCAN);
        }
        return false;
    }

    /**
     * Check if Bluetooth permission is permanently denied
     */
    public static boolean isBluetoothPermissionPermanentlyDenied(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return !hasBluetoothPermissions(activity) && !shouldShowBluetoothRationale(activity);
        }
        return false;
    }

    /**
     * Handle permission request results
     */
    public static boolean handlePermissionResult(int requestCode, String[] permissions,
                                                 int[] grantResults) {
        if (grantResults.length > 0) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            return allGranted;
        }
        return false;
    }

    // Helper methods
    private static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
            == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean hasPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (!hasPermission(context, permission)) {
                return false;
            }
        }
        return true;
    }

    private static void requestPermissions(Activity activity, String[] permissions,
                                          int requestCode) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }
}
