package com.obddroid.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class to load OpenStreetMap tiles for GPS location preview
 */
public class MapTileHelper {
    private static final String TAG = "MapTileHelper";
    private static final String TILE_SERVER = "https://tile.openstreetmap.org";
    private static final int DEFAULT_ZOOM = 15; // Street-level zoom
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Load map tile centered on GPS coordinates
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param imageView ImageView to display the map
     */
    public static void loadMapTile(double latitude, double longitude, ImageView imageView) {
        loadMapTile(latitude, longitude, DEFAULT_ZOOM, imageView);
    }

    /**
     * Load map tile with custom zoom level
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param zoom Zoom level (0-19, higher = more zoomed in)
     * @param imageView ImageView to display the map
     */
    public static void loadMapTile(double latitude, double longitude, int zoom, ImageView imageView) {
        DebugLog.d(TAG, String.format("loadMapTile called with lat=%f, lon=%f, zoom=%d", latitude, longitude, zoom));

        executor.execute(() -> {
            try {
                // Convert lat/lon to tile coordinates
                int tileX = (int) Math.floor((longitude + 180.0) / 360.0 * Math.pow(2.0, zoom));
                int tileY = (int) Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(latitude)) + 1.0 / Math.cos(Math.toRadians(latitude))) / Math.PI) / 2.0 * Math.pow(2.0, zoom));

                // Build tile URL
                String tileUrl = String.format("%s/%d/%d/%d.png", TILE_SERVER, zoom, tileX, tileY);
                DebugLog.d(TAG, "Downloading map tile from: " + tileUrl);
                DebugLog.d(TAG, String.format("Tile coordinates: X=%d, Y=%d", tileX, tileY));

                // Download tile
                URL url = new URL(tileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "OBD-Droid/1.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                DebugLog.d(TAG, "HTTP Response Code: " + responseCode);

                if (responseCode == 200) {
                    InputStream input = connection.getInputStream();
                    Bitmap tileBitmap = BitmapFactory.decodeStream(input);
                    input.close();

                    if (tileBitmap != null) {
                        DebugLog.d(TAG, String.format("Bitmap loaded: %dx%d", tileBitmap.getWidth(), tileBitmap.getHeight()));
                        // Add location marker to the center
                        Bitmap markedBitmap = addLocationMarker(tileBitmap);

                        // Update UI on main thread
                        mainHandler.post(() -> {
                            imageView.setImageBitmap(markedBitmap);
                            DebugLog.d(TAG, "Map tile set to ImageView successfully");
                        });
                    } else {
                        Log.e(TAG, "Failed to decode bitmap from stream");
                    }
                } else {
                    Log.e(TAG, "HTTP request failed with code: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to load map tile: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Add a red location marker pin to the center of the tile
     */
    private static Bitmap addLocationMarker(Bitmap original) {
        Bitmap result = original.copy(original.getConfig(), true);
        Canvas canvas = new Canvas(result);

        int centerX = result.getWidth() / 2;
        int centerY = result.getHeight() / 2;

        // Draw red circle marker
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Outer circle (white border)
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, 12, paint);

        // Inner circle (red)
        paint.setColor(Color.RED);
        canvas.drawCircle(centerX, centerY, 10, paint);

        // Center dot (white)
        paint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, 4, paint);

        return result;
    }
}
