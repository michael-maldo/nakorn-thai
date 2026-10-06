package au.com.nakornthai.notification.infrastructure;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;

/** Infrastructure-only transport. Never retain or expose sensitive failure bodies. */
public final class VonageHttpClient {
 private VonageHttpClient() {}
 public static RestClient create(String baseUrl) {
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  factory.setReadTimeout(Duration.ofSeconds(10));
  return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
 }
 public static JsonNode post(RestClient api,String path,String key,String secret,LinkedMultiValueMap<String,String> body) {
  return api.post().uri(path).headers(h->h.setBasicAuth(key,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(JsonNode.class);
 }
 public static String number(String destination) {return destination.startsWith("+")?destination.substring(1):destination;}
}
