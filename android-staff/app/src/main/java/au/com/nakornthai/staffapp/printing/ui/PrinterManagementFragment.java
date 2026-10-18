package au.com.nakornthai.staffapp.printing.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.FragmentPrintersBinding;
import au.com.nakornthai.staffapp.identity.ui.AuthViewModel;
import au.com.nakornthai.staffapp.printing.discovery.*;
import au.com.nakornthai.staffapp.printing.driver.*;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class PrinterManagementFragment extends Fragment {
  private final java.util.List<PrinterDriver> drivers = new PrinterDriverRegistry().all();
  private FragmentPrintersBinding b;
  private PrinterManagementViewModel vm;
  private PrinterDiscovery discovery;
  private final Set<String> seen = new HashSet<>();
  private boolean filling;
  private final ActivityResultLauncher<String> connectPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            if (b != null)
              b.printerMessage.setText(
                  granted
                      ? "Bluetooth connection permission granted. Choose Test Connection or Print"
                          + " Test Docket again."
                      : "Bluetooth connection permission denied. No printer data sent.");
          });
  private final ActivityResultLauncher<String[]> permissions =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(),
          result -> {
            if (b == null || !vm.admin()) return;
            if (BluetoothPrinterDiscovery.permitted(requireContext())) startDiscovery();
            else
              b.discoveryStatus.setText(
                  "Bluetooth discovery permission denied. Profiles and manual LAN setup remain"
                      + " available.");
          });

  @Override
  public View onCreateView(LayoutInflater i, ViewGroup c, Bundle state) {
    b = FragmentPrintersBinding.inflate(i, c, false);
    return b.getRoot();
  }

  @Override
  public void onViewCreated(View v, Bundle state) {
    vm = new ViewModelProvider(requireActivity()).get(PrinterManagementViewModel.class);
    MainActivity activity = (MainActivity) requireActivity();
    new ViewModelProvider(activity, activity.factory())
        .get(AuthViewModel.class)
        .state
        .observe(
            getViewLifecycleOwner(),
            s -> {
              boolean admin = PrinterManagementViewModel.isAdmin(s.session) && !s.busy;
              b.getRoot().setVisibility(admin ? View.VISIBLE : View.GONE);
              if (!admin) {
                stopDiscovery();
                vm.draft = null;
                vm.editing = false;
              }
              if (s.session != null && !admin && !s.busy) activity.navigate(R.id.ordersFragment);
            });
    if (!vm.admin()) {
      b.getRoot().setVisibility(View.GONE);
      return;
    }
    spinner(
        b.printerTransport,
        new String[] {
          "Network / LAN", "Bluetooth Classic (SPP)", "Bluetooth Low Energy (discovery only)"
        });
    spinner(b.printerDriver, drivers.stream().map(PrinterDriver::label).toArray(String[]::new));
    spinner(b.printerPaper, new String[] {"58 mm — 32 columns", "80 mm — 48 columns"});
    spinner(b.printerAssignment, new String[] {"Kitchen", "FOH"});
    b.printerTransport.setOnItemSelectedListener(
        selected(
            () -> {
              if (!filling) {
                stopDiscovery();
                capture();
              }
              capabilities();
            }));
    b.printerDriver.setOnItemSelectedListener(
        selected(
            () -> {
              if (!filling
                  && vm.draft != null
                  && !vm.draft.driver.equals(
                      drivers.get(b.printerDriver.getSelectedItemPosition()).id()))
                b.printerCompatible.setChecked(false);
              capabilities();
            }));
    b.printerEndpoint.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!filling) b.printerCompatible.setChecked(false);
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    b.addPrinter.setOnClickListener(x -> edit(new PrinterProfile()));
    b.cancelPrinter.setOnClickListener(
        x -> {
          stopDiscovery();
          vm.editing = false;
          vm.draft = null;
          renderEditing();
        });
    b.savePrinter.setOnClickListener(
        x -> {
          try {
            capture();
            vm.draft.validate();
            vm.save(vm.draft.copy(), false);
            vm.editing = false;
            stopDiscovery();
            renderEditing();
          } catch (Exception e) {
            b.discoveryStatus.setText(e.getMessage());
          }
        });
    b.discoverPrinters.setOnClickListener(
        x -> {
          capture();
          if (!"LAN".equals(vm.draft.type)
              && !BluetoothPrinterDiscovery.permitted(requireContext())) {
            b.discoveryStatus.setText(
                "Bluetooth discovery needs Nearby Devices permission on Android 12+, or location"
                    + " permission on Android 11 and earlier. No location is collected.");
            permissions.launch(BluetoothPrinterDiscovery.permissions());
          } else startDiscovery();
        });
    b.stopPrinterDiscovery.setOnClickListener(
        x -> {
          stopDiscovery();
          b.discoveryStatus.setText("Discovery cancelled");
        });
    b.bluetoothSettings.setOnClickListener(
        x -> {
          try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
          } catch (Exception e) {
            b.discoveryStatus.setText("Bluetooth settings unavailable");
          }
        });
    vm.profiles.observe(getViewLifecycleOwner(), this::renderList);
    vm.message.observe(getViewLifecycleOwner(), s -> b.printerMessage.setText(s));
    vm.busy.observe(
        getViewLifecycleOwner(),
        busy -> {
          b.printerProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
          b.addPrinter.setEnabled(!busy);
          b.savePrinter.setEnabled(!busy);
          renderList(vm.profiles.getValue());
        });
    if (vm.editing && vm.draft != null) fill();
    renderEditing();
  }

  private void spinner(Spinner s, String[] choices) {
    ArrayAdapter<String> a =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, choices);
    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    s.setAdapter(a);
  }

  private AdapterView.OnItemSelectedListener selected(Runnable r) {
    return new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
        r.run();
      }

      public void onNothingSelected(AdapterView<?> p) {}
    };
  }

  private void edit(PrinterProfile p) {
    if (!vm.admin() || Boolean.TRUE.equals(vm.busy.getValue())) return;
    stopDiscovery();
    vm.draft = p.copy();
    vm.editing = true;
    fill();
    renderEditing();
  }

  private void fill() {
    filling = true;
    PrinterProfile p = vm.draft;
    b.printerName.setText(p.name);
    b.printerEndpoint.setText(p.endpoint);
    b.printerPort.setText(String.valueOf(p.port));
    b.printerTimezone.setText(p.timezone);
    b.printerTransport.setSelection("LAN".equals(p.type) ? 0 : "CLASSIC".equals(p.type) ? 1 : 2);
    b.printerDriver.setSelection(
        java.util.stream.IntStream.range(0, drivers.size())
            .filter(index -> drivers.get(index).id().equals(p.driver))
            .findFirst()
            .orElse(0));
    b.printerPaper.setSelection(p.paperWidth == 80 ? 1 : 0);
    b.printerAssignment.setSelection("FOH".equals(p.assignment) ? 1 : 0);
    b.printerEnabled.setChecked(p.enabled);
    b.printerCompatible.setChecked(p.compatibilityConfirmed);
    b.printerIdentity.setText(
        "Manufacturer: "
            + p.manufacturer
            + "\nModel: "
            + p.model
            + "\nDiscovered services: "
            + (p.discoveredServices.isEmpty() ? "Unknown" : p.discoveredServices)
            + "\n"
            + "Name and endpoint are editable; capabilities below are driver-declared, not"
            + " device-verified.");
    filling = false;
    capabilities();
  }

  private void capture() {
    if (b == null || vm.draft == null) return;
    PrinterProfile p = vm.draft;
    p.name = b.printerName.getText().toString().trim();
    p.endpoint = b.printerEndpoint.getText().toString().trim();
    try {
      p.port = Integer.parseInt(b.printerPort.getText().toString());
    } catch (NumberFormatException e) {
      p.port = 0;
    }
    p.timezone = b.printerTimezone.getText().toString().trim();
    p.type = new String[] {"LAN", "CLASSIC", "BLE"}[b.printerTransport.getSelectedItemPosition()];
    p.driver = drivers.get(b.printerDriver.getSelectedItemPosition()).id();
    p.paperWidth = b.printerPaper.getSelectedItemPosition() == 1 ? 80 : 58;
    p.assignment = b.printerAssignment.getSelectedItemPosition() == 1 ? "FOH" : "Kitchen";
    p.enabled = b.printerEnabled.isChecked();
    p.compatibilityConfirmed = b.printerCompatible.isChecked();
  }

  private void capabilities() {
    if (b == null) return;
    PrinterDriver driver = drivers.get(b.printerDriver.getSelectedItemPosition());
    boolean esc = driver.capabilities().text;
    boolean ble = b.printerTransport.getSelectedItemPosition() == 2;
    b.printerPaper.setEnabled(esc && !ble);
    b.printerCompatible.setEnabled(esc && !ble);
    b.printerPort.setVisibility(
        b.printerTransport.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
    b.bluetoothSettings.setVisibility(
        b.printerTransport.getSelectedItemPosition() == 0 ? View.GONE : View.VISIBLE);
    b.printerCapabilities.setText(
        ble
            ? "BLE discovery supported. GATT printing and connection tests unsupported until a"
                + " compatible driver is added."
            : esc
                ? driver.capabilities().description
                : "Profile can be saved. Physical test printing disabled until a compatible driver"
                    + " is selected and confirmed.");
  }

  private void renderEditing() {
    if (b == null) return;
    b.printerEditor.setVisibility(vm.editing ? View.VISIBLE : View.GONE);
    b.printerList.setVisibility(vm.editing ? View.GONE : View.VISIBLE);
    b.addPrinter.setVisibility(vm.editing ? View.GONE : View.VISIBLE);
  }

  private void renderList(List<PrinterProfile> list) {
    if (b == null) return;
    b.printerList.removeAllViews();
    if (list == null || list.isEmpty()) {
      TextView t = new TextView(requireContext());
      t.setText(
          "No configured printers. Add a profile; a saved profile is not proof of connectivity.");
      b.printerList.addView(t);
      return;
    }
    boolean busy = Boolean.TRUE.equals(vm.busy.getValue());
    for (PrinterProfile p : list) {
      View profile = getLayoutInflater().inflate(R.layout.item_dashboard_printer,b.printerList,false);
      LinearLayout row = profile.findViewById(R.id.profileActions);
      TextView info = profile.findViewById(R.id.profileInfo);
      String stamp =
          p.diagnosticAt == 0
              ? "Never"
              : DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")
                  .withZone(ZoneId.of(p.timezone))
                  .format(Instant.ofEpochMilli(p.diagnosticAt));
      info.setText(
          p.name
              + "\n"
              + p.type
              + " · "
              + p.endpoint
              + ("LAN".equals(p.type) ? ":" + p.port : "")
              + "\nDriver: "
              + new PrinterDriverRegistry().get(p.driver).label()
              + "\n"
              + p.assignment
              + " · "
              + (p.enabled ? "Enabled" : "Disabled")
              + " · Independent destination member (no default/automatic routing)\n"
              + "Last diagnostic: "
              + p.lastDiagnostic
              + "\n"
              + stamp);

      button(row, "Edit", busy, () -> edit(p));
      button(
          row,
          "Test Connection",
          busy || !p.enabled || "BLE".equals(p.type),
          () -> diagnose(p, false));
      button(
          row,
          "Print Test Docket",
          busy || !new PrinterDriverRegistry().get(p.driver).canPrint(p),
          () ->
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle("Print one test docket?")
                  .setMessage(
                      "Confirm the selected endpoint belongs to your printer. This sends one ASCII"
                          + " test only. Physical output cannot be confirmed by the app. If an"
                          + " earlier attempt was uncertain, inspect the paper before proceeding.")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton("Send test", (d, w) -> diagnose(p, true))
                  .show());
      button(
          row,
          p.enabled ? "Disable" : "Enable",
          busy,
          () -> {
            PrinterProfile copy = p.copy();
            copy.enabled = !copy.enabled;
            vm.save(copy, false);
          });
      button(
          row,
          "Remove",
          busy,
          () ->
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle("Remove " + p.name + "?")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton("Remove", (d, w) -> vm.save(p, true))
                  .show());
      b.printerList.addView(profile);
    }
  }

  private void button(LinearLayout row, String title, boolean disabled, Runnable action) {
    com.google.android.material.button.MaterialButton button =
        (com.google.android.material.button.MaterialButton)getLayoutInflater().inflate(R.layout.item_dashboard_action,row,false);
    button.setText(title);
    button.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
    button.setEnabled(!disabled);
    button.setOnClickListener(
        v -> {
          if (vm.admin()) action.run();
        });
    row.addView(button, new LinearLayout.LayoutParams(-2, -2));
  }

  private void diagnose(PrinterProfile p, boolean print) {
    if (!vm.admin()) return;
    if ("CLASSIC".equals(p.type)
        && android.os.Build.VERSION.SDK_INT >= 31
        && androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.BLUETOOTH_CONNECT)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      connectPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT);
      return;
    }
    vm.diagnose(p, print);
  }

  private void startDiscovery() {
    if (b == null || !vm.admin()) return;
    capture();
    stopDiscovery();
    seen.clear();
    b.discoveredPrinters.removeAllViews();
    discovery =
        "LAN".equals(vm.draft.type)
            ? new NetworkPrinterDiscovery(requireContext())
            : new BluetoothPrinterDiscovery(requireContext(), "BLE".equals(vm.draft.type));
    discovery.start(
        device -> {
          if (!isAdded()) return;
          requireActivity()
              .runOnUiThread(
                  () -> {
                    if (b == null
                        || !vm.admin()
                        || !vm.editing
                        || !seen.add(device.type + device.identifier + device.port)) return;
                    button(
                        b.discoveredPrinters,
                        device.toString(),
                        false,
                        () -> {
                          capture();
                          vm.draft.name = device.name;
                          vm.draft.endpoint = device.identifier;
                          if (device.port > 0) vm.draft.port = device.port;
                          vm.draft.discoveredServices = device.services;
                          vm.draft.driver = "unsupported";
                          vm.draft.compatibilityConfirmed = false;
                          stopDiscovery();
                          fill();
                        });
                  });
        },
        s -> {
          if (isAdded())
            requireActivity()
                .runOnUiThread(
                    () -> {
                      if (b != null) b.discoveryStatus.setText(s);
                    });
        });
  }

  private void stopDiscovery() {
    if (discovery != null) discovery.stop();
    discovery = null;
  }

  @Override
  public void onStop() {
    if (vm != null && vm.editing) capture();
    stopDiscovery();
    super.onStop();
  }

  @Override
  public void onDestroyView() {
    stopDiscovery();
    b = null;
    super.onDestroyView();
  }
}
