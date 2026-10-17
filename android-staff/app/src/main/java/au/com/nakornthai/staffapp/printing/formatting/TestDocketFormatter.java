package au.com.nakornthai.staffapp.printing.formatting;

import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class TestDocketFormatter {
  public static String format(PrinterProfile p, Instant now) {
    String text =
        "NAKORN THAI\nPRINTER TEST\n\nPrinter: "
            + p.name
            + "\nAssignment: "
            + p.assignment
            + "\nConnection: "
            + p.type
            + "\nDriver: "
            + p.driver
            + "\nDate/Time: "
            + DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")
                .withZone(ZoneId.of(p.timezone))
                .format(now)
            + "\n\nTEST PRINT\n1234567890\nABCDEFGHIJKLMNOPQRSTUVWXYZ\n\nEnd of test\n\n\n";
    int width = p.paperWidth == 80 ? 48 : 32;
    StringBuilder out = new StringBuilder();
    int col = 0;
    for (char c : text.toCharArray()) {
      if (c == '\n') {
        out.append(c);
        col = 0;
      } else {
        if (col == width) {
          out.append('\n');
          col = 0;
        }
        out.append(c >= 32 && c <= 126 ? c : '?');
        col++;
      }
    }
    return out.toString();
  }
}
