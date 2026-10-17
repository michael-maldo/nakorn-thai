package au.com.nakornthai.staffapp.ordering.ui;

import androidx.lifecycle.*;
import au.com.nakornthai.staffapp.app.AppGraph;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.ordering.monitoring.BoardState;
import java.util.*;

public final class OrdersViewModel extends ViewModel {
  private final MutableLiveData<BoardState> mutableState = new MutableLiveData<>();
  public final LiveData<BoardState> state = mutableState;
  private final au.com.nakornthai.staffapp.identity.data.IdentityRepository identity;
  private final au.com.nakornthai.staffapp.ordering.monitoring.OrderMonitor monitor;
  private final Map<String, OrderDraft> drafts = new java.util.concurrent.ConcurrentHashMap<>();
  private long owner = -1;

  public OrdersViewModel(AppGraph graph) {
    this(graph.monitor, graph.identity);
  }

  public OrdersViewModel(
      au.com.nakornthai.staffapp.ordering.monitoring.OrderMonitor monitor,
      au.com.nakornthai.staffapp.identity.data.IdentityRepository identity) {
    this.monitor = monitor;
    this.identity = identity;
    monitor.setListener(
        value -> {
          if (owner != identity.generation()) {
            drafts.clear();
            owner = identity.generation();
          }
          mutableState.postValue(value);
        });
  }

  public String role() {
    au.com.nakornthai.staffapp.identity.data.Session current = identity.current();
    return current == null ? "" : current.user.role;
  }

  public Order order(String id) {
    return monitor.snapshot().find(id);
  }

  private String key(String id, String operation) {
    return id
        + ":"
        + (("PAYID".equals(operation) || "PAYPAL".equals(operation)) ? "payment" : "status");
  }

  public OrderDraft draft(String id, String operation) {
    String key = key(id, operation);
    OrderDraft existing = drafts.get(key);
    if (existing != null) return existing;
    Order order = order(id);
    if (order == null) return null;
    OrderDraft draft = new OrderDraft(order, operation);
    drafts.put(key, draft);
    return draft;
  }

  public void discard(OrderDraft draft) {
    drafts.remove(key(draft.orderId, draft.operation), draft);
    mutableState.postValue(monitor.snapshot());
  }

  public OrderDraft existingDraft(String id, boolean payment) {
    return drafts.get(id + (payment ? ":payment" : ":status"));
  }

  public List<OrderDraft> orphanDrafts() {
    BoardState current = monitor.snapshot();
    List<OrderDraft> result = new ArrayList<>();
    for (OrderDraft draft : drafts.values())
      if (current.find(draft.orderId) == null) result.add(draft);
    return result;
  }

  public boolean submit(OrderDraft draft) {
    return monitor.mutate(
        draft,
        success -> {
          if (success) discard(draft);
        });
  }

  public void refresh() {
    monitor.refresh();
  }

  public void acknowledge() {
    monitor.acknowledge();
  }

  public boolean writable() {
    return monitor.snapshot().canWrite();
  }
}
