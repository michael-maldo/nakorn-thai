package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.ordering.data.*;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.ordering.monitoring.*;
import au.com.nakornthai.staffapp.shared.network.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;

public class OrderMonitorTest {
  ScheduledExecutorService executor;
  IdentityRepository identity;
  OrderRepository repository;
  OrderMonitor monitor;

  @Before
  public void setup() throws Exception {
    executor = Executors.newSingleThreadScheduledExecutor();
    identity = mock(IdentityRepository.class);
    Session session = new Session();
    session.user = new Session.User();
    session.user.role = "FOH";
    when(identity.current()).thenReturn(session);
    when(identity.generation()).thenReturn(1L);
    repository = mock(OrderRepository.class);
    when(repository.load()).thenReturn(List.of(OrderWorkflowTest.order("a", "NEW", 0)));
    monitor = new OrderMonitor(executor, repository, identity);
    monitor.sessionChanged();
  }

  @After
  public void stop() {
    monitor.setForeground(false);
    executor.shutdownNow();
  }

  void await(java.util.function.BooleanSupplier predicate) throws Exception {
    long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!predicate.getAsBoolean() && System.nanoTime() < limit) Thread.sleep(5);
    assertTrue(predicate.getAsBoolean());
  }

  @Test
  public void noOverlapsAndResumeRefreshes() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    when(repository.load())
        .thenAnswer(
            call -> {
              entered.countDown();
              release.await(2, TimeUnit.SECONDS);
              return List.of(OrderWorkflowTest.order("a", "NEW", 0));
            });
    monitor.setForeground(true);
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    monitor.setForeground(true);
    monitor.refresh();
    verify(repository, times(1)).load();
    release.countDown();
    await(() -> monitor.snapshot().lastSync > 0);
    monitor.setForeground(false);
    long before = monitor.snapshot().lastSync;
    Thread.sleep(10);
    monitor.setForeground(true);
    await(() -> monitor.snapshot().lastSync > before);
  }

  @Test
  public void timeoutLocksActionsUntilReconciliationAndNeverResubmits() throws Exception {
    monitor.setForeground(true);
    await(() -> monitor.snapshot().canWrite());
    OrderDraft draft = new OrderDraft(monitor.snapshot().orders.get(0), "ACCEPTED");
    doThrow(new ApiFailure(0, "Uncertain", true)).when(repository).mutate(any(), eq(1L));
    when(repository.load()).thenThrow(new IOException("offline"));
    assertTrue(monitor.mutate(draft, success -> assertFalse(success)));
    assertFalse(monitor.mutate(draft, success -> fail()));
    await(() -> monitor.snapshot().error != null);
    assertTrue(monitor.snapshot().blocked);
    assertTrue(draft.invalidated);
    verify(repository, times(1)).mutate(any(), eq(1L));
    doReturn(List.of(OrderWorkflowTest.order("a", "ACCEPTED", 1))).when(repository).load();
    monitor.refresh();
    await(() -> monitor.snapshot().canWrite());
    assertEquals("ACCEPTED", monitor.snapshot().orders.get(0).status);
  }

  @Test
  public void networkFailureRetainsLastSnapshotAndManualReconnectWorks() throws Exception {
    monitor.setForeground(true);
    await(() -> monitor.snapshot().lastSync > 0);
    when(repository.load()).thenThrow(new IOException("offline"));
    monitor.refresh();
    await(() -> monitor.snapshot().error != null);
    assertEquals(1, monitor.snapshot().orders.size());
    assertFalse(monitor.snapshot().canWrite());
    doReturn(List.of(OrderWorkflowTest.order("b", "NEW", 0))).when(repository).load();
    monitor.refresh();
    await(() -> monitor.snapshot().error == null && !monitor.snapshot().loading);
    assertEquals("b", monitor.snapshot().orders.get(0).id);
    assertEquals(1, monitor.snapshot().unacknowledged);
    monitor.acknowledge();
    assertEquals(0, monitor.snapshot().unacknowledged);
  }

  @Test
  public void paymentPrerequisiteBlocksAccept() throws Exception {
    when(repository.load())
        .thenReturn(List.of(new Order("a", "a", "NEW", 0, "PAYPAL", null, List.of())));
    monitor.setForeground(true);
    await(() -> monitor.snapshot().canWrite());
    assertFalse(
        monitor.mutate(new OrderDraft(monitor.snapshot().orders.get(0), "ACCEPTED"), s -> fail()));
    verify(repository, never()).mutate(any(), anyLong());
  }

  @Test
  public void backoffIsBoundedAndBackgroundCancelsScheduling() throws Exception {
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    java.util.List<Runnable> callbacks = new java.util.ArrayList<>();
    java.util.List<Long> delays = new java.util.ArrayList<>();
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doAnswer(
            call -> {
              callbacks.add(call.getArgument(0));
              delays.add(call.getArgument(1));
              return future;
            })
        .when(scheduler)
        .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS));
    doThrow(new IOException("offline")).when(repository).load();
    OrderMonitor local = new OrderMonitor(scheduler, repository, identity);
    local.sessionChanged();
    local.setForeground(true);
    for (int i = 0; i < 4; i++) callbacks.get(i).run();
    assertEquals(java.util.List.of(0L, 15L, 30L, 60L, 60L), delays);
    local.setForeground(false);
    verify(future, atLeastOnce()).cancel(false);
  }

  @Test
  public void sessionClearedBeforeListenerCannotDispatchOrCrash() throws Exception {
    monitor.setForeground(true);
    await(() -> monitor.snapshot().canWrite());
    OrderDraft draft = new OrderDraft(monitor.snapshot().orders.get(0), "ACCEPTED");
    when(identity.current()).thenReturn(null);
    assertFalse(monitor.mutate(draft, success -> fail()));
    verify(repository, never()).mutate(any(), anyLong());
  }
}
