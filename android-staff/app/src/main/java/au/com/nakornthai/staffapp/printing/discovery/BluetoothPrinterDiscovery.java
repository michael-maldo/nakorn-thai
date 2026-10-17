package au.com.nakornthai.staffapp.printing.discovery;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.core.content.ContextCompat;
import java.util.function.Consumer;

public final class BluetoothPrinterDiscovery implements PrinterDiscovery {
  private final Context context;
  private final boolean ble;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private BluetoothAdapter adapter;
  private BluetoothLeScanner scanner;
  private BroadcastReceiver receiver;
  private ScanCallback callback;

  public BluetoothPrinterDiscovery(Context c, boolean b) {
    context = c.getApplicationContext();
    ble = b;
  }

  public static String[] permissions() {
    return Build.VERSION.SDK_INT >= 31
        ? new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
        : new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
  }

  public static boolean permitted(Context c) {
    for (String p : permissions())
      if (ContextCompat.checkSelfPermission(c, p) != PackageManager.PERMISSION_GRANTED)
        return false;
    return true;
  }

  public void start(Consumer<Device> found, Consumer<String> status) {
    stop();
    if (!permitted(context)) {
      status.accept("Bluetooth discovery permission denied. Grant it to discover devices.");
      return;
    }
    try {
      BluetoothManager m = context.getSystemService(BluetoothManager.class);
      adapter = m == null ? null : m.getAdapter();
      if (adapter == null) {
        status.accept("Bluetooth adapter unavailable");
        return;
      }
      if (!adapter.isEnabled()) {
        status.accept("Bluetooth disabled. Enable it in Android settings.");
        return;
      }
      for (BluetoothDevice d : adapter.getBondedDevices()) {
        if (ble && d.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC) continue;
        if (!ble && d.getType() == BluetoothDevice.DEVICE_TYPE_LE) continue;
        found.accept(
            new Device(
                d.getName(),
                d.getAddress(),
                "Paired; cached services: "
                    + (d.getUuids() == null ? "Unknown" : java.util.Arrays.toString(d.getUuids()))
                    + "; printing protocol unverified",
                ble ? "BLE" : "CLASSIC",
                0));
      }
      if (ble) {
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
          status.accept("BLE scanner unavailable");
          return;
        }
        callback =
            new ScanCallback() {
              public void onScanResult(int type, ScanResult r) {
                try {
                  found.accept(
                      new Device(
                          r.getDevice().getName(),
                          r.getDevice().getAddress(),
                          "BLE advertised services: "
                              + (r.getScanRecord() == null
                                      || r.getScanRecord().getServiceUuids() == null
                                  ? "Unknown"
                                  : r.getScanRecord().getServiceUuids().toString())
                              + "; GATT printing unsupported",
                          "BLE",
                          0));
                } catch (SecurityException e) {
                  status.accept("Bluetooth permission revoked");
                  stop();
                }
              }

              public void onScanFailed(int error) {
                status.accept("BLE scan failed: " + error);
                stop();
              }
            };
        scanner.startScan(callback);
      } else {
        receiver =
            new BroadcastReceiver() {
              public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null)
                  try {
                    found.accept(
                        new Device(
                            d.getName(),
                            d.getAddress(),
                            "Discoverable; pair in Android settings before SPP connection",
                            "CLASSIC",
                            0));
                  } catch (SecurityException e) {
                    status.accept("Bluetooth permission revoked");
                    stop();
                  }
              }
            };
        ContextCompat.registerReceiver(
            context,
            receiver,
            new IntentFilter(BluetoothDevice.ACTION_FOUND),
            ContextCompat.RECEIVER_EXPORTED);
        if (!adapter.startDiscovery()) {
          stop();
          status.accept("Bluetooth discovery could not start");
          return;
        }
      }
      status.accept(
          "Scanning for 15 seconds. Devices are not necessarily printers. On Android 11 or earlier,"
              + " location services may also need enabling.");
      handler.postDelayed(
          () -> {
            stop();
            status.accept(
                "Discovery finished. Select a device or use Android Bluetooth settings to pair.");
          },
          15000);
    } catch (SecurityException e) {
      stop();
      status.accept("Bluetooth permission denied or revoked");
    }
  }

  public void stop() {
    handler.removeCallbacksAndMessages(null);
    try {
      if (scanner != null && callback != null) scanner.stopScan(callback);
      if (adapter != null && !ble && adapter.isDiscovering()) adapter.cancelDiscovery();
    } catch (SecurityException ignored) {
    }
    if (receiver != null) {
      try {
        context.unregisterReceiver(receiver);
      } catch (IllegalArgumentException ignored) {
      }
    }
    receiver = null;
    scanner = null;
    callback = null;
  }
}
