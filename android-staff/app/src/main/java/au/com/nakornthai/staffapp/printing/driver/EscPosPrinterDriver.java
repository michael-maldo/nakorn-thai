package au.com.nakornthai.staffapp.printing.driver;

import au.com.nakornthai.staffapp.printing.formatting.TestDocketFormatter;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;

public final class EscPosPrinterDriver implements PrinterDriver {
  public au.com.nakornthai.staffapp.printing.model.PrinterCapabilities capabilities() {
    return new au.com.nakornthai.staffapp.printing.model.PrinterCapabilities(
        true,
        new int[] {58, 80},
        "US-ASCII",
        "Driver-declared: ASCII text, 58/80 mm layout. Non-ASCII replaced with ?. Cutter, Thai text"
            + " and status queries unsupported. No device-response verification.");
  }

  public String id() {
    return "escpos";
  }

  public String label() {
    return "Generic ESC/POS text (operator-confirmed)";
  }

  public boolean canPrint(PrinterProfile p) {
    return p.enabled
        && p.compatibilityConfirmed
        && !"BLE".equals(p.type)
        && "escpos".equals(p.driver);
  }

  public byte[] testDocket(PrinterProfile p, java.time.Instant now) {
    p.validate();
    if (!canPrint(p))
      throw new IllegalArgumentException(
          "ESC/POS compatibility must be confirmed; BLE printing is unsupported");
    return TestDocketFormatter.format(p, now).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }
}
