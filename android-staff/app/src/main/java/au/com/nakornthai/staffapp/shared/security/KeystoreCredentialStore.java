package au.com.nakornthai.staffapp.shared.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM envelope in private preferences; the AES key never leaves Android Keystore. */
public final class KeystoreCredentialStore implements CredentialStore {
  private final SharedPreferences preferences;
  private static final String ALIAS = "nakorn.staff.refresh.v1";

  public KeystoreCredentialStore(Context context) {
    preferences = context.getSharedPreferences("session_envelope", Context.MODE_PRIVATE);
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
    KeyGenerator generator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(
        new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
    return generator.generateKey();
  }

  public synchronized String read() {
    String envelope = preferences.getString("refresh", null);
    if (envelope == null) return "";
    try {
      String[] parts = envelope.split(":", 2);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
      return new String(
          cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    } catch (Exception invalidated) {
      clear();
      return "";
    }
  }

  public synchronized void replace(String value) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      String envelope =
          Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
              + ":"
              + Base64.encodeToString(
                  cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
      if (!preferences.edit().putString("refresh", envelope).commit())
        throw new IllegalStateException("Credential storage unavailable");
    } catch (Exception failure) {
      throw new IllegalStateException("Secure credential storage unavailable", failure);
    }
  }

  public synchronized void clear() {
    if (!preferences.edit().clear().commit())
      throw new IllegalStateException("Credential clearing failed");
  }
}
