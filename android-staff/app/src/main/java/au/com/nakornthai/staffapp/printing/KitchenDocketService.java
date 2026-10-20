package au.com.nakornthai.staffapp.printing;

import au.com.nakornthai.staffapp.ordering.model.Order;
import au.com.nakornthai.staffapp.printing.connection.*;
import au.com.nakornthai.staffapp.printing.data.PrinterConfigurationRepository;
import au.com.nakornthai.staffapp.printing.driver.*;
import au.com.nakornthai.staffapp.printing.formatting.OrderDocketFormatter;
import au.com.nakornthai.staffapp.printing.formatting.OnlineOrderDocketFormatter;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Uses the existing device-local Kitchen profile and transport; never retries an uncertain send. */
public final class KitchenDocketService implements KitchenDocketPrinter {
  private final PrinterConfigurationRepository profiles;
  private final PrinterConnectionManager connections;
  private final Executor executor;
  private final ScheduledExecutorService timer;
  private final AtomicBoolean busy = new AtomicBoolean();

  public KitchenDocketService(PrinterConfigurationRepository profiles,
      PrinterConnectionManager connections, Executor executor, ScheduledExecutorService timer) {
    this.profiles = profiles; this.connections = connections; this.executor = executor; this.timer = timer;
  }

  public boolean print(Order order, String pickupMinutes, Consumer<String> result) {
    if (!busy.compareAndSet(false, true)) return false;
    executor.execute(() -> {
      String outcome;
      boolean sending = false;
      try {
        PrinterProfile profile = selectKitchenProfile();
        PrinterDriver driver = new PrinterDriverRegistry().get(profile.driver);
        if (!"LAN".equals(profile.type))
          throw new IllegalStateException("Kitchen docket printing requires a LAN/TCP printer profile.");
        if (!(driver instanceof EscPosPrinterDriver) || !driver.canPrint(profile))
          throw new IllegalStateException("Kitchen printer is not enabled for confirmed ESC/POS printing.");
        byte[] bytes = "DETAILED_ONLINE".equals(profile.docketFormat)
            ? OnlineOrderDocketFormatter.format(profile, order, pickupMinutes)
            : OrderDocketFormatter.format(profile, order, pickupMinutes);
        try (PrinterConnection connection = connections.open(profile)) {
          ScheduledFuture<?> deadline = timer.schedule(() -> {
            try { connection.close(); } catch (Exception ignored) { }
          }, 8000, TimeUnit.MILLISECONDS);
          try { sending = true; connection.send(bytes); }
          finally { deadline.cancel(false); }
        }
        outcome = "Kitchen docket sent for #" + reference(order) + ". Printer output is not device-confirmed.";
      } catch (Exception failure) {
        outcome = sending
            ? "Kitchen docket outcome uncertain for #" + reference(order) + ". Order acceptance remains saved. Inspect printer before using Reprint; no automatic retry."
            : "Kitchen docket failed for #" + reference(order) + ": " + message(failure) + " Order acceptance remains saved.";
      }
      try { result.accept(outcome); } finally { busy.set(false); }
    });
    return true;
  }

  private PrinterProfile selectKitchenProfile() {
    List<PrinterProfile> matches = new ArrayList<>();
    for (PrinterProfile profile : profiles.load())
      if ("Kitchen".equals(profile.assignment) && profile.enabled) matches.add(profile);
    if (matches.isEmpty()) throw new IllegalStateException("No enabled Kitchen printer is configured.");
    if (matches.size() > 1) throw new IllegalStateException("Multiple enabled Kitchen printers are configured; choose one in Printer Management.");
    return matches.get(0);
  }

  private static String message(Exception e) {
    if (e instanceof SocketTimeoutException) return "printer connection timed out";
    if (e instanceof ConnectException) return "printer connection was refused or unreachable";
    if (e instanceof NoRouteToHostException) return "printer host is unreachable";
    if (e instanceof UnknownHostException) return "printer hostname could not be resolved";
    return e.getMessage() == null ? "printer operation failed" : e.getMessage();
  }

  private static String reference(Order order) {
    return order == null || order.reference == null ? "unknown" : order.reference;
  }
}
