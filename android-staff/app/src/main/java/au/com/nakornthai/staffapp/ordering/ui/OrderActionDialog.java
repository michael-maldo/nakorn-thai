package au.com.nakornthai.staffapp.ordering.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.DialogOrderActionBinding;
import au.com.nakornthai.staffapp.ordering.model.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class OrderActionDialog extends DialogFragment {
  private OrdersViewModel vm;
  private OrderAction action;

  public static OrderActionDialog create(String id, String operation) {
    OrderActionDialog dialog = new OrderActionDialog();
    Bundle args = new Bundle();
    args.putString("id", id);
    args.putString("operation", operation);
    args.putString("actionToken", java.util.UUID.randomUUID().toString());
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle saved) {
    vm = new ViewModelProvider(requireActivity(), ((MainActivity) requireActivity()).factory())
        .get(OrdersViewModel.class);
    String id = requireArguments().getString("id"), operation = requireArguments().getString("operation");
    // Arguments survive recreation; a submitted dialog reattaches to its own operation.
    String token = requireArguments().getString("actionToken");
    if (token == null) { token = java.util.UUID.randomUUID().toString(); requireArguments().putString("actionToken", token); }
    action = vm.action(id, operation, token);
    if (action == null)
      return new MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.order_action_missing)
          .setPositiveButton(R.string.label_close, (d, w) -> dismiss())
          .create();
    OrderDraft draft = action.draft;
    DialogOrderActionBinding b = DialogOrderActionBinding.inflate(getLayoutInflater());
    setCancelable(false);
    String op = draft.operation;
    boolean accept = op.equals("ACCEPTED"), cancel = op.equals("CANCELLED"),
        payid = op.equals("PAYID"), complete = op.equals("COMPLETED");
    boolean retained = OrderDraft.retainedOperation(op);
    b.actionDescription.setText(cancel
        ? getString(R.string.order_cancel_description, draft.orderReference)
        : payid ? getString(R.string.order_payid_description, draft.orderReference)
        : op.equals("PAYPAL") ? getString(R.string.order_paypal_description, draft.orderReference)
        : getString(R.string.order_transition_description, draft.orderReference, OrderPresentation.target(op)));
    b.confirmAction.setText(cancel ? R.string.label_cancel_order : R.string.label_confirm);
    b.closeAction.setText(cancel ? R.string.label_keep_order : retained ? R.string.label_keep_draft_and_close : R.string.label_cancel);
    b.discardDraft.setVisibility(retained ? View.VISIBLE : View.GONE);
    if (cancel) b.confirmAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
        com.google.android.material.color.MaterialColors.getColor(b.confirmAction, com.google.android.material.R.attr.colorError)));
    b.actionInput.setVisibility(accept || cancel || payid ? View.VISIBLE : View.GONE);
    b.actionInput.setHint(accept ? R.string.order_pickup_estimate : payid ? R.string.order_bank_reference : R.string.order_cancellation_reason);
    b.actionInput.setInputType(accept ? android.text.InputType.TYPE_CLASS_NUMBER
        : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    b.actionInput.setText(accept ? draft.pickupMinutes : payid ? draft.bankReference : draft.reason);
    b.actionCheck.setVisibility(payid || complete ? View.VISIBLE : View.GONE);
    b.actionCheck.setText(payid ? R.string.order_bank_acknowledgement : R.string.order_handover_acknowledgement);
    b.actionCheck.setChecked(payid ? draft.bankReceiptChecked : draft.paymentCollected);
    b.actionInput.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (accept) draft.pickupMinutes = s.toString();
        else if (payid) draft.bankReference = s.toString();
        else draft.reason = s.toString();
        if (!action.finished && !action.pending) { action.feedback = null; b.actionError.setText(""); }
      }
      public void afterTextChanged(Editable text) {}
    });
    b.actionCheck.setOnCheckedChangeListener((button, checked) -> {
      if (payid) draft.bankReceiptChecked = checked;
      else draft.paymentCollected = checked;
    });
    b.confirmAction.setOnClickListener(v -> {
      if (action.pending || action.finished) return;
      String invalid = op.equals("PAYPAL") ? null : draft.validate();
      if (invalid != null) { action.feedback = invalid; b.actionError.setText(invalid); return; }
      if (!vm.submit(action)) {
        action.feedback = getString(R.string.order_action_blocked);
        b.actionError.setText(action.feedback);
      } else b.confirmAction.setEnabled(false);
    });
    b.discardDraft.setOnClickListener(v -> { if (vm.close(action)) { vm.discard(draft); dismiss(); } });
    b.closeAction.setOnClickListener(v -> { if (vm.close(action)) dismiss(); });
    vm.state.observe(this, state -> {
      boolean conflict = draft.conflict(state.find(id));
      b.confirmAction.setEnabled(vm.canSubmit(action, state));
      b.actionInput.setEnabled(!state.busy && !action.finished);
      b.actionCheck.setEnabled(!state.busy && !action.finished);
      b.discardDraft.setEnabled(!state.busy && !action.pending);
      b.closeAction.setEnabled(!state.busy && !action.pending);
      if (action.pending) b.actionError.setText(R.string.order_saving_once);
      else if (action.feedback != null) b.actionError.setText(action.feedback);
      else if (conflict) b.actionError.setText(retained ? R.string.order_draft_conflict : R.string.order_transition_conflict);
      else if (!state.canWrite()) b.actionError.setText(R.string.order_action_blocked);
      else b.actionError.setText(""); // Never inherit a queue-wide notice from another operation.
      if (action.finished && action.succeeded) dismiss();
    });
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle(OrderPresentation.action(op)).setView(b.getRoot()).create();
  }

  @Override public void onDismiss(DialogInterface dialog) {
    if (action != null && vm != null && getActivity() != null && !getActivity().isChangingConfigurations()) vm.close(action);
    super.onDismiss(dialog);
  }
}
