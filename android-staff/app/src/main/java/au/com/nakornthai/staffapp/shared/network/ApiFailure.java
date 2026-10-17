package au.com.nakornthai.staffapp.shared.network;

import java.io.IOException;

public final class ApiFailure extends IOException {
  public final int status;
  public final boolean uncertain;

  public ApiFailure(int status, String message) {
    this(status, message, false);
  }

  public ApiFailure(int status, String message, boolean uncertain) {
    super(message);
    this.status = status;
    this.uncertain = uncertain;
  }
}
