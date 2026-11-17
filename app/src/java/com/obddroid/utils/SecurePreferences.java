package com.obddroid.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

/**
 * Provides secure storage for sensitive data like API keys.
 * Uses AES encryption to protect stored values.
 */
public class SecurePreferences
{
    private static final Logger log = Logger.getLogger(SecurePreferences.class.getName());
    private static final String PREFS_NAME = "obddroid_secure_prefs";
    private static final String KEY_OPENAI_API_KEY = "openai_api_key";
    private static final String KEY_REMOTE_TELEMETRY_PASSWORD = "remote_telemetry_password";
    private static final String ALGORITHM = "AES";

    private final SharedPreferences preferences;
    private final SecretKey secretKey;

    public SecurePreferences(Context context)
    {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        secretKey = generateKey(context);
    }

    /**
     * Generates an encryption key based on app-specific data.
     * Note: This is basic encryption. For production, consider using Android Keystore.
     */
    private SecretKey generateKey(Context context)
    {
        try
        {
            String packageName = context.getPackageName();
            String seed = packageName + "_obddroid_2024";

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));

            // Use first 16 bytes for AES-128
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);

            return new SecretKeySpec(keyBytes, ALGORITHM);
        }
        catch (NoSuchAlgorithmException e)
        {
            log.log(Level.SEVERE, "Failed to generate encryption key", e);
            // Fallback to a default key (not secure, but allows app to function)
            byte[] fallbackKey = "ObdDroid_Default".getBytes(StandardCharsets.UTF_8);
            return new SecretKeySpec(fallbackKey, ALGORITHM);
        }
    }

    /**
     * Encrypts a string value.
     */
    private String encrypt(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "";
        }

        try
        {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        }
        catch (GeneralSecurityException e)
        {
            log.log(Level.SEVERE, "Failed to encrypt value", e);
            return "";
        }
    }

    /**
     * Decrypts a string value.
     */
    private String decrypt(String encrypted)
    {
        if (encrypted == null || encrypted.isEmpty())
        {
            return "";
        }

        try
        {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT));
            return new String(decrypted, StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException | IllegalArgumentException e)
        {
            log.log(Level.SEVERE, "Failed to decrypt value", e);
            return "";
        }
    }

    /**
     * Stores the OpenAI API key securely.
     */
    public void setOpenAiApiKey(String apiKey)
    {
        String encrypted = encrypt(apiKey);
        preferences.edit().putString(KEY_OPENAI_API_KEY, encrypted).apply();
    }

    /**
     * Retrieves the OpenAI API key.
     */
    public String getOpenAiApiKey()
    {
        String encrypted = preferences.getString(KEY_OPENAI_API_KEY, "");
        return decrypt(encrypted);
    }

    /**
     * Checks if an OpenAI API key is configured.
     */
    public boolean hasOpenAiApiKey()
    {
        String key = getOpenAiApiKey();
        return key != null && !key.trim().isEmpty();
    }

    /**
     * Clears the stored OpenAI API key.
     */
    public void clearOpenAiApiKey()
    {
        preferences.edit().remove(KEY_OPENAI_API_KEY).apply();
    }

    /**
     * Stores the Remote Telemetry password securely.
     */
    public void setRemoteTelemetryPassword(String password)
    {
        String encrypted = encrypt(password);
        preferences.edit()
            .putString(KEY_REMOTE_TELEMETRY_PASSWORD, encrypted)
            .apply();
    }

    /**
     * Retrieves the Remote Telemetry password.
     */
    public String getRemoteTelemetryPassword()
    {
        String encrypted = preferences.getString(KEY_REMOTE_TELEMETRY_PASSWORD, "");
        return decrypt(encrypted);
    }

    /**
     * Clears the stored Remote Telemetry password.
     */
    public void clearRemoteTelemetryPassword()
    {
        preferences.edit()
            .remove(KEY_REMOTE_TELEMETRY_PASSWORD)
            .apply();
    }
}
