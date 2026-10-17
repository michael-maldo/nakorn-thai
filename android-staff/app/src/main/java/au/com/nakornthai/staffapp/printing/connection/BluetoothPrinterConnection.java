package au.com.nakornthai.staffapp.printing.connection;

import android.bluetooth.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;

/** Secure RFCOMM SPP only. A successful connection does not verify a printing protocol. */
public final class BluetoothPrinterConnection implements PrinterConnection {
  private final BluetoothSocket socket;

  public BluetoothPrinterConnection(
      BluetoothAdapter adapter, String address, ScheduledExecutorService timer) throws IOException {
    socket = connect(adapter, address, timer);
  }

  private static BluetoothSocket connect(
      BluetoothAdapter adapter, String address, ScheduledExecutorService timer) throws IOException {
    BluetoothSocket candidate = null;
    ScheduledFuture<?> timeout = null;
    java.util.concurrent.atomic.AtomicBoolean expired =
        new java.util.concurrent.atomic.AtomicBoolean();
    try {
      if (adapter == null || !adapter.isEnabled())
        throw new IOException("Bluetooth unavailable or disabled");
      BluetoothDevice device = adapter.getRemoteDevice(address);
      if (device.getBondState() != BluetoothDevice.BOND_BONDED)
        throw new IOException("Pair this device in Android Bluetooth settings first");
      candidate =
          device.createRfcommSocketToServiceRecord(
              UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
      BluetoothSocket closing = candidate;
      timeout =
          timer.schedule(
              () -> {
                expired.set(true);
                try {
                  closing.close();
                } catch (IOException ignored) {
                }
              },
              8,
              TimeUnit.SECONDS);
      candidate.connect();
      return candidate;
    } catch (SecurityException e) {
      if (candidate != null)
        try {
          candidate.close();
        } catch (IOException ignored) {
        }
      throw new IOException("Bluetooth permission denied or revoked", e);
    } catch (IOException e) {
      if (candidate != null)
        try {
          candidate.close();
        } catch (IOException ignored) {
        }
      if (expired.get())
        throw new java.net.SocketTimeoutException("Bluetooth connection timed out");
      throw e;
    } finally {
      if (timeout != null) timeout.cancel(false);
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
