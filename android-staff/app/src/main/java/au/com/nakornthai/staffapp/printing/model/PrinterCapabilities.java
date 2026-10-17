package au.com.nakornthai.staffapp.printing.model;

/** Driver declarations, never evidence of physical device capabilities. */
public final class PrinterCapabilities {
  public final boolean text, cut, statusQuery, thai;
  public final String encoding, description;
  public final int[] paperWidths;

  public PrinterCapabilities(boolean text, int[] widths, String encoding, String description) {
    this.text = text;
    this.paperWidths = widths.clone();
    this.encoding = encoding;
    this.description = description;
    cut = false;
    statusQuery = false;
    thai = false;
  }
}
