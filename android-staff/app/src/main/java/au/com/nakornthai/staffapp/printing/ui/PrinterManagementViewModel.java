package au.com.nakornthai.staffapp.printing.ui;

import android.app.Application;
import androidx.lifecycle.*;
import au.com.nakornthai.staffapp.app.StaffApplication;
import au.com.nakornthai.staffapp.identity.data.Session;
import au.com.nakornthai.staffapp.printing.connection.*;
import au.com.nakornthai.staffapp.printing.data.*;
import au.com.nakornthai.staffapp.printing.diagnostics.*;
import au.com.nakornthai.staffapp.printing.model.*;
import java.util.*;
import java.util.concurrent.*;

public final class PrinterManagementViewModel extends AndroidViewModel {
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final PrinterConfigurationRepository repository;
  private final PrinterDiagnostics diagnostics;
  public final MutableLiveData<List<PrinterProfile>> profiles =
      new MutableLiveData<>(Collections.emptyList());
  public final MutableLiveData<String> message = new MutableLiveData<>("Loading printer profiles…");
  public final MutableLiveData<Boolean> busy = new MutableLiveData<>(true);
  private List<PrinterProfile> current = new ArrayList<>();
  private boolean working = true;
  public PrinterProfile draft;
  public boolean editing;

  public PrinterManagementViewModel(Application a) {
    super(a);
    repository = new PrinterConfigurationRepository(a);
    diagnostics = new PrinterDiagnostics(new AndroidPrinterConnections(a, timer), executor, timer);
    executor.execute(
        () -> {
          current = repository.load();
          profiles.postValue(new ArrayList<>(current));
          message.postValue(
              repository.warning.isEmpty()
                  ? "Profiles are device-local. Saved does not mean online."
                  : repository.warning);
          synchronized (this) {
            working = false;
          }
          busy.postValue(false);
        });
  }

  public static boolean isAdmin(Session s) {
    return s != null && s.valid() && "ADMIN".equals(s.user.role);
  }

  public boolean admin() {
    return isAdmin(((StaffApplication) getApplication()).graph().identity.current());
  }

  private synchronized boolean begin() {
    if (!admin()) {
      message.setValue("ADMIN session required");
      return false;
    }
    if (working || diagnostics.busy()) return false;
    working = true;
    busy.setValue(true);
    return true;
  }

  public void save(PrinterProfile p, boolean remove) {
    if (!begin()) return;
    String owner = ((StaffApplication) getApplication()).graph().identity.current().user.id;
    executor.execute(
        () -> {
          try {
            if (!sameAdmin(owner)) throw new IllegalStateException("ADMIN session changed");
            List<PrinterProfile> next = new ArrayList<>(current);
            next.removeIf(x -> x.id.equals(p.id));
            if (!remove) {
              p.validate();
              next.add(p.copy());
            }
            repository.save(next);
            current = next;
            profiles.postValue(new ArrayList<>(current));
            message.postValue(
                remove
                    ? "Printer removed"
                    : "Printer profile saved; physical output remains unverified");
          } catch (Exception e) {
            message.postValue(e.getMessage());
          } finally {
            finish();
          }
        });
  }

  private boolean sameAdmin(String owner) {
    Session s = ((StaffApplication) getApplication()).graph().identity.current();
    return isAdmin(s) && owner.equals(s.user.id);
  }

  private synchronized void finish() {
    working = false;
    busy.postValue(false);
  }

  public void diagnose(PrinterProfile p, boolean print) {
    if (!begin()) return;
    String owner = ((StaffApplication) getApplication()).graph().identity.current().user.id;
    diagnostics.run(
        p.copy(),
        print,
        () -> sameAdmin(owner),
        s -> record(p, s),
        s -> {
          try {
            record(p, s);
          } catch (Exception e) {
            message.postValue(s + " · Could not persist diagnostic result");
          } finally {
            finish();
          }
        });
  }

  private void record(PrinterProfile p, String result) {
    List<PrinterProfile> next = new ArrayList<>();
    for (PrinterProfile x : current) {
      PrinterProfile copy = x.copy();
      if (x.id.equals(p.id)) {
        copy.lastDiagnostic = result;
        copy.diagnosticAt = System.currentTimeMillis();
      }
      next.add(copy);
    }
    repository.save(next);
    current = next;
    profiles.postValue(new ArrayList<>(current));
    message.postValue(result);
  }

  @Override
  protected void onCleared() {
    // Keep deadlines alive until any bounded in-flight operation finishes.
    executor.execute(timer::shutdown);
    executor.shutdown();
  }
}
