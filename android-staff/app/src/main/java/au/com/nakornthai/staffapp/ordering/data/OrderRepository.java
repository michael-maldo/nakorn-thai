package au.com.nakornthai.staffapp.ordering.data;

import au.com.nakornthai.staffapp.ordering.model.*;
import java.io.IOException;
import java.util.List;

public interface OrderRepository {
  List<Order> load() throws IOException;

  Order detail(String id) throws IOException;

  void mutate(OrderDraft draft, long principal) throws IOException;
}
