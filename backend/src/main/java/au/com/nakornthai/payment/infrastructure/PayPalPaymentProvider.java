package au.com.nakornthai.payment.infrastructure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.math.BigDecimal;
@Component
public class PayPalPaymentProvider {
 private final RestClient api;
 private final String clientId,secret,returnUrl;
 private final boolean enabled;
 public PayPalPaymentProvider(@Value("${PAYPAL_ENABLED:false}") boolean enabled,@Value("${PAYPAL_ENV:sandbox}") String environment,
   @Value("${PAYPAL_CLIENT_ID:}") String clientId,@Value("${PAYPAL_CLIENT_SECRET:}") String secret,
   @Value("${PAYPAL_RETURN_URL:http://localhost:5173/#/order-confirmation}") String returnUrl) {
  this.enabled=enabled;this.clientId=clientId;this.secret=secret;this.returnUrl=returnUrl;
  if(!Set.of("sandbox","live").contains(environment))throw new IllegalArgumentException("PAYPAL_ENV must be sandbox or live");
  if(enabled && (clientId.isBlank() || secret.isBlank() || (!returnUrl.startsWith("https://") && environment.equals("live"))))throw new IllegalArgumentException("Configure PayPal credentials and HTTPS return URL");
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(15));
  api=RestClient.builder().requestFactory(factory).baseUrl(environment.equals("live")?"https://api-m.paypal.com":"https://api-m.sandbox.paypal.com").build();
 }
 public boolean enabled(){return enabled;}
 private String token(){
  if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"PayPal is unavailable");
  try{return api.post().uri("/v1/oauth2/token").headers(h->h.setBasicAuth(clientId,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials").retrieve().body(JsonNode.class).path("access_token").asText();}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal could not be reached. Please retry.");}
 }
 public JsonNode create(UUID orderId,long total) {
  var amount=Map.of("currency_code","AUD","value",BigDecimal.valueOf(total,2).toPlainString());
  return send("/v2/checkout/orders",Map.of("intent","CAPTURE","purchase_units",List.of(Map.of("custom_id",orderId.toString(),"amount",amount)),"payment_source",Map.of("paypal",Map.of("experience_context",Map.of("return_url",returnUrl,"cancel_url",returnUrl,"user_action","PAY_NOW","shipping_preference","NO_SHIPPING")))),orderId.toString());
 }
 public JsonNode details(String id) {
  try{return api.get().uri("/v2/checkout/orders/{id}",id).headers(h->h.setBearerAuth(token())).retrieve().body(JsonNode.class);}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal status is unavailable. Retry before paying again.");}
 }
 public JsonNode capture(String id,UUID orderId) {return send("/v2/checkout/orders/"+id+"/capture",Map.of(),UUID.nameUUIDFromBytes(("capture:"+orderId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());}
 private JsonNode send(String path,Object body,String key) {
  try{return api.post().uri(path).headers(h->{h.setBearerAuth(token());h.set("PayPal-Request-Id",key);h.set("Prefer","return=representation");}).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal request could not be completed. Check payment status before retrying.");}
 }
 public static String validatedCapture(JsonNode data,UUID orderId,long total) {
  if(!"COMPLETED".equals(data.path("status").asText()))return null;
  var units=data.path("purchase_units");
  if(units.size()!=1 || !orderId.toString().equals(units.get(0).path("custom_id").asText()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment order mismatch");
  var captures=units.get(0).path("payments").path("captures");
  if(captures.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment requires review");
  var capture=captures.get(0);var amount=capture.path("amount");
  if(!"COMPLETED".equals(capture.path("status").asText()))return null;
  if(!"AUD".equals(amount.path("currency_code").asText()) || new BigDecimal(amount.path("value").asText()).compareTo(BigDecimal.valueOf(total,2))!=0 || capture.path("id").asText().isBlank())throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment amount mismatch");
  return capture.path("id").asText();
 }
}
