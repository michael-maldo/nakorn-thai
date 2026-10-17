package au.com.nakornthai.staffapp.printing.driver;

import java.util.*;

public final class PrinterDriverRegistry {
  private final Map<String, PrinterDriver> drivers = new LinkedHashMap<>();

  public PrinterDriverRegistry() {
    register(new UnsupportedPrinterDriver());
    register(new EscPosPrinterDriver());
  }

  public void register(PrinterDriver d) {
    if (drivers.putIfAbsent(d.id(), d) != null)
      throw new IllegalArgumentException("Duplicate driver");
  }

  public java.util.List<PrinterDriver> all() {
    return new ArrayList<>(drivers.values());
  }

  public PrinterDriver get(String id) {
    return drivers.getOrDefault(id, drivers.get("unsupported"));
  }
}
