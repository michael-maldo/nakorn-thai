package au.com.nakornthai.staffapp.app;

import android.app.Application;
import android.net.*;
import androidx.lifecycle.*;

public class StaffApplication extends Application implements DefaultLifecycleObserver {
  private AppGraph graph;

  public AppGraph graph() {
    return graph;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    graph = createGraph();
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    connectivity.registerDefaultNetworkCallback(
        new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(Network network) {
            graph.monitor.refresh();
          }
        });
  }

  protected AppGraph createGraph() {
    return new AppGraph(this);
  }

  @Override
  public void onStart(LifecycleOwner owner) {
    graph.monitor.setForeground(true);
  }

  @Override
  public void onStop(LifecycleOwner owner) {
    graph.monitor.setForeground(false);
  }
}
