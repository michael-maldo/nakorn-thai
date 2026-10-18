package au.com.nakornthai.staffapp.ordering.ui;

import android.view.*;
import androidx.recyclerview.widget.*;
import au.com.nakornthai.staffapp.databinding.ItemOrderBinding;
import au.com.nakornthai.staffapp.ordering.model.Order;
import com.google.gson.Gson;
import java.util.*;
import java.util.function.Consumer;

public final class OrderAdapter extends ListAdapter<Order, OrderAdapter.Holder> {
  private final Consumer<String> open;
  private final boolean kitchen;
  private static final Gson GSON = new Gson();

  public OrderAdapter(boolean kitchen, Consumer<String> open) {
    super(
        new DiffUtil.ItemCallback<Order>() {
          public boolean areItemsTheSame(Order a, Order b) {
            return a.id.equals(b.id);
          }

          public boolean areContentsTheSame(Order a, Order b) {
            return GSON.toJson(a).equals(GSON.toJson(b));
          }
        });
    this.open = open;
    this.kitchen = kitchen;
    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    String id = getItem(position).id;
    try {
      return UUID.fromString(id).getMostSignificantBits()
          ^ UUID.fromString(id).getLeastSignificantBits();
    } catch (IllegalArgumentException invalid) {
      return id.hashCode();
    }
  }

  @Override
  public Holder onCreateViewHolder(ViewGroup parent, int type) {
    return new Holder(
        ItemOrderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(Holder holder, int position) {
    Order order = getItem(position);
    ItemOrderBinding b = holder.binding;
    android.content.Context context=b.getRoot().getContext();
    b.cardTitle.setText(context.getString(au.com.nakornthai.staffapp.R.string.dashboard_order_reference,order.reference));
    int status=au.com.nakornthai.staffapp.shared.dashboard.DashboardPresentation.statusIndex(order.status);
    b.cardStatus.setText(status<0?order.status:context.getString(au.com.nakornthai.staffapp.shared.dashboard.QueueSummaries.LABELS[status]));
    if(status>=0)b.cardStatus.setBackgroundResource(au.com.nakornthai.staffapp.shared.dashboard.QueueSummaries.SURFACES[status]);
    b.cardTimes.setText(context.getString(au.com.nakornthai.staffapp.R.string.dashboard_order_times,
        OrderPresentation.time(order.createdAt),OrderPresentation.time(order.estimatedReadyAt)));
    b.cardBody.setText(OrderPresentation.items(order));
    b.cardPayment.setVisibility(kitchen ? View.GONE : View.VISIBLE);
    b.cardPayment.setText(kitchen ? "" : OrderPresentation.payment(order));
    b.getRoot().setOnClickListener(v -> open.accept(order.id));
    b.getRoot()
        .setContentDescription(
            context.getString(au.com.nakornthai.staffapp.R.string.dashboard_order_accessibility,order.reference,order.status));
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final ItemOrderBinding binding;

    Holder(ItemOrderBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }
  }
}
