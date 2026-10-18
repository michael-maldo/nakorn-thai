package au.com.nakornthai.staffapp.app;

import android.content.Context;
import au.com.nakornthai.staffapp.BuildConfig;
import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.ordering.data.*;
import au.com.nakornthai.staffapp.ordering.monitoring.*;
import au.com.nakornthai.staffapp.shared.network.*;
import au.com.nakornthai.staffapp.shared.security.*;
import java.util.concurrent.*;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class AppGraph {
  public long heardAlertSequence;
  public final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  public final IdentityRepository identity;
  public final OrderMonitor monitor;
  public final OrderRepository orders;
  public final au.com.nakornthai.staffapp.timekeeping.data.TimekeepingRepository timekeeping;

  public AppGraph(Context context) {
    this(BuildConfig.API_BASE_URL, new KeystoreCredentialStore(context));
  }

  public AppGraph(String baseUrl, CredentialStore storage) {
    HttpUrl origin = HttpUrl.get(baseUrl);
    if (!origin.isHttps()
        && !(BuildConfig.DEBUG
            && (origin.host().equals("10.0.2.2")
                || origin.host().equals("localhost")
                || origin.host().equals("127.0.0.1"))))
      throw new IllegalArgumentException("Remote API requires HTTPS");
    SessionCookieJar jar = new SessionCookieJar(storage, origin);
    OkHttpClient client =
        new OkHttpClient.Builder()
            .cookieJar(jar)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build();
    StaffApi api =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StaffApi.class);
    identity = new IdentityRepository(api, jar);
    orders = new ApiOrderRepository(api, identity);
    timekeeping = new au.com.nakornthai.staffapp.timekeeping.data.TimekeepingRepository(api, identity);
    monitor = new OrderMonitor(executor, orders, identity);
  }
}
