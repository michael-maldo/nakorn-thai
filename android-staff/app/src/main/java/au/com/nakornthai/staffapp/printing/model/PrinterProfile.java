package au.com.nakornthai.staffapp.printing.model;

import java.time.ZoneId;
import java.util.*;

public final class PrinterProfile {
  public int schemaVersion = 1;
  public String id = UUID.randomUUID().toString(),
      name = "",
      type = "LAN",
      endpoint = "",
      driver = "unsupported",
      assignment = "Kitchen",
      timezone = "Australia/Melbourne";
  public int port = 9100, paperWidth = 58;
  public boolean enabled = true, compatibilityConfirmed = false;
  public String manufacturer = "Unknown",
      model = "Unknown",
      discoveredServices = "",
      lastDiagnostic = "Not tested";
  public long diagnosticAt;

  public PrinterProfile copy() {
    return new com.google.gson.Gson()
        .fromJson(new com.google.gson.Gson().toJson(this), PrinterProfile.class);
  }

  public void validate() {
    if (manufacturer == null) manufacturer = "Unknown";
    if (model == null) model = "Unknown";
    if (discoveredServices == null) discoveredServices = "";
    if (lastDiagnostic == null) lastDiagnostic = "Not tested";
    if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported profile schema");
    try {
      UUID.fromString(id);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid profile identifier");
    }
    if (name == null || name.trim().isEmpty() || name.length() > 100)
      throw new IllegalArgumentException("Enter a friendly name (maximum 100 characters)");
    if (!Arrays.asList("LAN", "CLASSIC", "BLE").contains(type))
      throw new IllegalArgumentException("Invalid transport");
    if ("LAN".equals(type)) {
      validateEndpoint(endpoint, port);
    } else if (endpoint == null || !endpoint.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))
      throw new IllegalArgumentException("Select a Bluetooth device");
    if (!Arrays.asList("Kitchen", "FOH").contains(assignment))
      throw new IllegalArgumentException("Invalid assignment");
    if (driver == null || !driver.matches("[a-zA-Z0-9_-]{1,64}"))
      throw new IllegalArgumentException("Unknown driver");
    if (paperWidth != 58 && paperWidth != 80)
      throw new IllegalArgumentException("Supported paper widths: 58 or 80 mm");
    try {
      ZoneId.of(timezone);
    } catch (Exception e) {
      throw new IllegalArgumentException("Enter a valid restaurant timezone");
    }
  }

  public static void validateEndpoint(String host, int port) {
    if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1–65535");
    if (host == null
        || host.length() > 253
        || host.isBlank()
        || host.contains("://")
        || host.contains("/")
        || host.contains("@")
        || host.contains(" "))
      throw new IllegalArgumentException(
          "Enter a hostname or IP address, without URL or credentials");
    if (host.contains(":")) {
      if (!host.matches("[0-9a-fA-F:]+(?:%[A-Za-z0-9_.-]{1,32})?")
          || host.chars().filter(c -> c == ':').count() < 2)
        throw new IllegalArgumentException("Invalid IPv6 address");
      try {
        if (!(java.net.InetAddress.getByName(host.split("%", 2)[0])
            instanceof java.net.Inet6Address)) throw new Exception();
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid IPv6 address");
      }
    } else if (host.matches("[0-9.]+")) {
      String[] parts = host.split("\\.", -1);
      if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4 address");
      for (String p : parts)
        try {
          if (p.isEmpty() || Integer.parseInt(p) > 255 || p.length() > 3) throw new Exception();
        } catch (Exception e) {
          throw new IllegalArgumentException("Invalid IPv4 address");
        }
    } else {
      for (String label : host.split("\\.", -1))
        if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"))
          throw new IllegalArgumentException("Invalid hostname");
    }
  }
}
