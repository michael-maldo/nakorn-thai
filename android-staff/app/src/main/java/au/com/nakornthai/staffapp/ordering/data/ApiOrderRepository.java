package au.com.nakornthai.staffapp.ordering.data;

import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.shared.network.*;
import java.io.IOException;
import java.util.*;

public final class ApiOrderRepository implements OrderRepository {
  private final StaffApi api;
  private final IdentityRepository identity;

  public ApiOrderRepository(StaffApi api, IdentityRepository identity) {
    this.api = api;
    this.identity = identity;
  }

  public List<Order> load() throws IOException {
    synchronized (identity) {
      for (int attempt = 0; ; attempt++) {
        try {
          String auth = identity.authorization();
          List<Order> orders =
              HttpResult.execute(
                  "BOH".equals(identity.current().user.role)
                      ? api.kitchen(auth)
                      : api.orders(auth, false));
          if (orders == null) throw new IOException("Invalid order queue response");
          for (Order order : orders)
            if (order.id == null || order.status == null || order.items == null)
              throw new IOException("Invalid order snapshot");
          return Collections.unmodifiableList(new ArrayList<>(orders));
        } catch (ApiFailure failure) {
          if (failure.status == 401 && attempt == 0) identity.refresh();
          else {
            if (failure.status == 401) identity.clear();
            throw failure;
          }
        }
      }
    }
  }

  public Order detail(String id) throws IOException {
    synchronized (identity) {
      for (int attempt = 0; ; attempt++)
        try {
          return HttpResult.execute(api.order(id, identity.authorization()));
        } catch (ApiFailure failure) {
          if (failure.status == 401 && attempt == 0) identity.refresh();
          else {
            if (failure.status == 401) identity.clear();
            throw failure;
          }
        }
    }
  }

  public void mutate(OrderDraft draft, long principal) throws IOException {
    synchronized (identity) {
      identity.assertOwner(principal);
      Session session = identity.current();
      boolean front = !"BOH".equals(session.user.role);
      if (("PAYID".equals(draft.operation) || "PAYPAL".equals(draft.operation)) && !front)
        throw new ApiFailure(403, "Payment verification requires ADMIN or FOH.");
      for (int attempt = 0; ; attempt++) {
        try {
          String auth = identity.authorization();
          identity.assertOwner(principal);
          StaffApi.Csrf csrf = HttpResult.execute(api.orderCsrf(auth));
          Map<String, String> headers = new HashMap<>(IdentityRepository.header(csrf));
          headers.put("Authorization", auth);
          identity.assertOwner(principal);
          if ("PAYID".equals(draft.operation))
            HttpResult.execute(api.confirmPayid(draft.orderId, headers, draft.command()));
          else if ("PAYPAL".equals(draft.operation))
            HttpResult.execute(api.checkPaypal(draft.orderId, headers, Collections.emptyMap()));
          else HttpResult.execute(api.status(draft.orderId, headers, draft.command()));
          return;
        } catch (ApiFailure failure) {
          if (failure.status == 401 && attempt == 0) identity.refresh();
          else {
            if (failure.status == 401) identity.clear();
            throw failure;
          }
        } catch (IOException failure) {
          throw new ApiFailure(
              0, "The response was lost. Reconcile the order before another action.", true);
        }
      }
    }
  }
}
