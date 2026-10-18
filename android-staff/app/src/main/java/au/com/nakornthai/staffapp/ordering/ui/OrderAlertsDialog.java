package au.com.nakornthai.staffapp.ordering.ui;

import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Keeps acknowledge available from every screen, including very short windows. */
public final class OrderAlertsDialog extends DialogFragment {
  @Override public Dialog onCreateDialog(Bundle saved) {
    MainActivity activity=(MainActivity)requireActivity();
    OrdersViewModel vm=new ViewModelProvider(activity,activity.factory()).get(OrdersViewModel.class);
    androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.dashboard_alert_title)
        .setMessage(getString(R.string.dashboard_alert_body,0))
        .setPositiveButton(R.string.label_acknowledge_alert,(d,w)->vm.acknowledge())
        .setNeutralButton(R.string.dashboard_view_orders,(d,w)->activity.navigate(R.id.ordersFragment))
        .setNegativeButton(R.string.dashboard_close,null).create();
    vm.state.observe(this,state->dialog.setMessage(getString(R.string.dashboard_alert_body,state.unacknowledged)));
    return dialog;
  }
}
