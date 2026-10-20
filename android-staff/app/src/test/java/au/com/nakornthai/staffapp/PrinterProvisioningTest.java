package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;

import au.com.nakornthai.staffapp.identity.data.Session;
import au.com.nakornthai.staffapp.printing.connection.*;
import au.com.nakornthai.staffapp.printing.data.*;
import au.com.nakornthai.staffapp.printing.diagnostics.*;
import au.com.nakornthai.staffapp.printing.driver.*;
import au.com.nakornthai.staffapp.printing.formatting.*;
import au.com.nakornthai.staffapp.printing.model.*;
import au.com.nakornthai.staffapp.printing.KitchenDocketService;
import au.com.nakornthai.staffapp.printing.formatting.OrderDocketFormatter;
import au.com.nakornthai.staffapp.ordering.model.Order;
import au.com.nakornthai.staffapp.printing.ui.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;

public class PrinterProvisioningTest {
  PrinterProfile profile() {
    PrinterProfile p = new PrinterProfile();
    p.name = "Test kitchen";
    p.endpoint = "printer.local";
    return p;
  }

  static final class Memory implements PrinterConfigurationRepository.Store {
    String value = "{\"schemaVersion\":1,\"profiles\":[]}";

    public String read() {
      return value;
    }

    public boolean write(String s) {
      value = s;
      return true;
    }
  }

  @Test
  public void endpointsAndProfilesValidate() {
    PrinterProfile p = profile();
    p.validate();
    for (String host : new String[] {"192.168.1.9", "kitchen.local", "2001:db8::1"})
      PrinterProfile.validateEndpoint(host, 9100);
    for (String host :
        new String[] {
          "http://host", "host:9100", "256.1.1.1", "host/path", "a..b", "user@host", "", "x y"
        })
      assertThrows(
          IllegalArgumentException.class, () -> PrinterProfile.validateEndpoint(host, 9100));
    assertThrows(
        IllegalArgumentException.class, () -> PrinterProfile.validateEndpoint("host", 65536));
    p.timezone = "bad/time";
    assertThrows(IllegalArgumentException.class, p::validate);
  }

  @Test
  public void persistenceRestoresMultipleProfilesAndDiagnostics() {
    Memory m = new Memory();
    PrinterConfigurationRepository r = new PrinterConfigurationRepository(m);
    PrinterProfile a = profile(), b = profile();
    b.assignment = "FOH";
    b.enabled = false;
    b.lastDiagnostic = "Uncertain";
    b.diagnosticAt = 123;
    r.save(Arrays.asList(a, b));
    List<PrinterProfile> restored = new PrinterConfigurationRepository(m).load();
    assertEquals(2, restored.size());
    assertEquals("FOH", restored.get(1).assignment);
    assertFalse(restored.get(1).enabled);
    assertEquals(123, restored.get(1).diagnosticAt);
  }

  @Test
  public void malformedAndFutureSchemasAreNotOverwritten() {
    for (String value : new String[] {"bad", "{\"schemaVersion\":99,\"profiles\":[]}"}) {
      Memory m = new Memory();
      m.value = value;
      PrinterConfigurationRepository r = new PrinterConfigurationRepository(m);
      assertTrue(r.load().isEmpty());
      assertThrows(IllegalStateException.class, () -> r.save(List.of(profile())));
      assertEquals(value, m.value);
    }
  }

  @Test
  public void invalidIndividualProfileIsSkipped() {
    Memory m = new Memory();
    PrinterProfile p = profile();
    p.port = -1;
    m.value = "{\"schemaVersion\":1,\"profiles\":[" + new com.google.gson.Gson().toJson(p) + "]}";
    PrinterConfigurationRepository r = new PrinterConfigurationRepository(m);
    assertTrue(r.load().isEmpty());
    assertTrue(r.warning.contains("skipped"));
  }

  @Test
  public void registryAndCapabilitiesRequireExplicitCompatibility() {
    PrinterDriverRegistry registry = new PrinterDriverRegistry();
    PrinterProfile p = profile();
    assertFalse(registry.get(p.driver).canPrint(p));
    assertThrows(
        IllegalArgumentException.class, () -> registry.get("unknown").testDocket(p, Instant.now()));
    p.driver = "escpos";
    assertFalse(registry.get(p.driver).canPrint(p));
    p.compatibilityConfirmed = true;
    assertTrue(registry.get(p.driver).canPrint(p));
    assertTrue(registry.get(p.driver).capabilities().cut);
    assertFalse(registry.get(p.driver).capabilities().thai);
    assertFalse(registry.get(p.driver).capabilities().statusQuery);
    p.type = "BLE";
    assertFalse(registry.get(p.driver).canPrint(p));
  }

