package au.com.nakornthai.staffapp.ordering.monitoring;

import au.com.nakornthai.staffapp.ordering.model.Order;
import java.util.*;

/**
 * First successful snapshot establishes a silent baseline. One instance survives Activity
 * recreation.
 */
public final class NewOrderTracker {
  private final Set<String> seen = new HashSet<>(), unacknowledged = new HashSet<>();
  private boolean initialized;

  public synchronized boolean accept(List<Order> orders) {
    Set<String> pending = new HashSet<>();
    boolean arrived = false;
    for (Order order : orders) {
      if ("NEW".equals(order.status)) {
        pending.add(order.id);
        if (initialized && !seen.contains(order.id)) {
          unacknowledged.add(order.id);
          arrived = true;
        }
      }
      seen.add(order.id);
    }
    unacknowledged.retainAll(pending);
    initialized = true;
    return arrived;
  }

  public synchronized int count() {
    return unacknowledged.size();
  }

  public synchronized void acknowledge() {
    unacknowledged.clear();
  }

  public synchronized void reset() {
    seen.clear();
    unacknowledged.clear();
    initialized = false;
  }
}
