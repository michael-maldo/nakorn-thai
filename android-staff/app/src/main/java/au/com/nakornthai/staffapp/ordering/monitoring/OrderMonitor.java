package au.com.nakornthai.staffapp.ordering.monitoring;

import au.com.nakornthai.staffapp.identity.data.IdentityRepository;
import au.com.nakornthai.staffapp.ordering.data.OrderRepository;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.shared.network.ApiFailure;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** A single scheduler owns all queue reads and mutations. No Fragment owns a polling timer. */
public final class OrderMonitor {
  private final ScheduledExecutorService executor;
  private final OrderRepository repository;
  private final IdentityRepository identity;
  private final NewOrderTracker alerts = new NewOrderTracker();
  private Consumer<BoardState> listener = state -> {};
  private List<Order> orders = Collections.emptyList();
  private ScheduledFuture<?> scheduled;
  private boolean foreground, loading, busy, blocked, refreshAgain;
  private long lastSync, alertSequence, epoch, principal = -1;
  private int failures;
  private String error, notice;

  public OrderMonitor(
      ScheduledExecutorService executor, OrderRepository repository, IdentityRepository identity) {
    this.executor = executor;
    this.repository = repository;
    this.identity = identity;
  }

  public synchronized void setListener(Consumer<BoardState> listener) {
    this.listener = listener;
    emit();
  }

  public synchronized BoardState snapshot() {
    return new BoardState(
        orders,
        loading,
        busy,
        foreground,
        blocked,
        lastSync,
        alertSequence,
        alerts.count(),
        error,
        notice);
  }

  private void emit() {
    listener.accept(snapshot());
  }

  public synchronized void setForeground(boolean value) {
    boolean returning = value && !foreground;
    foreground = value;
    if (returning) blocked = true;
    if (!value) {
      if (scheduled != null) scheduled.cancel(false);
      scheduled = null;
      emit();
      return;
    }
    refresh();
  }

  public synchronized void sessionChanged() {
    boolean changed = identity.generation() != principal;
    if (changed) {
      principal = identity.generation();
      epoch++;
      orders = Collections.emptyList();
      lastSync = 0;
      alerts.reset();
      failures = 0;
      error = null;
      notice = null;
      blocked = false;
      busy = false;
      if (scheduled != null) scheduled.cancel(false);
      scheduled = null;
    }
    if (identity.current() != null && foreground && changed) refresh();
    else emit();
  }

  public synchronized void refresh() {
    if (!foreground || identity.current() == null) {
      emit();
      return;
    }
    if (loading || busy) {
      refreshAgain = true;
      return;
    }
    schedule(0);
  }

  private void schedule(long seconds) {
    if (scheduled != null) scheduled.cancel(false);
    scheduled = executor.schedule(this::poll, seconds, TimeUnit.SECONDS);
  }

  private void poll() {
    final long ticket;
    synchronized (this) {
      scheduled = null;
      if (!foreground || identity.current() == null || loading || busy) return;
      loading = true;
      ticket = epoch;
      emit();
    }
    List<Order> next = null;
    IOException failure = null;
    try {
      next = repository.load();
    } catch (IOException e) {
      failure = e;
    } catch (RuntimeException e) {
      failure = new IOException("Unable to read orders safely. Refresh or sign in again.");
    }
    synchronized (this) {
      loading = false;
      if (ticket != epoch) {
        if (foreground && identity.current() != null) schedule(0);
        return;
      }
      if (failure == null) {
        orders = next;
        lastSync = System.currentTimeMillis();
        error = null;
        failures = 0;
        blocked = false;
        if (alerts.accept(next) && foreground) alertSequence++;
      } else {
        error = failure.getMessage();
        failures = Math.min(3, failures + 1);
      }
      emit();
      if (foreground && identity.current() != null) {
        long delay = refreshAgain ? 0 : Math.min(60, 15L * (1L << Math.max(0, failures - 1)));
        refreshAgain = false;
        schedule(delay);
      }
    }
  }

  public boolean mutate(OrderDraft draft, Consumer<Boolean> completion) {
    final long ticket, owner;
    synchronized (this) {
      au.com.nakornthai.staffapp.identity.data.Session session = identity.current();
      if (session == null) return false;
      Order current = snapshot().find(draft.orderId);
      if (!snapshot().canWrite() || draft.conflict(current)) return false;
      if (!"PAYPAL".equals(draft.operation) && draft.validate() != null) return false;
      if (!Arrays.asList("PAYID", "PAYPAL").contains(draft.operation)) {
        if (!current.actions(session.user.role).contains(draft.operation)) return false;
        if (current.needsPayment()
            && Arrays.asList("ACCEPTED", "COMPLETED").contains(draft.operation)) return false;
      }
      busy = true;
      blocked = true;
      notice = null;
      ticket = epoch;
      owner = principal;
      if (scheduled != null) scheduled.cancel(false);
      scheduled = null;
      emit();
    }
    executor.execute(
        () -> {
          boolean success = false;
          String message;
          try {
            identity.assertOwner(owner);
            repository.mutate(draft, owner);
            success = true;
            message =
                "PAYPAL".equals(draft.operation)
                    ? "Payment check finished. Reloaded payment state remains authoritative."
                    : "Update saved. Reloading the server state.";
          } catch (IOException failure) {
            draft.invalidated = true;
            message =
                failure instanceof ApiFailure && ((ApiFailure) failure).status == 409
                    ? "Order changed on another terminal. Draft retained; discard it after"
                        + " reloading."
                    : failure.getMessage();
          } catch (RuntimeException failure) {
            draft.invalidated = true;
            message = "Unable to complete the action safely. Reconcile before retrying.";
          }
          synchronized (this) {
            if (ticket != epoch) {
              completion.accept(false);
              return;
            }
            busy = false;
            notice = message; // No further writes until a successful reconciliation.
            completion.accept(success);
            if (foreground && identity.current() != null) schedule(0);
            emit();
          }
        });
    return true;
  }

  public synchronized void acknowledge() {
    alerts.acknowledge();
    emit();
  }
}
