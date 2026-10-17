package au.com.nakornthai.staffapp.identity.ui;

import androidx.lifecycle.*;
import au.com.nakornthai.staffapp.app.AppGraph;
import au.com.nakornthai.staffapp.identity.data.Session;
import java.io.IOException;

public final class AuthViewModel extends ViewModel {
  public static final class State {
    public final Session session;
    public final boolean busy;
    public final String message;

    public State(Session session, boolean busy, String message) {
      this.session = session;
      this.busy = busy;
      this.message = message;
    }
  }

  private final MutableLiveData<State> mutableState =
      new MutableLiveData<>(new State(null, true, "Restoring staff session…"));
  public final LiveData<State> state = mutableState;
  private final AppGraph graph;
  private boolean busy = true;
  private String message;

  public AuthViewModel(AppGraph graph) {
    this.graph = graph;
    graph.identity.setListener(
        () -> {
          if (graph.identity.current() == null && !busy)
            message = "Session expired or revoked. Please sign in again.";
          graph.monitor.sessionChanged();
          mutableState.postValue(new State(graph.identity.current(), busy, message));
        });
    graph.executor.execute(
        () -> {
          try {
            graph.identity.restore();
          } catch (IOException failure) {
            message = failure.getMessage();
          } finally {
            busy = false;
            publish();
          }
        });
  }

  private void publish() {
    mutableState.postValue(new State(graph.identity.current(), busy, message));
  }

  public void login(String username, String password) {
    if (busy) return;
    busy = true;
    message = null;
    publish();
    graph.executor.execute(
        () -> {
          try {
            graph.identity.login(username.trim(), password);
          } catch (IOException | RuntimeException failure) {
            message =
                failure instanceof IOException
                    ? failure.getMessage()
                    : "Secure session storage unavailable.";
          } finally {
            busy = false;
            publish();
          }
        });
  }

  public void logout() {
    if (busy) return;
    busy = true;
    publish();
    graph.monitor.setForeground(false);
    graph.executor.execute(
        () -> {
          try {
            graph.identity.logout();
            message = "Signed out.";
          } catch (IOException failure) {
            message =
                "Signed out locally. Server revocation could not be confirmed; the server session"
                    + " retains its existing expiry.";
          } catch (RuntimeException storageFailure) {
            message =
                "Session closed in memory, but secure storage could not be cleared. Clear app data"
                    + " before reusing this terminal.";
          } finally {
            busy = false;
            publish();
            graph.monitor.setForeground(true);
          }
        });
  }
}
