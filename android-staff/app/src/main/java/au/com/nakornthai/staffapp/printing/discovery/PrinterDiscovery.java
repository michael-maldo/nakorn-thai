package au.com.nakornthai.staffapp.printing.discovery;

import java.util.function.Consumer;

public interface PrinterDiscovery {
  final class Device {
    public final String name, identifier, services, type;
    public final int port;

    public Device(String n, String i, String s, String t, int p) {
      name = n == null ? "Unknown" : n;
      identifier = i;
      services = s;
      type = t;
      port = p;
    }

    public String toString() {
      return name
          + " · "
          + identifier
          + (port > 0 ? ":" + port : "")
          + " · "
          + services
          + " (compatibility unknown)";
    }
  }

  void start(Consumer<Device> found, Consumer<String> status);

  void stop();
}
