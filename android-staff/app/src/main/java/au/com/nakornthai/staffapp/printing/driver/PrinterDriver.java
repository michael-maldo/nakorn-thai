package au.com.nakornthai.staffapp.printing.driver;

import au.com.nakornthai.staffapp.printing.model.PrinterProfile;

public interface PrinterDriver {
  au.com.nakornthai.staffapp.printing.model.PrinterCapabilities capabilities();

  String id();

  String label();

  boolean canPrint(PrinterProfile p);

  byte[] testDocket(PrinterProfile p, java.time.Instant now);
}
