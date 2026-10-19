package au.com.nakornthai.staffapp.ordering.model;

/** One dialog's command and feedback. Retained input never retains a previous operation's result. */
public final class OrderAction {
  public final String token;
  public final OrderDraft draft;
  public final long owner;
  public volatile boolean pending, finished, succeeded, closed;
  public volatile String feedback;

  public OrderAction(String token, OrderDraft draft, long owner) {
    this.token = token;
    this.draft = draft;
    this.owner = owner;
  }

  public synchronized boolean begin() {
    if (pending || finished || closed) return false;
    pending = true;
    feedback = null;
    return true;
  }

  public synchronized void complete(boolean success, String message) {
    pending = false;
    succeeded = success;
    if (!closed) feedback = success ? null : message;
    finished = true;
  }

  public synchronized boolean close() {
    if (pending) return false; // Dismissal cannot undo an already-dispatched command.
    closed = true;
    return true;
  }
}
