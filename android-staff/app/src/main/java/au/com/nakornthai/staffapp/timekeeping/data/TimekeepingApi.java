package au.com.nakornthai.staffapp.timekeeping.data;

import au.com.nakornthai.staffapp.timekeeping.model.Attendance.*;
import au.com.nakornthai.staffapp.identity.data.Session;
import au.com.nakornthai.staffapp.shared.network.StaffApi.Csrf;
import java.util.*;
import retrofit2.Call;
import retrofit2.http.*;

public interface TimekeepingApi {
  @GET("api/restaurant/availability") Call<Restaurant> restaurant();
  @GET("api/identity/users") Call<List<Session.User>> staff(@Header("Authorization") String auth);
  @GET("api/staff/timekeeping/csrf") Call<Csrf> timekeepingCsrf(@Header("Authorization") String auth);
  @GET("api/staff/timekeeping/me") Call<Timesheet> myTimesheet(@Header("Authorization") String auth,
      @Query("from") String from, @Query("to") String to, @Query("page") int page, @Query("size") int size);
  @GET("api/staff/timekeeping/entries") Call<Timesheet> attendance(@Header("Authorization") String auth,
      @Query("staffUserId") String staff, @Query("from") String from, @Query("to") String to,
      @Query("page") int page, @Query("size") int size);
  @POST("api/staff/timekeeping/clock-in") Call<Entry> clockIn(@HeaderMap Map<String,String> headers, @Body Map<String,Object> body);
  @POST("api/staff/timekeeping/clock-out") Call<Entry> clockOut(@HeaderMap Map<String,String> headers, @Body Map<String,Object> body);
  @POST("api/staff/timekeeping/entries") Call<Entry> manualAttendance(@HeaderMap Map<String,String> headers, @Body Map<String,Object> body);
  @PATCH("api/staff/timekeeping/entries/{id}") Call<Entry> correctAttendance(@Path("id") String id,
      @HeaderMap Map<String,String> headers, @Body okhttp3.RequestBody body);
  @GET("api/staff/timekeeping/entries/{id}/audit") Call<AuditPage> attendanceAudit(@Path("id") String id,
      @Header("Authorization") String auth, @Query("page") int page, @Query("size") int size);
}
