package au.com.nakornthai.staffapp.printing.diagnostics;

import au.com.nakornthai.staffapp.printing.connection.*;
import au.com.nakornthai.staffapp.printing.driver.*;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

public final class PrinterDiagnostics {
  private final AtomicBoolean busy = new AtomicBoolean();
  private final PrinterConnectionManager connections;
  private final Executor executor;
  private final ScheduledExecutorService timer;
  private final long writeTimeoutMillis;

  public PrinterDiagnostics(PrinterConnectionManager c, Executor e, ScheduledExecutorService t) {
    this(c, e, t, 8000);
  }

  public PrinterDiagnostics(
      PrinterConnectionManager c, Executor e, ScheduledExecutorService t, long timeoutMillis) {
    if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout must be positive");
    writeTimeoutMillis = timeoutMillis;
    connections = c;
    executor = e;
    timer = t;
  }

  public boolean busy() {
    return busy.get();
  }

  public boolean run(
      PrinterProfile p,
      boolean print,
      BooleanSupplier authorized,
      Consumer<String> beforeSend,
      Consumer<String> done) {
    if (!busy.compareAndSet(false, true)) return false;
    executor.execute(
        () -> {
          String result;
          boolean sending = false;
          try {
            p.validate();
            if (!authorized.getAsBoolean())
              throw new IllegalStateException("ADMIN session required");
            if (!p.enabled) throw new IllegalStateException("Printer disabled");
            PrinterDriver driver = new PrinterDriverRegistry().get(p.driver);
            byte[] bytes = print ? driver.testDocket(p, Instant.now()) : null;
            try (PrinterConnection connection = connections.open(p)) {
              if (!authorized.getAsBoolean())
                throw new IllegalStateException("Session changed; operation cancelled");
              if (print) {
                beforeSend.accept(
                    "Transmission started — outcome uncertain if interrupted. Inspect paper before"
                        + " retrying.");
                sending = true;
                ScheduledFuture<?> deadline =
                    timer.schedule(
                        () -> {
                          try {
                            connection.close();
                          } catch (Exception ignored) {
                          }
                        },
                        writeTimeoutMillis,
                        TimeUnit.MILLISECONDS);
                try {
                  connection.send(bytes);
                } finally {
                  deadline.cancel(false);
                }
                result =
                    "Sent to printer — physical output not confirmed. Protocol compatibility is"
                        + " operator-confirmed, not device-verified.";
              } else
                result =
                    "Transport reachable — protocol compatibility and physical printing not"
                        + " verified";
            }
          } catch (Exception e) {
            result =
                sending
                    ? "Uncertain transmission outcome — inspect printer before an explicit new"
                        + " test. No automatic retry."
                    : message(e);
          }
          try {
            done.accept(result);
          } finally {
            busy.set(false);
          }
        });
    return true;
  }

  private static String message(Exception e) {
    if (e instanceof SecurityException) return "Bluetooth permission denied or revoked";
    if (e instanceof java.net.SocketTimeoutException) return "Connection timed out";
    if (e instanceof java.net.ConnectException) return "Connection refused or unreachable";
    if (e instanceof java.net.NoRouteToHostException) return "Host unreachable";
    if (e instanceof java.net.UnknownHostException) return "Host could not be resolved";
    return e.getMessage() == null ? "Printer operation failed" : e.getMessage();
  }
}
