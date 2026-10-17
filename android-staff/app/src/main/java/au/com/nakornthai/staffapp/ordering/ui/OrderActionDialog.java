package au.com.nakornthai.staffapp.ordering.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.DialogOrderActionBinding;
import au.com.nakornthai.staffapp.ordering.model.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class OrderActionDialog extends DialogFragment {
  private boolean submitted;

  public static OrderActionDialog create(String id, String operation) {
    OrderActionDialog dialog = new OrderActionDialog();
    Bundle args = new Bundle();
    args.putString("id", id);
    args.putString("operation", operation);
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle saved) {
    OrdersViewModel vm =
        new ViewModelProvider(requireActivity(), ((MainActivity) requireActivity()).factory())
            .get(OrdersViewModel.class);
    String id = requireArguments().getString("id"),
        operation = requireArguments().getString("operation");
    OrderDraft draft = vm.draft(id, operation);
    if (draft == null)
      return new MaterialAlertDialogBuilder(requireContext())
          .setMessage("Order is no longer in the queue. Refresh Orders.")
          .setPositiveButton("Close", (d, w) -> dismiss())
          .create();
    DialogOrderActionBinding b = DialogOrderActionBinding.inflate(getLayoutInflater());
    setCancelable(false);
    String op = draft.operation;
    boolean accept = op.equals("ACCEPTED"),
        cancel = op.equals("CANCELLED"),
        payid = op.equals("PAYID"),
        complete = op.equals("COMPLETED");
    b.actionDescription.setText(
        cancel
            ? "Cancellation does not refund payment. Arrange any refund separately."
            : payid
                ? "Check the actual restaurant bank account before confirming receipt."
                : "Confirm this action using the current server order version.");
    b.actionInput.setVisibility(accept || cancel || payid ? View.VISIBLE : View.GONE);
    b.actionInput.setHint(
        accept
            ? "Pickup estimate (5–180 minutes)"
            : payid ? "Bank transaction reference" : "Cancellation reason");
    b.actionInput.setInputType(
        accept
            ? android.text.InputType.TYPE_CLASS_NUMBER
            : android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    b.actionInput.setText(
        accept ? draft.pickupMinutes : payid ? draft.bankReference : draft.reason);
    b.actionCheck.setVisibility(payid || complete ? View.VISIBLE : View.GONE);
    b.actionCheck.setText(
        payid
            ? "I verified receipt of the full amount in the bank account."
            : "Payment is collected and the customer is receiving this order.");
    b.actionCheck.setChecked(payid ? draft.bankReceiptChecked : draft.paymentCollected);
    b.actionInput.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (accept) draft.pickupMinutes = s.toString();
            else if (payid) draft.bankReference = s.toString();
            else draft.reason = s.toString();
          }

          public void afterTextChanged(Editable text) {}
        });
    b.actionCheck.setOnCheckedChangeListener(
        (button, checked) -> {
          if (payid) draft.bankReceiptChecked = checked;
          else draft.paymentCollected = checked;
        });
    b.confirmAction.setOnClickListener(
        v -> {
          if (submitted) return;
          String invalid = op.equals("PAYPAL") ? null : draft.validate();
          if (invalid != null) {
            b.actionError.setText(invalid);
            return;
          }
          b.confirmAction.setEnabled(false);
          submitted = vm.submit(draft);
          if (!submitted)
            b.actionError.setText(
                "Action blocked. Check the latest order, payment state and connection; discard"
                    + " stale drafts.");
        });
    b.discardDraft.setOnClickListener(
        v -> {
          vm.discard(draft);
          dismiss();
        });
    b.closeAction.setOnClickListener(v -> dismiss());
    vm.state.observe(
        this,
        state -> {
          boolean conflict = draft.conflict(state.find(id));
          b.confirmAction.setEnabled(state.canWrite() && !conflict);
          b.actionInput.setEnabled(!state.busy);
          b.actionCheck.setEnabled(!state.busy);
          b.discardDraft.setEnabled(!state.busy);
          b.closeAction.setEnabled(!state.busy);
          if (conflict)
            b.actionError.setText(
                "Order changed or outcome needs reconciliation. Draft retained. Discard this draft"
                    + " and use the latest order.");
          else if (state.busy) b.actionError.setText("Saving once…");
          else if (state.notice != null) b.actionError.setText(state.notice);
          if (submitted && !state.busy && !draft.invalidated) {
            dismiss();
          }
        });
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle(OrderPresentation.action(op))
        .setView(b.getRoot())
        .create();
  }
}
