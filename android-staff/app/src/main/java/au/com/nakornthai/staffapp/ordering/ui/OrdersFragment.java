package au.com.nakornthai.staffapp.ordering.ui;

import android.os.Bundle;
import android.view.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.*;
import au.com.nakornthai.staffapp.ordering.monitoring.BoardState;
import com.google.android.material.tabs.TabLayout;
import java.util.*;

public final class OrdersFragment extends Fragment {
  private static final String[] STATES = {"NEW", "ACCEPTED", "PREPARING", "READY"},
      LABELS = {"Pending", "Accepted", "Preparing", "Ready"};
  private FragmentOrdersBinding binding;
  private final List<ViewOrderColumnBinding> columns = new ArrayList<>();
  private final List<OrderAdapter> adapters = new ArrayList<>();
  private int selected;
  private OrdersViewModel vm;
  private boolean wide;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    binding = FragmentOrdersBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle saved) {
    vm =
        new ViewModelProvider(requireActivity(), ((MainActivity) requireActivity()).factory())
            .get(OrdersViewModel.class);
    selected = saved == null ? 0 : saved.getInt("statusTab", 0);
    for (int i = 0; i < 4; i++) {
      ViewOrderColumnBinding column =
          ViewOrderColumnBinding.inflate(getLayoutInflater(), binding.columns, false);
      columns.add(column);
      column.orderList.setId(
          new int[] {
                R.id.orderListNew,
                R.id.orderListAccepted,
                R.id.orderListPreparing,
                R.id.orderListReady
              }
              [i]);
      android.widget.LinearLayout.LayoutParams params =
          new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
      int gutter = getResources().getDimensionPixelSize(R.dimen.board_gutter);
      params.setMargins(i == 0 ? 0 : gutter / 2, 0, i == 3 ? 0 : gutter - gutter / 2, 0);
      binding.columns.addView(column.getRoot(), params);
      OrderAdapter adapter =
          new OrderAdapter(
              "BOH".equals(vm.role()),
              id -> {
                if (getParentFragmentManager().findFragmentByTag("order") == null)
                  OrderDetailDialog.create(id).show(getParentFragmentManager(), "order");
              });
      adapters.add(adapter);
      column.orderList.setLayoutManager(new LinearLayoutManager(requireContext()));
      column.orderList.setAdapter(adapter);
      binding.statusTabs.addTab(binding.statusTabs.newTab().setText(LABELS[i]));
    }
    binding.statusTabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          public void onTabSelected(TabLayout.Tab tab) {
            selected = tab.getPosition();
            layoutColumns();
          }

          public void onTabUnselected(TabLayout.Tab tab) {}

          public void onTabReselected(TabLayout.Tab tab) {}
        });
    binding.statusTabs.getTabAt(selected).select();
    binding.columns.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          boolean next = (r - l) / getResources().getDisplayMetrics().density >= 800;
          if (next != wide) {
            wide = next;
            layoutColumns();
          }
        });
    layoutColumns();
    binding.refresh.setOnClickListener(v -> vm.refresh());
    binding.retainedDrafts.setOnClickListener(
        v -> {
          if (getParentFragmentManager().findFragmentByTag("retainedDrafts") == null)
            new RetainedDraftsDialog().show(getParentFragmentManager(), "retainedDrafts");
        });
    vm.state.observe(getViewLifecycleOwner(), this::render);
  }

  private void layoutColumns() {
    if (binding == null) return;
    binding.statusTabs.setVisibility(wide ? View.GONE : View.VISIBLE);
    int gutter = getResources().getDimensionPixelSize(R.dimen.board_gutter);
    for (int i = 0; i < columns.size(); i++) {
      View column = columns.get(i).getRoot();
      android.widget.LinearLayout.LayoutParams params =
          (android.widget.LinearLayout.LayoutParams) column.getLayoutParams();
      int left = wide && i > 0 ? gutter / 2 : 0, right = wide && i < 3 ? gutter - gutter / 2 : 0;
      if (params.leftMargin != left || params.rightMargin != right) {
        params.setMargins(left, 0, right, 0);
        column.setLayoutParams(params);
      }
      column.setVisibility(wide || selected == i ? View.VISIBLE : View.GONE);
    }
  }

  private void render(BoardState state) {
    binding.refresh.setEnabled(!state.loading && !state.busy);
    int retained = vm.orphanDrafts().size();
    binding.retainedDrafts.setVisibility(retained == 0 ? View.GONE : View.VISIBLE);
    binding.retainedDrafts.setText("Review " + retained + " retained draft(s) outside this queue");
    // Connection and last-sync status stay in the persistent shell; avoid a duplicate
    // healthy-status row above the board or a layout jump on every polling cycle.
    binding.connection.setVisibility(state.orders.size() == 200 ? View.VISIBLE : View.GONE);
    binding.connection.setText(R.string.queue_limit_notice);
    binding.boardMessage.setText(
        (state.busy ? "Saving… " : "")
            + (state.error == null ? "" : state.error + " ")
            + (state.notice == null ? "" : state.notice)
            + (state.blocked ? " Refresh required before another action." : ""));
    binding.boardMessage.setVisibility(
        binding.boardMessage.getText().length() == 0 ? View.GONE : View.VISIBLE);
    for (int i = 0; i < 4; i++) {
      List<au.com.nakornthai.staffapp.ordering.model.Order> orders = state.column(STATES[i]);
      columns.get(i).columnHeading.setText(LABELS[i]);
      columns.get(i).columnCount.setText(String.valueOf(orders.size()));
      columns
          .get(i)
          .columnCount
          .setContentDescription(getString(R.string.order_column_count, LABELS[i], orders.size()));
      binding.statusTabs.getTabAt(i).setText(LABELS[i] + " " + orders.size());
      adapters.get(i).submitList(orders);
      columns.get(i).emptyColumn.setText("No " + LABELS[i].toLowerCase(Locale.ROOT) + " orders");
      columns.get(i).emptyColumn.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    out.putInt("statusTab", selected);
    super.onSaveInstanceState(out);
  }

  @Override
  public void onDestroyView() {
    for (ViewOrderColumnBinding column : columns) column.orderList.setAdapter(null);
    columns.clear();
    adapters.clear();
    binding = null;
    super.onDestroyView();
  }
}
