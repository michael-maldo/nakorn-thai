package au.com.nakornthai.staffapp.shared.network;

import au.com.nakornthai.staffapp.identity.data.Session;
import au.com.nakornthai.staffapp.ordering.model.Order;
import com.google.gson.JsonObject;
import java.util.*;
import retrofit2.Call;
import retrofit2.http.*;

public interface StaffApi {
  final class Csrf {
    public String headerName, token;
  }

  @GET("api/identity/csrf")
  Call<Csrf> identityCsrf();

  @POST("api/identity/login")
  Call<Session> login(@HeaderMap Map<String, String> headers, @Body Map<String, String> body);

  @POST("api/identity/refresh")
  Call<Session> refresh(@HeaderMap Map<String, String> headers, @Body Map<String, String> body);

  @POST("api/identity/logout")
  Call<Void> logout(@HeaderMap Map<String, String> headers, @Body Map<String, String> body);

  @GET("api/identity/me")
  Call<Session.User> me(@Header("Authorization") String authorization);

  @GET("api/staff/foh/orders")
  Call<List<Order>> orders(
      @Header("Authorization") String authorization, @Query("history") boolean history);

  @GET("api/staff/kitchen/orders")
  Call<List<Order>> kitchen(@Header("Authorization") String authorization);

  @GET("api/staff/orders/{id}")
  Call<Order> order(@Path("id") String id, @Header("Authorization") String authorization);

  @GET("api/staff/orders/csrf")
  Call<Csrf> orderCsrf(@Header("Authorization") String authorization);

  @PATCH("api/staff/orders/{id}/status")
  Call<Void> status(
      @Path("id") String id,
      @HeaderMap Map<String, String> headers,
      @Body Map<String, Object> command);

  @POST("api/staff/payments/{id}/payid-confirm")
  Call<JsonObject> confirmPayid(
      @Path("id") String id,
      @HeaderMap Map<String, String> headers,
      @Body Map<String, Object> command);

  @POST("api/staff/payments/{id}/check")
  Call<JsonObject> checkPaypal(
      @Path("id") String id,
      @HeaderMap Map<String, String> headers,
      @Body Map<String, Object> command);
}
