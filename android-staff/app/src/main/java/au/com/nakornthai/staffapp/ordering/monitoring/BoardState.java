package au.com.nakornthai.staffapp.ordering.monitoring;

import au.com.nakornthai.staffapp.ordering.model.Order;
import java.util.*;

public final class BoardState {
  public final List<Order> orders;
  public final boolean loading, busy, foreground, blocked;
  public final long lastSync, alertSequence;
  public final int unacknowledged;
  public final String error, notice;

  public BoardState(
      List<Order> orders,
      boolean loading,
      boolean busy,
      boolean foreground,
      boolean blocked,
      long lastSync,
      long alertSequence,
      int unacknowledged,
      String error,
      String notice) {
    this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
    this.loading = loading;
    this.busy = busy;
    this.foreground = foreground;
    this.blocked = blocked;
    this.lastSync = lastSync;
    this.alertSequence = alertSequence;
    this.unacknowledged = unacknowledged;
    this.error = error;
    this.notice = notice;
  }

  public Order find(String id) {
    for (Order order : orders) if (order.id.equals(id)) return order;
    return null;
  }

  public List<Order> column(String status) {
    List<Order> result = new ArrayList<>();
    for (Order o : orders) if (status.equals(o.status)) result.add(o);
    return result;
  }

  public boolean canWrite() {
    return foreground && !loading && !busy && !blocked && lastSync > 0 && error == null;
  }
}
