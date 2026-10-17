package au.com.nakornthai.staffapp.printing.driver;

import au.com.nakornthai.staffapp.printing.model.PrinterProfile;

public final class UnsupportedPrinterDriver implements PrinterDriver {
  public au.com.nakornthai.staffapp.printing.model.PrinterCapabilities capabilities() {
    return new au.com.nakornthai.staffapp.printing.model.PrinterCapabilities(
        false,
        new int[0],
        "None",
        "No supported command generation. Physical test printing disabled.");
  }

  public String id() {
    return "unsupported";
  }

  public String label() {
    return "Unsupported / Unverified";
  }

  public boolean canPrint(PrinterProfile p) {
    return false;
  }

  public byte[] testDocket(PrinterProfile p, java.time.Instant now) {
    throw new IllegalArgumentException("Select and confirm a compatible driver before printing");
  }
}
