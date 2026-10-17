package au.com.nakornthai.staffapp.reservation.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import au.com.nakornthai.staffapp.R;

public final class ReservationsFragment extends Fragment {
  public ReservationsFragment() {
    super(R.layout.fragment_placeholder);
  }

  @Override
  public void onViewCreated(View view, Bundle saved) {
    ((TextView) view).setText("Reservations — Coming Soon");
  }
}
