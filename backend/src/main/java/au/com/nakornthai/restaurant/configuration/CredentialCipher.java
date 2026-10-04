package au.com.nakornthai.restaurant.configuration;
public interface CredentialCipher {
 boolean available();
 String encrypt(String context,String plaintext);
 String decrypt(String context,String encoded);
}
