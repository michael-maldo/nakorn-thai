package au.com.nakornthai.staffapp.printing;

import au.com.nakornthai.staffapp.ordering.model.Order;
import java.util.function.Consumer;

/** Dispatches one explicitly requested kitchen docket and reports its transport outcome. */
public interface KitchenDocketPrinter {
  boolean print(Order order, String pickupMinutes, Consumer<String> result);
}
