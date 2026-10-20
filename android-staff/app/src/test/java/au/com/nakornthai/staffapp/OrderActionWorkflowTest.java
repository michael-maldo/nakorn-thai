package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.ordering.data.OrderRepository;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.ordering.monitoring.*;
import au.com.nakornthai.staffapp.ordering.ui.*;
import au.com.nakornthai.staffapp.printing.KitchenDocketPrinter;
import au.com.nakornthai.staffapp.shared.network.ApiFailure;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;

public class OrderActionWorkflowTest {
  @Rule public InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();
  ScheduledExecutorService executor;
  IdentityRepository identity;
  OrderRepository repository;
  OrderMonitor monitor;
  OrdersViewModel vm;
  Session session;
  AtomicReference<Order> remote;
  java.util.concurrent.atomic.AtomicInteger printCalls;
  List<java.util.function.Consumer<String>> printResults;

  @Before public void setup() throws Exception {
    executor = Executors.newSingleThreadScheduledExecutor();
    identity = mock(IdentityRepository.class);
    session = new Session(); session.user = new Session.User(); session.user.role = "ADMIN";
    when(identity.current()).thenReturn(session); when(identity.generation()).thenReturn(1L);
    repository = mock(OrderRepository.class);
    remote = new AtomicReference<>(OrderWorkflowTest.order("a", "NEW", 7));
    when(repository.load()).thenAnswer(call -> List.of(remote.get()));
    monitor = new OrderMonitor(executor, repository, identity);
    printCalls = new java.util.concurrent.atomic.AtomicInteger();
    printResults = new CopyOnWriteArrayList<>();
    KitchenDocketPrinter printer = (order, pickup, result) -> {
      printCalls.incrementAndGet(); printResults.add(result); return true;
    };
    vm = new OrdersViewModel(monitor, identity, printer);
    monitor.sessionChanged(); monitor.setForeground(true);
    await(() -> monitor.snapshot().canWrite());
  }