  @Test
  public void testDocketUsesExplicitTimezoneAndNoCommands() {
    PrinterProfile p = profile();
    p.driver = "escpos";
    p.compatibilityConfirmed = true;
    p.name = "Kitchen\u001b\u0e01";
    String text =
        new String(
            new EscPosPrinterDriver().testDocket(p, Instant.parse("2026-01-01T00:00:00Z")),
            java.nio.charset.StandardCharsets.US_ASCII);
    assertTrue(text.contains("NAKORN THAI"));
    assertTrue(text.contains("2026-01-01 11:00:00"));
    assertTrue(text.contains("End of test"));
    assertFalse(text.contains("\u001b"));
    for (String line : text.split("\n")) assertTrue(line.length() <= 32);
    p.paperWidth = 80;
    assertTrue(TestDocketFormatter.format(p, Instant.now()).contains("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
  }

  @Test
  public void kitchenDocketIncludesOrderSnapshotAndExcludesCustomerAndPaymentData() {
    PrinterProfile p = profile(); p.paperWidth = 80;
    p.driver = "escpos"; p.compatibilityConfirmed = true;
    Order order = new com.google.gson.Gson().fromJson(
        "{\"reference\":\"NT-42\",\"createdAt\":\"2026-01-01T00:00:00Z\","
            + "\"estimatedReadyAt\":\"2026-01-01T00:30:00Z\",\"customerName\":\"Private Name\","
            + "\"phone\":\"0412345678\",\"paymentMethod\":\"PAYPAL\",\"notes\":\"No nuts\","
            + "\"items\":[{\"dishName\":\"Green curry\",\"variationName\":\"Large\",\"quantity\":2,"
            + "\"selectedOptions\":[{\"optionGroupName\":\"Protein\",\"optionName\":\"Tofu\",\"quantity\":1}]}]}",
        Order.class);
    byte[] output = OrderDocketFormatter.format(p, order, "20");
    String docket = new String(Arrays.copyOf(output, output.length - 4),
        java.nio.charset.StandardCharsets.US_ASCII);
    assertTrue(docket.contains("NT-42"));
    assertTrue(docket.contains("01 Jan 2026 11:00"));
    assertTrue(docket.contains("Pickup estimate: 20 min"));
    assertTrue(docket.contains("2 x Green curry / Large"));
    assertTrue(docket.contains("1 x Tofu (Protein)"));
    assertTrue(docket.contains("No nuts"));
    assertFalse(docket.contains("Private Name"));
    assertFalse(docket.contains("0412345678"));
    assertFalse(docket.contains("PAYPAL"));
    assertArrayEquals(docket.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        Arrays.copyOf(output, output.length - 4));
    assertArrayEquals(new byte[] {0x1d, 0x56, 0x42, 0x00},
        Arrays.copyOfRange(output, output.length - 4, output.length));
  }

  @Test
  public void docketFormattingRejectsUnconfirmedEscPosProfileBeforeAddingCutBytes() {
    PrinterProfile p = profile();
    p.driver = "escpos";
    Order order = OrderWorkflowTest.order("a", "NEW", 1);
    assertThrows(IllegalArgumentException.class, () -> OrderDocketFormatter.format(p, order, "20"));
    p.compatibilityConfirmed = true;
    byte[] output = OrderDocketFormatter.format(p, order, "20");
    assertArrayEquals(new byte[] {0x1d, 0x56, 0x42, 0x00},
        Arrays.copyOfRange(output, output.length - 4, output.length));
  }

  @Test
  public void automaticDocketRequiresOneEnabledKitchenLanProfileAndDispatchesOnce() {
    Memory store = new Memory();
    PrinterConfigurationRepository repository = new PrinterConfigurationRepository(store);
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    try {
      List<String> outcomes = new ArrayList<>();
      java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
      java.util.concurrent.atomic.AtomicInteger opens = new java.util.concurrent.atomic.AtomicInteger();
      java.util.concurrent.atomic.AtomicReference<byte[]> payload = new java.util.concurrent.atomic.AtomicReference<>();
      KitchenDocketService service = new KitchenDocketService(repository,
          p -> {
            opens.incrementAndGet();
            return new PrinterConnection() {
            public void send(byte[] bytes) { writes.incrementAndGet(); payload.set(bytes.clone()); }
            public void close() {}
            };
          }, Runnable::run, timer);
      assertTrue(service.print(OrderWorkflowTest.order("a", "NEW", 1), "20", outcomes::add));
      assertTrue(outcomes.get(0).contains("No enabled Kitchen printer"));
      assertEquals(0, writes.get());

      PrinterProfile kitchen = profile(); kitchen.driver = "escpos";
      kitchen.compatibilityConfirmed = true;
      repository.save(List.of(kitchen)); outcomes.clear();
      Order invalidDocketOrder = OrderWorkflowTest.order("a", "NEW", 1);
      invalidDocketOrder.items = null;
      assertTrue(service.print(invalidDocketOrder, "20", outcomes::add));
      assertTrue(outcomes.get(0).contains("failed"));
      assertEquals(0, opens.get());
      outcomes.clear();
      assertTrue(service.print(OrderWorkflowTest.order("a", "NEW", 1), "20", outcomes::add));
      assertEquals(1, writes.get());
      assertEquals(1, opens.get());
      assertArrayEquals(new byte[] {0x1d, 0x56, 0x42, 0x00},
          Arrays.copyOfRange(payload.get(), payload.get().length - 4, payload.get().length));
      assertTrue(outcomes.get(0).contains("not device-confirmed"));
      kitchen.type = "CLASSIC"; kitchen.endpoint = "00:11:22:33:44:55";
      repository.save(List.of(kitchen)); outcomes.clear();
      assertTrue(service.print(OrderWorkflowTest.order("a", "NEW", 1), "20", outcomes::add));
      assertTrue(outcomes.get(0).contains("LAN/TCP"));
      assertEquals(1, writes.get());
    } finally { timer.shutdownNow(); }
  }

  @Test
  public void uncertainKitchenSendIsReportedWithoutRetry() {
    Memory store = new Memory();
    PrinterConfigurationRepository repository = new PrinterConfigurationRepository(store);
    PrinterProfile kitchen = profile(); kitchen.driver = "escpos"; kitchen.compatibilityConfirmed = true;
    repository.save(List.of(kitchen));
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    try {
      java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
      List<String> outcomes = new ArrayList<>();
      KitchenDocketService service = new KitchenDocketService(repository, p -> new PrinterConnection() {
        public void send(byte[] bytes) throws IOException { writes.incrementAndGet(); throw new IOException("socket disconnected"); }
        public void close() {}
      }, Runnable::run, timer);
      assertTrue(service.print(OrderWorkflowTest.order("a", "NEW", 1), "20", outcomes::add));
      assertEquals(1, writes.get());
      assertTrue(outcomes.get(0).contains("outcome uncertain"));
      assertTrue(outcomes.get(0).contains("no automatic retry"));
    } finally { timer.shutdownNow(); }
  }

  @Test
  public void adminGuardRejectsMissingDisabledAndOtherRoles() {
    Session s = new Session();
    s.accessToken = "fake";
    s.user = new Session.User();
    s.user.id = "id";
    s.user.username = "user";
    s.user.enabled = true;
    assertFalse(PrinterManagementViewModel.isAdmin(null));
    for (String role : new String[] {"FOH", "BOH"}) {
      s.user.role = role;
      assertFalse(PrinterManagementViewModel.isAdmin(s));
    }
    s.user.role = "ADMIN";
    assertTrue(PrinterManagementViewModel.isAdmin(s));
    s.user.enabled = false;
    assertFalse(PrinterManagementViewModel.isAdmin(s));
  }

  @Test
  public void duplicateRequestsAreGatedAndUncertainWritesNeverRetry() throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    CountDownLatch entered = new CountDownLatch(1),
        release = new CountDownLatch(1),
        done = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger writes =
        new java.util.concurrent.atomic.AtomicInteger();
    List<String> outcomes = new CopyOnWriteArrayList<>();
    PrinterConnectionManager fake =
        p ->
            new PrinterConnection() {
              public void send(byte[] bytes) throws IOException {
                writes.incrementAndGet();
                entered.countDown();
                try {
                  release.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                throw new IOException("lost response");
              }

              public void close() {}
            };
    PrinterProfile p = profile();
    p.driver = "escpos";
    p.compatibilityConfirmed = true;
    PrinterDiagnostics d = new PrinterDiagnostics(fake, worker, timer);
    try {
      assertTrue(
          d.run(
              p,
              true,
              () -> true,
              outcomes::add,
              s -> {
                outcomes.add(s);
                done.countDown();
              }));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertFalse(d.run(p, true, () -> true, outcomes::add, outcomes::add));
      release.countDown();
      assertTrue(done.await(2, TimeUnit.SECONDS));
      assertEquals(1, writes.get());
      assertTrue(outcomes.get(0).contains("uncertain"));
      assertTrue(outcomes.get(1).contains("Uncertain"));
    } finally {
      release.countDown();
      worker.shutdownNow();
      timer.shutdownNow();
    }
  }

  @Test
  public void diagnosticDistinguishesTransportFromPhysicalOutput() {
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    List<String> results = new ArrayList<>();
    PrinterDiagnostics d =
        new PrinterDiagnostics(
            p ->
                new PrinterConnection() {
                  public void send(byte[] b) {}

                  public void close() {}
                },
            Runnable::run,
            timer);
    try {
      assertTrue(d.run(profile(), false, () -> true, s -> {}, results::add));
      assertTrue(results.get(0).contains("not verified"));
      PrinterProfile p = profile();
      p.driver = "escpos";
      p.compatibilityConfirmed = true;
      d.run(p, true, () -> true, s -> {}, results::add);
      assertTrue(results.get(1).startsWith("Sent to printer — physical output not confirmed"));
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  public void timeoutAndPermissionFailuresAreExplicit() {
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    try {
      for (Exception e :
          new Exception[] {new java.net.SocketTimeoutException(), new SecurityException()}) {
        List<String> results = new ArrayList<>();
        PrinterDiagnostics d =
            new PrinterDiagnostics(
                p -> {
                  if (e instanceof IOException) throw (IOException) e;
                  throw (SecurityException) e;
                },
                Runnable::run,
                timer);
        d.run(profile(), false, () -> true, s -> {}, results::add);
        assertTrue(
            results.get(0).contains(e instanceof IOException ? "timed out" : "permission denied"));
      }
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  public void sessionChangePreventsSending() {
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    try {
      List<String> results = new ArrayList<>();
      PrinterDiagnostics d =
          new PrinterDiagnostics(
              p -> {
                throw new AssertionError("Must not open without admin");
              },
              Runnable::run,
              timer);
      d.run(profile(), false, () -> false, s -> {}, results::add);
      assertTrue(results.get(0).contains("ADMIN"));
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  public void bluetoothPermissionDeniedStopsBeforeAdapterAccess() {
    android.content.Context c = org.mockito.Mockito.mock(android.content.Context.class);
    org.mockito.Mockito.when(c.getApplicationContext()).thenReturn(c);
    org.mockito.Mockito.when(
            c.checkPermission(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(-1);
    org.mockito.Mockito.when(c.checkSelfPermission(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(-1);
    java.util.List<String> results = new ArrayList<>();
    new au.com.nakornthai.staffapp.printing.discovery.BluetoothPrinterDiscovery(c, false)
        .start(
            d -> {
              throw new AssertionError();
            },
            results::add);
    assertTrue(results.get(0).contains("permission denied"));
    org.mockito.Mockito.verify(c, org.mockito.Mockito.never())
        .getSystemService(android.bluetooth.BluetoothManager.class);
  }

  @Test
  public void networkDiscoveryCancellationStopsEveryRegisteredService() {
    android.content.Context c = org.mockito.Mockito.mock(android.content.Context.class);
    android.net.nsd.NsdManager manager = org.mockito.Mockito.mock(android.net.nsd.NsdManager.class);
    org.mockito.Mockito.when(c.getSystemService(android.net.nsd.NsdManager.class))
        .thenReturn(manager);
    au.com.nakornthai.staffapp.printing.discovery.NetworkPrinterDiscovery d =
        new au.com.nakornthai.staffapp.printing.discovery.NetworkPrinterDiscovery(c);
    d.start(x -> {}, s -> {});
    d.stop();
    d.stop();
    org.mockito.Mockito.verify(manager, org.mockito.Mockito.times(4))
        .stopServiceDiscovery(org.mockito.ArgumentMatchers.any());
  }

  @Test
  public void blockedWriteIsClosedByDeadlineAndReportedUncertain() throws Exception {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    CountDownLatch closed = new CountDownLatch(1), done = new CountDownLatch(1);
    List<String> results = new CopyOnWriteArrayList<>();
    PrinterDiagnostics d =
        new PrinterDiagnostics(
            p ->
                new PrinterConnection() {
                  public void send(byte[] b) throws IOException {
                    try {
                      if (!closed.await(2, TimeUnit.SECONDS))
                        throw new AssertionError("Missing deadline");
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    throw new IOException("closed");
                  }

                  public void close() {
                    closed.countDown();
                  }
                },
            worker,
            timer,
            20);
    PrinterProfile p = profile();
    p.driver = "escpos";
    p.compatibilityConfirmed = true;
    try {
      d.run(
          p,
          true,
          () -> true,
          s -> {},
          s -> {
            results.add(s);
            done.countDown();
          });
      assertTrue(done.await(3, TimeUnit.SECONDS));
      assertTrue(results.get(0).startsWith("Uncertain"));
    } finally {
      worker.shutdownNow();
      timer.shutdownNow();
    }
  }

  @Test
  public void failedJournalWritePreventsTransmission() {
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    java.util.concurrent.atomic.AtomicInteger
        writes = new java.util.concurrent.atomic.AtomicInteger(),
        closed = new java.util.concurrent.atomic.AtomicInteger();
    List<String> results = new ArrayList<>();
    PrinterDiagnostics d =
        new PrinterDiagnostics(
            p ->
                new PrinterConnection() {
                  public void send(byte[] b) {
                    writes.incrementAndGet();
                  }

                  public void close() {
                    closed.incrementAndGet();
                  }
                },
            Runnable::run,
            timer);
    PrinterProfile p = profile();
    p.driver = "escpos";
    p.compatibilityConfirmed = true;
    try {
      d.run(
          p,
          true,
          () -> true,
          s -> {
            throw new IllegalStateException("Storage full");
          },
          results::add);
      assertEquals(0, writes.get());
      assertEquals(1, closed.get());
      assertEquals("Storage full", results.get(0));
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  public void missingPersistedIdentityAndDuplicateIdsAreRejected() {
    Memory m = new Memory();
    m.value = "{\"schemaVersion\":1,\"profiles\":[{}]}";
    PrinterConfigurationRepository repository = new PrinterConfigurationRepository(m);
    assertTrue(repository.load().isEmpty());
    PrinterProfile p = profile();
    assertThrows(IllegalArgumentException.class, () -> repository.save(Arrays.asList(p, p.copy())));
  }

  @Test
  public void bluetoothConnectDeadlineClosesSocketAndReportsTimeout() throws Exception {
    android.bluetooth.BluetoothAdapter adapter =
        org.mockito.Mockito.mock(android.bluetooth.BluetoothAdapter.class);
    android.bluetooth.BluetoothDevice device =
        org.mockito.Mockito.mock(android.bluetooth.BluetoothDevice.class);
    android.bluetooth.BluetoothSocket socket =
        org.mockito.Mockito.mock(android.bluetooth.BluetoothSocket.class);
    ScheduledExecutorService timer = org.mockito.Mockito.mock(ScheduledExecutorService.class);
    org.mockito.Mockito.when(adapter.isEnabled()).thenReturn(true);
    org.mockito.Mockito.when(adapter.getRemoteDevice(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(device);
    org.mockito.Mockito.when(device.getBondState())
        .thenReturn(android.bluetooth.BluetoothDevice.BOND_BONDED);
    org.mockito.Mockito.when(
            device.createRfcommSocketToServiceRecord(org.mockito.ArgumentMatchers.any()))
        .thenReturn(socket);
    org.mockito.Mockito.when(
            timer.schedule(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return org.mockito.Mockito.mock(ScheduledFuture.class);
            });
    org.mockito.Mockito.doThrow(new IOException("socket closed")).when(socket).connect();
    assertThrows(
        java.net.SocketTimeoutException.class,
        () -> new BluetoothPrinterConnection(adapter, "00:11:22:33:44:55", timer));
    org.mockito.Mockito.verify(socket, org.mockito.Mockito.atLeastOnce()).close();
  }
}
