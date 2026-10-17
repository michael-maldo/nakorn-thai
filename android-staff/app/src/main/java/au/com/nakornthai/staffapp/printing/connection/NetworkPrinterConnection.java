package au.com.nakornthai.staffapp.printing.connection;

import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public final class NetworkPrinterConnection implements PrinterConnection {
  private final Socket socket;
  private static final ExecutorService DNS =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(1),
          r -> {
            Thread t = new Thread(r, "printer-dns");
            t.setDaemon(true);
            return t;
          });

  public NetworkPrinterConnection(PrinterProfile p) throws IOException {
    PrinterProfile.validateEndpoint(p.endpoint, p.port);
    socket = new Socket();
    // Bound DNS separately: Socket.connect timeout does not bound name resolution.
    Future<InetAddress> address;
    try {
      address = DNS.submit(() -> InetAddress.getByName(p.endpoint));
    } catch (RejectedExecutionException e) {
      close();
      throw new IOException("Printer hostname resolver busy; try later", e);
    }
    try {
      InetAddress resolved = address.get(4, TimeUnit.SECONDS);
      socket.connect(new InetSocketAddress(resolved, p.port), 4000);
      socket.setSoTimeout(4000);
    } catch (TimeoutException e) {
      close();
      throw new SocketTimeoutException("Hostname resolution timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      close();
      throw new IOException("Connection cancelled", e);
    } catch (ExecutionException e) {
      close();
      throw new IOException("Hostname could not be resolved", e.getCause());
    } catch (IOException e) {
      close();
      throw e;
    } finally {
      address.cancel(true);
    }
  }

  public void send(byte[] b) throws IOException {
    socket.getOutputStream().write(b);
    socket.getOutputStream().flush();
  }

  public void close() throws IOException {
    socket.close();
  }
}
