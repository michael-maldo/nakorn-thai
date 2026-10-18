package au.com.nakornthai.staffapp.analytics.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.ordering.ui.*;
import au.com.nakornthai.staffapp.shared.dashboard.QueueSummaries;

/** Queue counts come from the same role-filtered monitor, not an invented historical API. */
public final class AnalyticsFragment extends Fragment {
  public AnalyticsFragment() { super(R.layout.fragment_analytics); }
  @Override public void onViewCreated(View view, Bundle saved) {
    MainActivity activity=(MainActivity)requireActivity();
    OrdersViewModel vm=new ViewModelProvider(activity,activity.factory()).get(OrdersViewModel.class);
    QueueSummaries metrics=new QueueSummaries(view.findViewById(R.id.analyticsSummaries),null);
    View refresh=view.findViewById(R.id.analyticsRefresh);refresh.setOnClickListener(v->vm.refresh());
    vm.state.observe(getViewLifecycleOwner(),state->{
      metrics.render(state);refresh.setEnabled(!state.loading && !state.busy);
      TextView status=view.findViewById(R.id.analyticsStatus);
      status.setText(state.error!=null?getString(R.string.dashboard_stale):state.loading?getString(R.string.dashboard_synchronizing):getString(R.string.dashboard_active));
      status.setBackgroundResource(state.error!=null?R.drawable.tk_notice_error:R.drawable.tk_notice_info);
      ((TextView)view.findViewById(R.id.analyticsSync)).setText(state.lastSync==0?getString(R.string.dashboard_waiting):getString(R.string.dashboard_analytics_sync,OrderPresentation.time(java.time.Instant.ofEpochMilli(state.lastSync).toString())));
    });
  }
}
