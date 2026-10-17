package au.com.nakornthai.staffapp.printing.discovery;

import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import java.util.*;
import java.util.function.Consumer;

public final class NetworkPrinterDiscovery implements PrinterDiscovery {
  private final NsdManager manager;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<NsdManager.DiscoveryListener> listeners = new ArrayList<>();
  private int generation;
  private final ArrayDeque<NsdServiceInfo> pending = new ArrayDeque<>();
  private boolean resolving;

  public NetworkPrinterDiscovery(Context c) {
    manager = c.getSystemService(NsdManager.class);
  }

  public void start(Consumer<Device> found, Consumer<String> status) {
    stop();
    int token = generation;
    if (manager == null) {
      status.accept("Network discovery unavailable; configure host manually");
      return;
    }
    for (String type :
        new String[] {"_pdl-datastream._tcp.", "_printer._tcp.", "_ipp._tcp.", "_ipps._tcp."}) {
      NsdManager.DiscoveryListener l =
          new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String t) {}

            public void onDiscoveryStopped(String t) {}

            public void onStartDiscoveryFailed(String t, int e) {
              status.accept(
                  "Some discovery services unavailable ("
                      + e
                      + "); manual LAN configuration remains available");
            }

            public void onStopDiscoveryFailed(String t, int e) {}

            public void onServiceLost(NsdServiceInfo s) {}

            public void onServiceFound(NsdServiceInfo s) {
              handler.post(
                  () -> {
                    if (token != generation) return;
                    pending.add(s);
                    resolveNext(token, found, status);
                  });
            }
          };
      listeners.add(l);
      try {
        manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l);
      } catch (RuntimeException e) {
        status.accept("Discovery unavailable; configure host manually");
      }
    }
    status.accept(
        "Discovering advertised LAN printing services for 15 seconds. IPP/LPR advertisements do not"
            + " imply raw ESC/POS support.");
    handler.postDelayed(
        () -> {
          stop();
          status.accept(
              "LAN discovery finished. Manual configuration is available for non-advertising"
                  + " devices.");
        },
        15000);
  }

  private void resolveNext(int token, Consumer<Device> found, Consumer<String> status) {
    if (token != generation || resolving || pending.isEmpty()) return;
    resolving = true;
    manager.resolveService(
        pending.remove(),
        new NsdManager.ResolveListener() {
          public void onResolveFailed(NsdServiceInfo s, int e) {
            handler.post(
                () -> {
                  if (token == generation) {
                    resolving = false;
                    resolveNext(token, found, status);
                  }
                });
          }

          public void onServiceResolved(NsdServiceInfo s) {
            handler.post(
                () -> {
                  if (token != generation) return;
                  String host = s.getHost() == null ? "" : s.getHost().getHostAddress();
                  found.accept(
                      new Device(
                          s.getServiceName(),
                          host,
                          s.getServiceType() + "; " + s.getAttributes().keySet(),
                          "LAN",
                          s.getPort()));
                  resolving = false;
                  resolveNext(token, found, status);
                });
          }
        });
  }

  public void stop() {
    generation++;
    handler.removeCallbacksAndMessages(null);
    pending.clear();
    resolving = false;
    for (NsdManager.DiscoveryListener l : listeners)
      try {
        manager.stopServiceDiscovery(l);
      } catch (RuntimeException ignored) {
      }
    listeners.clear();
  }
}
