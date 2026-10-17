package au.com.nakornthai.staffapp.printing.connection;

import java.io.*;

public interface PrinterConnection extends AutoCloseable {
  void send(byte[] bytes) throws IOException;

  void close() throws IOException;
}
