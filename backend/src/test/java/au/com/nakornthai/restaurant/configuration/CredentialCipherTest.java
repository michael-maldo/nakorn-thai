package au.com.nakornthai.restaurant.configuration;

import au.com.nakornthai.restaurant.infrastructure.AesCredentialCipher;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class CredentialCipherTest {
 static String key(){byte[] key=new byte[32];new SecureRandom().nextBytes(key);return Base64.getEncoder().encodeToString(key);}
 @Test void roundTripUsesUniqueNoncesAndAuthenticatedContext(){
  var cipher=new AesCredentialCipher(key());String secret="test-credential";
  var first=cipher.encrypt("PAYPAL.clientSecret",secret);var second=cipher.encrypt("PAYPAL.clientSecret",secret);
  assertTrue(!first.equals(secret)&&!first.equals(second));
  assertTrue(cipher.decrypt("PAYPAL.clientSecret",first).equals(secret));
  assertThrows(IllegalStateException.class,()->cipher.decrypt("SMTP.password",first));
  assertThrows(IllegalStateException.class,()->new AesCredentialCipher(key()).decrypt("PAYPAL.clientSecret",first));
  assertThrows(IllegalStateException.class,()->cipher.decrypt("PAYPAL.clientSecret",first.substring(0,first.length()-3)));
 }
 @Test void missingOrInvalidKeysHaveNoDefaultAndCannotDecrypt(){
  for(String key:new String[]{"","invalid",Base64.getEncoder().encodeToString(new byte[16])}){
   var cipher=new AesCredentialCipher(key);assertFalse(cipher.available());
   assertThrows(IllegalStateException.class,()->cipher.encrypt("SMTP.password","test-credential"));
  }
 }
}
