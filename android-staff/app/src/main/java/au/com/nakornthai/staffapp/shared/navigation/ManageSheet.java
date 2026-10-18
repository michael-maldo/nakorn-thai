package au.com.nakornthai.staffapp.shared.navigation;

import android.os.Bundle;
import android.view.*;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;

public final class ManageSheet extends BottomSheetDialogFragment {
  private View insetContainer;
  @Override
  public int getTheme() {
    return R.style.Theme_NakornStaff_Manage;
  }

  @Override
  public void onStart() {
    super.onStart();
    BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
    WindowCompat.setDecorFitsSystemWindows(dialog.getWindow(), false);
    // The dialog is a separate window: constrain its coordinator to its own safe viewport.
    View container = dialog.findViewById(com.google.android.material.R.id.container);
    View coordinator = dialog.findViewById(com.google.android.material.R.id.coordinator);
    View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
    container.setFitsSystemWindows(false);
    coordinator.setFitsSystemWindows(false);
    sheet.setFitsSystemWindows(false);
    if (insetContainer != container) {
      WindowInsetsSupport.apply(container);
      insetContainer = container;
    }
    ViewCompat.requestApplyInsets(container);
    BottomSheetBehavior<?> behavior = dialog.getBehavior();
    behavior.setSkipCollapsed(true);
    behavior.setDraggable(false);
    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
  }

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
