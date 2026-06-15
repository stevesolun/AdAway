package org.adaway.model.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Secure storage for LLM API keys using direct Android Keystore + AES-256-GCM encryption.
 * <p>
 * - On all supported devices: AES-256-GCM with a hardware-backed Keystore key
 *   (never leaves secure hardware).
 * <p>
 * No new Gradle dependencies — all APIs are in the Android SDK.
 * <p>
 * MUST be called off the main thread (key generation involves I/O on first access).
 */
public final class SecureApiKeyStore {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "org.adaway.ai_key_master";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String PREFS_FILE = "ai_key_store";

    private static volatile SecureApiKeyStore sInstance;

    private final SharedPreferences mPrefs;

    private SecureApiKeyStore(@NonNull Context context)
            throws GeneralSecurityException, IOException {
        mPrefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        ensureKeyExists();
    }

    /**
     * Returns the singleton. MUST be called off the main thread on first access.
     */
    @NonNull
    public static SecureApiKeyStore getInstance(@NonNull Context context)
            throws GeneralSecurityException, IOException {
        if (sInstance == null) {
            synchronized (SecureApiKeyStore.class) {
                if (sInstance == null) {
                    sInstance = new SecureApiKeyStore(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Stores an API key. Pass {@code null} to remove it.
     * Call this on a background thread.
     */
    public void putApiKey(@NonNull String keyName, @Nullable String apiKey)
            throws GeneralSecurityException, IOException {
        SharedPreferences.Editor editor = mPrefs.edit();
        if (apiKey == null || apiKey.isEmpty()) {
            editor.remove(keyName);
        } else {
            editor.putString(keyName, encrypt(apiKey));
        }
        editor.apply();
    }

    /**
     * Retrieves a stored API key. Returns {@code null} if not set.
     * Call this on a background thread.
     */
    @Nullable
    public String getApiKey(@NonNull String keyName)
            throws GeneralSecurityException, IOException {
        String stored = mPrefs.getString(keyName, null);
        if (stored == null) return null;
        return decrypt(stored);
    }

    /** Returns true if an API key is stored for the given name. */
    public boolean hasApiKey(@NonNull String keyName) {
        return mPrefs.contains(keyName);
    }

    // -------------------------------------------------------------------------
    // Keystore helpers (API 23+)
    // -------------------------------------------------------------------------

    private void ensureKeyExists() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGen = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            keyGen.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            keyGen.generateKey();
        }
    }

    private SecretKey loadKey() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
        ks.load(null);
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    @NonNull
    private String encrypt(@NonNull String plaintext) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, loadKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buf = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
        buf.put(iv);
        buf.put(ciphertext);
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP);
    }

    @NonNull
    private String decrypt(@NonNull String encoded) throws GeneralSecurityException, IOException {
        byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
        // ATK-14: Guard against truncated/corrupted stored ciphertext.
        // Minimum valid blob = GCM IV (12 bytes) + GCM tag (16 bytes) = 28 bytes.
        if (data.length < GCM_IV_LENGTH + 16) {
            throw new GeneralSecurityException("Stored key is corrupted");
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buf.get(iv);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, loadKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
