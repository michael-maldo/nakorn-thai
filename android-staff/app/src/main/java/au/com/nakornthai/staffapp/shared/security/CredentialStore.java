package au.com.nakornthai.staffapp.shared.security;

/** Stores only refresh-cookie material. Implementations must commit replacements atomically. */
public interface CredentialStore {
  String read();

  void replace(String value);

  void clear();
}
