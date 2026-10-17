package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.shared.security.KeystoreCredentialStore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SecureStorageTest {
  @Test
  public void keystoreEnvelopeRestoresRotatesAndClearsWithoutPlaintextPreferences() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    KeystoreCredentialStore store = new KeystoreCredentialStore(context);
    try {
      store.replace("synthetic-refresh-only-test");
      assertEquals("synthetic-refresh-only-test", new KeystoreCredentialStore(context).read());
      for (Object value :
          context.getSharedPreferences("session_envelope", Context.MODE_PRIVATE).getAll().values())
        assertFalse(value.toString().contains("synthetic-refresh"));
      store.replace("synthetic-rotated-refresh");
      assertEquals("synthetic-rotated-refresh", new KeystoreCredentialStore(context).read());
      store.clear();
      assertEquals("", new KeystoreCredentialStore(context).read());
    } finally {
      store.clear();
    }
  }
}
