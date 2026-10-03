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
@Component @lombok.extern.slf4j.Slf4j
public class PayPalPaymentProvider {
 private final RestClient api;
 private final String clientId,secret,returnUrl;
 private final boolean enabled;
 private final Set<String> approvalHosts;
 public PayPalPaymentProvider(@Value("${PAYPAL_ENABLED:false}") boolean enabled,@Value("${PAYPAL_ENV:sandbox}") String environment,
   @Value("${PAYPAL_CLIENT_ID:}") String clientId,@Value("${PAYPAL_CLIENT_SECRET:}") String secret,
   @Value("${PAYPAL_RETURN_URL:http://localhost:5173/#/order-confirmation}") String returnUrl) {
  this.enabled=enabled;this.clientId=clientId;this.secret=secret;this.returnUrl=returnUrl;
  if(!Set.of("sandbox","live").contains(environment))throw new IllegalArgumentException("PAYPAL_ENV must be sandbox or live");
  approvalHosts=environment.equals("sandbox")?Set.of("sandbox.paypal.com","www.sandbox.paypal.com"):Set.of("paypal.com","www.paypal.com");
  if(enabled && (clientId.isBlank() || secret.isBlank() || (!returnUrl.startsWith("https://") && environment.equals("live"))))throw new IllegalArgumentException("Configure PayPal credentials and HTTPS return URL");
  var callback=java.net.URI.create(returnUrl);
  if(enabled && (callback.getHost()==null || callback.getUserInfo()!=null || !Set.of("http","https").contains(callback.getScheme()) || !"/order-confirmation".equals(callback.getFragment())))throw new IllegalArgumentException("PAYPAL_RETURN_URL must use the /#/order-confirmation route");
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(15));
  api=RestClient.builder().requestFactory(factory).baseUrl(environment.equals("live")?"https://api-m.paypal.com":"https://api-m.sandbox.paypal.com").build();
 }
 public boolean enabled(){return enabled;}
 public boolean validApprovalUrl(String value) {
  if(value==null)return false;
  try {
   var uri=java.net.URI.create(value);
   return "https".equals(uri.getScheme()) && uri.getUserInfo()==null && uri.getPort()==-1 && approvalHosts.contains(uri.getHost());
  }catch(IllegalArgumentException e){return false;}
 }
 private String callback(String outcome){return org.springframework.web.util.UriComponentsBuilder.fromUriString(returnUrl).replaceQueryParam("paypal",outcome).build().toUriString();}
 private String token(){
  if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"PayPal is unavailable");
  try{String access=api.post().uri("/v1/oauth2/token").headers(h->h.setBasicAuth(clientId,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials").retrieve().body(JsonNode.class).path("access_token").asText();if(access.isBlank())throw new IllegalStateException();return access;}
  catch(Exception e){log.warn("payment_provider_unavailable provider=PAYPAL");throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal could not be reached. Please retry.");}
 }
 public JsonNode create(UUID orderId,long total) {
  var amount=Map.of("currency_code","AUD","value",BigDecimal.valueOf(total,2).toPlainString());
  return send("/v2/checkout/orders",Map.of("intent","CAPTURE","purchase_units",List.of(Map.of("custom_id",orderId.toString(),"amount",amount)),"payment_source",Map.of("paypal",Map.of("experience_context",Map.of("return_url",callback("return"),"cancel_url",callback("cancel"),"user_action","PAY_NOW","shipping_preference","NO_SHIPPING")))),orderId.toString());
 }
 public JsonNode details(String id) {
  try{return api.get().uri("/v2/checkout/orders/{id}",id).headers(h->h.setBearerAuth(token())).retrieve().body(JsonNode.class);}
  catch(Exception e){log.warn("payment_provider_unavailable provider=PAYPAL");throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal status is unavailable. Retry before paying again.");}
 }
 public JsonNode capture(String id,UUID orderId) {return send("/v2/checkout/orders/"+id+"/capture",Map.of(),UUID.nameUUIDFromBytes(("capture:"+orderId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());}
 private JsonNode send(String path,Object body,String key) {
  try{return api.post().uri(path).headers(h->{h.setBearerAuth(token());h.set("PayPal-Request-Id",key);h.set("Prefer","return=representation");}).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);}
  catch(Exception e){log.warn("payment_provider_unavailable provider=PAYPAL");throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal request could not be completed. Check payment status before retrying.");}
 }
 public static void validateOrder(JsonNode data,String providerId,UUID orderId,long total) {
  if(data==null || !providerId.equals(data.path("id").asText()) || !"CAPTURE".equals(data.path("intent").asText()))throw mismatch("Payment order mismatch");
  var units=data.path("purchase_units");
  if(!units.isArray() || units.size()!=1 || !orderId.toString().equals(units.get(0).path("custom_id").asText()))throw mismatch("Payment order mismatch");
  validateAmount(units.get(0).path("amount"),total);
 }
 private static void validateAmount(JsonNode amount,long total) {
  try {
   if(!"AUD".equals(amount.path("currency_code").asText()) || !amount.path("value").asText().matches("[0-9]+[.][0-9]{2}") || new BigDecimal(amount.path("value").asText()).compareTo(BigDecimal.valueOf(total,2))!=0)throw mismatch("Payment amount mismatch");
  }catch(NumberFormatException e){throw mismatch("Payment amount mismatch");}
 }
 private static ResponseStatusException mismatch(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
 public static String validatedCapture(JsonNode data,UUID orderId,long total) {
  if(data==null || !"COMPLETED".equals(data.path("status").asText()))return null;
  var units=data.path("purchase_units");
  if(!units.isArray() || units.size()!=1 || !orderId.toString().equals(units.get(0).path("custom_id").asText()))throw mismatch("Payment order mismatch");
  var captures=units.get(0).path("payments").path("captures");
  if(!captures.isArray() || captures.size()!=1)throw mismatch("Payment requires review");
  var capture=captures.get(0);
  if(!"COMPLETED".equals(capture.path("status").asText()))return null;
  validateAmount(capture.path("amount"),total);
  if(!capture.path("id").asText().matches("[A-Za-z0-9]{1,100}") || !capture.path("final_capture").asBoolean())throw mismatch("Payment requires review");
  return capture.path("id").asText();
 }
}
