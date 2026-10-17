package au.com.nakornthai.staffapp.printing.connection;

import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.io.IOException;

public interface PrinterConnectionManager {
  PrinterConnection open(PrinterProfile profile) throws IOException;
}
