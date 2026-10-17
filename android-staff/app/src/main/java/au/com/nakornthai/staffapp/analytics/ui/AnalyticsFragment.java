package au.com.nakornthai.staffapp.analytics.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import au.com.nakornthai.staffapp.R;

public final class AnalyticsFragment extends Fragment {
  public AnalyticsFragment() {
    super(R.layout.fragment_placeholder);
  }

  @Override
  public void onViewCreated(View view, Bundle saved) {
    ((TextView) view).setText("Analytics — Coming Soon");
  }
}
