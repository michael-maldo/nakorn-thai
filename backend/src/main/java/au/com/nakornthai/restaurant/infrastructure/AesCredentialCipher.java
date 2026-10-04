package au.com.nakornthai.restaurant.infrastructure;
import au.com.nakornthai.restaurant.configuration.CredentialCipher;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class AesCredentialCipher implements CredentialCipher {
 private final byte[] key;
 private final SecureRandom random=new SecureRandom();
 public AesCredentialCipher(@Value("${NAKORN_CREDENTIAL_MASTER_KEY:}") String encoded){byte[] candidate=null;try{candidate=Base64.getDecoder().decode(encoded);if(candidate.length!=32)candidate=null;}catch(IllegalArgumentException ignored){}key=candidate;}
 public boolean available(){return key!=null;}
 private Cipher cipher(int mode,String context,byte[] nonce) throws Exception {if(key==null)throw new IllegalStateException("Credential master key unavailable");var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(("v1:"+context).getBytes(StandardCharsets.UTF_8));return c;}
 public String encrypt(String context,String plaintext){try{byte[] nonce=new byte[12];random.nextBytes(nonce);byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,context,nonce).doFinal(plaintext.getBytes(StandardCharsets.UTF_8));return "v1:"+Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(encrypted);}catch(Exception e){throw new IllegalStateException("Credential encryption unavailable");}}
 public String decrypt(String context,String encoded){try{String[] parts=encoded.split(":",-1);if(parts.length!=3||!parts[0].equals("v1"))throw new IllegalArgumentException();byte[] nonce=Base64.getDecoder().decode(parts[1]);if(nonce.length!=12)throw new IllegalArgumentException();return new String(cipher(Cipher.DECRYPT_MODE,context,nonce).doFinal(Base64.getDecoder().decode(parts[2])),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Stored credentials cannot be decrypted; deployment key required");}}
}
