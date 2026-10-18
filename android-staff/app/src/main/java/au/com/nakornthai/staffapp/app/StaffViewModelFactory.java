package au.com.nakornthai.staffapp.app;

import androidx.lifecycle.*;
import au.com.nakornthai.staffapp.identity.ui.AuthViewModel;
import au.com.nakornthai.staffapp.ordering.ui.OrdersViewModel;

public final class StaffViewModelFactory implements ViewModelProvider.Factory {
  private final AppGraph graph;

  public StaffViewModelFactory(AppGraph graph) {
    this.graph = graph;
  }

  @Override
  public <T extends ViewModel> T create(Class<T> type) {
    if (type == AuthViewModel.class) return type.cast(new AuthViewModel(graph));
    if (type == OrdersViewModel.class) return type.cast(new OrdersViewModel(graph));
    if (type == au.com.nakornthai.staffapp.timekeeping.ui.TimekeepingViewModel.class)
      return type.cast(new au.com.nakornthai.staffapp.timekeeping.ui.TimekeepingViewModel(graph));
    throw new IllegalArgumentException("Unknown ViewModel");
  }
}
