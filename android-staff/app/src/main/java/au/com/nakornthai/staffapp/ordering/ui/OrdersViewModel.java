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
  private final Map<String, OrderAction> actions = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<String, String> confirmedOperations = new java.util.concurrent.ConcurrentHashMap<>();
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
            actions.clear();
            confirmedOperations.clear();
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
    Order order = order(id);
    if (order == null) return null;
    if (!OrderDraft.retainedOperation(operation)) return new OrderDraft(order, operation);
    String key = key(id, operation);
    OrderDraft existing = drafts.get(key);
    if (existing != null) return existing.operation.equals(operation) ? existing : null;
    OrderDraft draft = new OrderDraft(order, operation);
    drafts.put(key, draft);
    return draft;
  }

  public OrderAction action(String id, String operation, String token) {
    OrderAction existing = actions.get(token);
    if (existing != null) return existing;
    OrderDraft draft = draft(id, operation);
    if (draft == null) return null;
    OrderAction action = new OrderAction(token, draft, identity.generation());
    actions.put(token, action);
    return action;
  }

  public boolean canSubmit(OrderAction action, BoardState state) {
    if (action.owner != identity.generation() || action.closed || action.pending || action.finished
        || !state.canWrite() || action.draft.conflict(state.find(action.draft.orderId))) return false;
    Order order = state.find(action.draft.orderId);
    String operation = action.draft.operation;
    if ("PAYID".equals(operation) || "PAYPAL".equals(operation))
      return ("ADMIN".equals(role()) || "FOH".equals(role())) && operation.equals(order.paymentMethod);
    return order.actions(role()).contains(operation)
        && !(order.needsPayment() && Arrays.asList("ACCEPTED", "COMPLETED").contains(operation));
  }

  public boolean submit(OrderAction action) {
    if (actions.get(action.token) != action || !canSubmit(action, monitor.snapshot()) || !action.begin()) return false;
    boolean accepted = monitor.mutate(action.draft, success -> {
      action.complete(success, monitor.snapshot().notice);
      if (success) {
        confirmedOperations.put(action.draft.orderId, action.draft.operation);
        discard(action.draft);
      }
    });
    if (!accepted) action.pending = false;
    return accepted;
  }

  public String confirmedOperation(String id) { return confirmedOperations.get(id); }

  public boolean close(OrderAction action) {
    if (!action.close()) return false;
    actions.remove(action.token, action);
    return true;
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
