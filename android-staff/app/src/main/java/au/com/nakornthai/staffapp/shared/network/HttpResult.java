package au.com.nakornthai.staffapp.shared.network;

import com.google.gson.*;
import java.io.IOException;
import retrofit2.*;

public final class HttpResult {
  private HttpResult() {}

  public static <T> T execute(Call<T> call) throws IOException {
    Response<T> response = call.execute();
    if (response.isSuccessful()) return response.body(); // HTTP 204 intentionally returns null.
    String message =
        response.code() == 401
            ? "Session expired or revoked. Please sign in again."
            : response.code() == 403
                ? "This action is not permitted, or its security token expired."
                : "Request failed (" + response.code() + ").";
    if (response.errorBody() != null) {
      try (okhttp3.ResponseBody body = response.errorBody()) {
        JsonElement parsed = JsonParser.parseString(body.string());
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("message"))
          message = parsed.getAsJsonObject().get("message").getAsString();
      } catch (RuntimeException ignored) {
        /* Proxy errors are not always JSON. */
      }
    }
    throw new ApiFailure(response.code(), message);
  }
}
