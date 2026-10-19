package au.com.nakornthai.staffapp.ordering.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.DialogOrderBinding;
import au.com.nakornthai.staffapp.ordering.model.Order;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class OrderDetailDialog extends DialogFragment {
  public static OrderDetailDialog create(String id) {
    OrderDetailDialog dialog = new OrderDetailDialog();
    Bundle args = new Bundle();
    args.putString("id", id);
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle saved) {
    OrdersViewModel vm =
        new ViewModelProvider(requireActivity(), ((MainActivity) requireActivity()).factory())
            .get(OrdersViewModel.class);
    DialogOrderBinding binding = DialogOrderBinding.inflate(getLayoutInflater());
    String id = requireArguments().getString("id");
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Order details")
            .setView(binding.getRoot())
            .setPositiveButton("Close", null)
            .create();
    vm.state.observe(
        this,
        state -> {
          Order order = state.find(id);
          binding.actions.removeAllViews();
          binding.cancelOrderSection.setVisibility(android.view.View.GONE);
          if (order == null) {
            String confirmed = vm.confirmedOperation(id);
            binding.detailNotice.setText("");
            binding.detailBody.setText("CANCELLED".equals(confirmed)
                ? getString(au.com.nakornthai.staffapp.R.string.order_cancelled_result)
                : "COMPLETED".equals(confirmed)
                    ? getString(au.com.nakornthai.staffapp.R.string.order_completed_result)
                    : "This order is no longer in your current queue. It may have completed or changed on another terminal.");
            return;
          }
          dialog.setTitle("#" + order.reference);
          binding.detailBody.setText(OrderPresentation.detail(order, vm.role()));
          binding.detailNotice.setText(
              state.error == null ? "" : state.error);
          au.com.nakornthai.staffapp.ordering.model.OrderDraft paymentDraft =
              vm.existingDraft(id, true);
          if (paymentDraft != null) add(binding, paymentDraft.operation, order, !state.busy);
          else if (!"BOH".equals(vm.role()) && order.needsPayment()) {
            if ("PAYID".equals(order.paymentMethod)) add(binding, "PAYID", order, state.canWrite());
            if ("PAYPAL".equals(order.paymentMethod))
              add(binding, "PAYPAL", order, state.canWrite());
          }
          for (String action : order.actions(vm.role())) {
            if ("CANCELLED".equals(action)) continue;
            add(
                binding,
                action,
                order,
                state.canWrite()
                    && !(order.needsPayment()
                        && (action.equals("ACCEPTED") || action.equals("COMPLETED"))));
          }
          if (order.actions(vm.role()).contains("CANCELLED")) {
            binding.cancelOrderSection.setVisibility(android.view.View.VISIBLE);
            binding.cancelOrderAction.setText(au.com.nakornthai.staffapp.R.string.label_cancel_order);
            binding.cancelOrderAction.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                binding.cancelOrderAction, com.google.android.material.R.attr.colorError));
            binding.cancelOrderAction.setEnabled(vm.existingDraft(id, false) != null ? !state.busy : state.canWrite());
            binding.cancelOrderAction.setOnClickListener(v -> open(order.id, "CANCELLED"));
          }
        });
    return dialog;
  }

  private void add(DialogOrderBinding binding, String operation, Order order, boolean enabled) {
    Button button = (Button)getLayoutInflater().inflate(au.com.nakornthai.staffapp.R.layout.item_dashboard_action,binding.actions,false);
    button.setText(OrderPresentation.action(operation));
    button.setEnabled(enabled);
    button.setOnClickListener(
        v -> {
          open(order.id, operation);
        });
    binding.actions.addView(button);
  }

  private void open(String id, String operation) {
    if (getParentFragmentManager().findFragmentByTag("action") == null)
      OrderActionDialog.create(id, operation).show(getParentFragmentManager(), "action");
  }
}
