package au.com.nakornthai.staffapp.ordering.ui;

import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.ordering.model.OrderDraft;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/** Missing orders never regain a submit control through retained drafts. */
public final class RetainedDraftsDialog extends DialogFragment {
  @Override
  public Dialog onCreateDialog(Bundle saved) {
    OrdersViewModel vm =
        new ViewModelProvider(requireActivity(), ((MainActivity) requireActivity()).factory())
            .get(OrdersViewModel.class);
    List<OrderDraft> drafts = vm.orphanDrafts();
    String[] labels = new String[drafts.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] =
          "Discard #"
              + drafts.get(i).orderReference
              + " · "
              + OrderPresentation.action(drafts.get(i).operation);
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Orders outside this queue — discard draft")
        .setItems(labels, (dialog, index) -> vm.discard(drafts.get(index)))
        .setNegativeButton("Keep drafts", null)
        .create();
  }
}
