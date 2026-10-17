package au.com.nakornthai.staffapp.printing.connection;

import android.bluetooth.*;
import android.content.Context;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.io.*;

public final class AndroidPrinterConnections implements PrinterConnectionManager {
  private final Context context;
  private final java.util.concurrent.ScheduledExecutorService timer;

  public AndroidPrinterConnections(Context c, java.util.concurrent.ScheduledExecutorService t) {
    context = c.getApplicationContext();
    timer = t;
  }

  public PrinterConnection open(PrinterProfile p) throws IOException {
    if ("LAN".equals(p.type)) return new NetworkPrinterConnection(p);
    if ("BLE".equals(p.type))
      throw new IOException("BLE discovery only: no supported GATT printing protocol selected");
    BluetoothManager m = context.getSystemService(BluetoothManager.class);
    return new BluetoothPrinterConnection(m == null ? null : m.getAdapter(), p.endpoint, timer);
  }
}
