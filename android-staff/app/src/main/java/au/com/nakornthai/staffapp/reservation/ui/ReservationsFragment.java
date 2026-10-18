package au.com.nakornthai.staffapp.reservation.ui;

import android.os.Bundle;
import android.view.View;
import androidx.fragment.app.Fragment;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;

/** Honest capability state: no Android reservation API has been implemented. */
public final class ReservationsFragment extends Fragment {
  public ReservationsFragment() { super(R.layout.fragment_reservations); }
  @Override public void onViewCreated(View view, Bundle saved) {
    view.findViewById(R.id.reservationOrders).setOnClickListener(v->((MainActivity)requireActivity()).navigate(R.id.ordersFragment));
  }
}
