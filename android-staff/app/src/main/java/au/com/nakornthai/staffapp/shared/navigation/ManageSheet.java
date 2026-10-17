package au.com.nakornthai.staffapp.shared.navigation;

import android.os.Bundle;
import android.view.*;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public final class ManageSheet extends BottomSheetDialogFragment {
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    View view = inflater.inflate(R.layout.sheet_manage, parent, false);
    view.findViewById(R.id.manageOrders).setOnClickListener(v -> go(R.id.ordersFragment));
    view.findViewById(R.id.manageReservations)
        .setOnClickListener(v -> go(R.id.reservationsFragment));
    view.findViewById(R.id.manageAnalytics).setOnClickListener(v -> go(R.id.analyticsFragment));
    view.findViewById(R.id.manageSignOut)
        .setOnClickListener(
            v -> {
              dismiss();
              ((MainActivity) requireActivity()).signOut();
            });
    MainActivity activity = (MainActivity) requireActivity();
    view.findViewById(R.id.managePrinters).setVisibility(
        au.com.nakornthai.staffapp.printing.ui.PrinterManagementViewModel.isAdmin(activity.graph().identity.current()) ? View.VISIBLE : View.GONE);
    view.findViewById(R.id.managePrinters).setOnClickListener(v -> go(R.id.printerManagementFragment));
    return view;
  }

  private void go(int destination) {
    ((MainActivity) requireActivity()).navigate(destination);
    dismiss();
  }
}
