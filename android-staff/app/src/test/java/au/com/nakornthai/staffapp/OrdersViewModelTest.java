package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.ordering.data.OrderRepository;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.ordering.monitoring.*;
import au.com.nakornthai.staffapp.ordering.ui.OrdersViewModel;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;

public class OrdersViewModelTest {
  @Rule public InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();

  @Test
  public void independentDraftsRetainOriginalVersionAcrossReloadAndClearOnSessionChange()
      throws Exception {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    IdentityRepository identity = mock(IdentityRepository.class);
    Session session = new Session();
    session.user = new Session.User();
    session.user.role = "ADMIN";
    when(identity.current()).thenReturn(session);
    when(identity.generation()).thenReturn(1L);
    OrderRepository repository = mock(OrderRepository.class);
    when(repository.load()).thenReturn(List.of(OrderWorkflowTest.order("a", "NEW", 7)));
    OrderMonitor monitor = new OrderMonitor(executor, repository, identity);
    OrdersViewModel vm = new OrdersViewModel(monitor, identity);
    try {
      monitor.sessionChanged();
      monitor.setForeground(true);
      await(() -> vm.state.getValue() != null && vm.state.getValue().lastSync > 0);
      OrderDraft status = vm.draft("a", "CANCELLED"), payment = vm.draft("a", "PAYID");
      status.reason = "Sold out";
      payment.bankReference = "BANK";
      assertSame(status, vm.draft("a", "CANCELLED"));
      assertNotSame(status, payment);
      when(repository.load()).thenReturn(List.of(OrderWorkflowTest.order("a", "ACCEPTED", 8)));
      monitor.refresh();
      await(() -> vm.state.getValue().orders.get(0).version == 8);
      assertSame(status, vm.draft("a", "CANCELLED"));
      assertTrue(status.conflict(vm.order("a")));
      assertEquals("BANK", payment.bankReference);
      vm.discard(status);
      assertEquals(8, vm.draft("a", "PREPARING").version);
      when(repository.load()).thenReturn(List.of());
      monitor.refresh();
      await(() -> vm.state.getValue().orders.isEmpty());
      assertEquals(1, vm.orphanDrafts().size());
      assertFalse(vm.submit(payment));
      vm.discard(payment);
      assertEquals(0, vm.orphanDrafts().size());
      when(identity.generation()).thenReturn(2L);
      when(identity.current()).thenReturn(null);
      monitor.sessionChanged();
      assertTrue(vm.state.getValue().orders.isEmpty());
      assertNull(vm.draft("a", "PAYID"));
    } finally {
      monitor.setForeground(false);
      executor.shutdownNow();
    }
  }

  private void await(java.util.function.BooleanSupplier ready) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!ready.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
    assertTrue(ready.getAsBoolean());
  }
}