  @After public void stop() { monitor.setForeground(false); executor.shutdownNow(); }
  void await(java.util.function.BooleanSupplier condition) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
    while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
    assertTrue(condition.getAsBoolean());
  }
  OrderAction open(String operation) { return vm.action("a", operation, UUID.randomUUID().toString()); }
  void reload(Order order) throws Exception {
    remote.set(order); monitor.refresh();
    await(() -> monitor.snapshot().canWrite() && monitor.snapshot().find("a") == order);
  }

  @Test public void ordinaryCancelIsOnlyDismissalAndNeverRetainsDraftOrSendsCancellation() throws Exception {
    for (String operation : List.of("ACCEPTED", "PREPARING", "READY", "COMPLETED", "PAYPAL")) {
      OrderAction action = open(operation);
      action.draft.pickupMinutes = "50"; action.draft.paymentCollected = true;
      assertTrue(vm.close(action)); assertFalse(vm.submit(action));
      OrderAction next = open(operation);
      assertNotSame(action.draft, next.draft);
      assertEquals("20", next.draft.pickupMinutes); assertFalse(next.draft.paymentCollected);
      assertNull(next.feedback); assertNull(vm.existingDraft("a", false));
      vm.close(next);
    }
    assertEquals("NEW", vm.order("a").status);
    verify(repository, never()).mutate(any(), anyLong());
  }

  @Test public void confirmSubmitsOnceCannotBeDismissedInFlightAndRefreshesServerSnapshot() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    doAnswer(call -> { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS));
      remote.set(OrderWorkflowTest.order("a", "ACCEPTED", 8)); return null;
    }).when(repository).mutate(any(), eq(1L));
    OrderAction action = open("ACCEPTED");
    try {
      assertTrue(vm.submit(action)); assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertFalse(vm.submit(action)); assertFalse(vm.close(action)); assertTrue(action.pending);
      // Configuration recreation reattaches to the same in-flight operation.
      assertSame(action, vm.action("a", "ACCEPTED", action.token));
      release.countDown();
      await(() -> action.finished && monitor.snapshot().canWrite() && vm.order("a").version == 8);
      assertTrue(action.succeeded); assertNull(action.feedback); assertNull(monitor.snapshot().notice);
      assertFalse(vm.submit(action)); verify(repository, times(1)).mutate(action.draft, 1L);
      assertNull(open("PREPARING").feedback);
    } finally { release.countDown(); }
  }

  @Test public void acceptingPrintsOnceOnlyAfterSuccessAndPrintFailureDoesNotUndoAcceptance() throws Exception {
    Order accepted = OrderWorkflowTest.order("a", "ACCEPTED", 8);
    doAnswer(call -> { remote.set(accepted); return null; }).when(repository).mutate(any(), anyLong());
    OrderAction action = open("ACCEPTED");
    assertTrue(vm.submit(action));
    await(() -> action.finished && monitor.snapshot().canWrite()
        && "ACCEPTED".equals(vm.order("a").status));
    assertTrue(action.succeeded);
    assertEquals(1, printCalls.get());
    assertEquals("ACCEPTED", vm.order("a").status);
    printResults.get(0).accept("Kitchen docket failed. Order acceptance remains saved.");
    printResults.get(0).accept("late duplicate callback");
    await(() -> vm.printingStatus.getValue() != null
        && vm.printingStatus.getValue().contains("late duplicate"));
    assertEquals("ACCEPTED", vm.order("a").status);
    assertTrue(action.succeeded);
    assertEquals(1, printCalls.get());
    verify(repository, times(1)).mutate(action.draft, 1L);
  }

  @Test public void failedAcceptanceDoesNotPrintAndReprintRequiresExplicitStaffCall() throws Exception {
    doThrow(new ApiFailure(409, "Version conflict")).when(repository).mutate(any(), anyLong());
    OrderAction failed = open("ACCEPTED");
    assertTrue(vm.submit(failed));
    await(() -> failed.finished && monitor.snapshot().canWrite());
    assertFalse(failed.succeeded);
    assertEquals(0, printCalls.get());

    remote.set(OrderWorkflowTest.order("a", "ACCEPTED", 8)); monitor.refresh();
    await(() -> monitor.snapshot().canWrite() && "ACCEPTED".equals(vm.order("a").status));
    assertEquals(0, printCalls.get());
    assertTrue(vm.reprint("a"));
    assertEquals(1, printCalls.get());
    assertFalse(vm.reprint("missing"));
  }

  @Test public void conflictKeepsCurrentOperationErrorButNewDialogDoesNotInheritIt() throws Exception {
    doThrow(new ApiFailure(409, "Version conflict")).when(repository).mutate(any(), eq(1L));
    OrderAction action = open("ACCEPTED"); assertTrue(vm.submit(action));
    await(() -> action.finished && monitor.snapshot().canWrite());
    assertFalse(action.succeeded); assertTrue(action.draft.invalidated);
    assertTrue(action.feedback.contains("Order changed")); assertFalse(action.feedback.contains("Draft retained"));
    assertEquals("NEW", vm.order("a").status); assertEquals(7, vm.order("a").version);
    assertFalse(vm.submit(action)); assertTrue(vm.close(action));
    assertNotNull(monitor.snapshot().notice); // Still visible on the board, not copied into a new dialog.
    OrderAction next = open("ACCEPTED"); assertNull(next.feedback); assertFalse(next.draft.invalidated);
    verify(repository, times(1)).mutate(any(), anyLong());
  }

  @Test public void lateFailureCannotPopulateAnotherDialogsFeedback() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    doAnswer(call -> { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS));
      throw new ApiFailure(0, "Response lost; check the order", true);
    }).when(repository).mutate(any(), anyLong());
    OrderAction first = open("ACCEPTED");
    try {
      assertTrue(vm.submit(first)); assertTrue(entered.await(2, TimeUnit.SECONDS));
      OrderAction next = open("ACCEPTED"); assertFalse(vm.submit(next));
      release.countDown(); await(() -> first.finished && monitor.snapshot().canWrite());
      assertEquals("Response lost; check the order", first.feedback);
      assertNull(next.feedback); assertFalse(next.finished); assertFalse(next.pending);
      verify(repository, times(1)).mutate(any(), anyLong());
    } finally { release.countDown(); }
  }

  @Test public void retainedReasonAndBankReferenceSurviveCloseWithoutRetainingFeedback() {
    for (String operation : List.of("CANCELLED", "PAYID")) {
      OrderAction action = open(operation); action.draft.reason = "Sold out";
      action.draft.bankReference = "BANK"; action.feedback = "Old validation error";
      assertTrue(vm.close(action));
      OrderAction next = open(operation);
      assertSame(action.draft, next.draft); assertEquals("Sold out", next.draft.reason);
      assertEquals("BANK", next.draft.bankReference); assertNull(next.feedback);
      assertEquals(7, next.draft.version); vm.close(next);
    }
  }

  @Test public void keepOrderDoesNotCancelAndExplicitCancellationRequiresReason() throws Exception {
    OrderAction action = open("CANCELLED"); assertNotNull(action.draft.validate());
    assertFalse(vm.submit(action)); assertTrue(vm.close(action));
    assertEquals("NEW", vm.order("a").status); verify(repository, never()).mutate(any(), anyLong());
    OrderAction confirmed = open("CANCELLED"); confirmed.draft.reason = "Sold out";
    assertEquals("CANCELLED", confirmed.draft.command().get("status"));
    assertEquals("Sold out", confirmed.draft.command().get("reason"));
    doAnswer(call -> { remote.set(OrderWorkflowTest.order("a", "CANCELLED", 8)); return null; })
        .when(repository).mutate(any(), anyLong());
    assertTrue(vm.submit(confirmed)); await(() -> confirmed.finished && "CANCELLED".equals(vm.order("a").status));
    verify(repository, times(1)).mutate(confirmed.draft, 1L);
    assertEquals("CANCELLED", vm.confirmedOperation("a"));
    assertNull(vm.existingDraft("a", false));
  }

  @Test public void unauthorizedCancellationStaleVersionsAndSessionChangesStayBlocked() throws Exception {
    session.user.role = "BOH";
    OrderAction cancel = open("CANCELLED"); cancel.draft.reason = "Sold out";
    assertFalse(vm.canSubmit(cancel, monitor.snapshot())); assertFalse(vm.submit(cancel));
    session.user.role = "ADMIN";
    OrderAction accept = open("ACCEPTED"); await(() -> vm.state.getValue() != null);
    reload(OrderWorkflowTest.order("a", "NEW", 8)); assertFalse(vm.submit(accept));
    when(identity.generation()).thenReturn(2L); monitor.sessionChanged();
    assertFalse(vm.submit(cancel)); verify(repository, never()).mutate(any(), anyLong());
  }

  @Test public void unpaidPaypalAndFinancialHoldsCannotBeAccepted() throws Exception {
    Order unpaid = new Order("a", "a", "NEW", 8, "PAYPAL", null, List.of()); reload(unpaid);
    assertFalse(vm.submit(open("ACCEPTED")));
    Order held = new Order("a", "a", "NEW", 9, "PAYPAL", "verified", List.of()); held.paymentReviewRequired = true;
    reload(held); assertFalse(vm.submit(open("ACCEPTED")));
    verify(repository, never()).mutate(any(), anyLong());
  }

  @Test public void cashHandoverAcknowledgementIsMandatoryAndReopeningResetsIt() throws Exception {
    reload(OrderWorkflowTest.order("a", "READY", 8));
    OrderAction action = open("COMPLETED"); assertFalse(vm.submit(action));
    action.draft.paymentCollected = true; assertTrue(vm.close(action));
    OrderAction fresh = open("COMPLETED"); assertFalse(fresh.draft.paymentCollected);
    assertNotNull(fresh.draft.validate()); fresh.draft.paymentCollected = true;
    assertEquals(true, fresh.draft.command().get("paymentCollected"));
    assertTrue(vm.submit(fresh)); await(() -> fresh.finished);
    verify(repository, times(1)).mutate(fresh.draft, 1L);
  }

  @Test public void paidPaypalCancellationDoesNotInventRefundAndShowsServerObligation() throws Exception {
    Order paid = new Order("a", "a", "READY", 8, "PAYPAL", "verified-capture", List.of()); paid.refundStatus = "NONE";
    reload(paid); OrderAction cancel = open("CANCELLED"); cancel.draft.reason = "Cannot fulfil";
    assertFalse(cancel.draft.command().containsKey("paidAt")); assertFalse(cancel.draft.command().containsKey("refundStatus"));
    doAnswer(call -> {
      Order cancelled = new Order("a", "a", "CANCELLED", 9, "PAYPAL", paid.paidAt, List.of()); cancelled.refundStatus = "REQUIRED";
      remote.set(cancelled); return null;
    }).when(repository).mutate(any(), anyLong());
    assertTrue(vm.submit(cancel)); await(() -> cancel.finished && vm.order("a").version == 9);
    assertEquals("verified-capture", vm.order("a").paidAt); assertEquals("REQUIRED", vm.order("a").refundStatus);
    assertTrue(OrderPresentation.payment(vm.order("a")).contains("Refund: REQUIRED"));
  }
}
