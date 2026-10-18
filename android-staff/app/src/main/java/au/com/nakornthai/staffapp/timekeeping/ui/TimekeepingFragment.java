package au.com.nakornthai.staffapp.timekeeping.ui;

import android.app.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.FragmentTimekeepingBinding;
import au.com.nakornthai.staffapp.identity.data.Session;
import au.com.nakornthai.staffapp.identity.ui.AuthViewModel;
import au.com.nakornthai.staffapp.timekeeping.model.*;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.*;
import java.util.*;

/** A single scroll owner keeps forms, confirmations and paging reachable above the IME. */
public final class TimekeepingFragment extends Fragment {
  private FragmentTimekeepingBinding b;
  private TimekeepingViewModel vm;
  private TimekeepingViewModel.State latest;
  private TimekeepingViewModel.Draft filled;
  private AttendanceCommand shownConfirmation;
  private AuditPage shownAudit;
  private List<Session.User> choices = new ArrayList<>();
  private String spinnerKey;
  private boolean filling;
  private Dialog pickerDialog;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    b = FragmentTimekeepingBinding.inflate(inflater, parent, false); return b.getRoot();
  }
  @Override public void onViewCreated(View view, Bundle saved) {
    MainActivity activity = (MainActivity) requireActivity();
    vm = new ViewModelProvider(this, activity.factory()).get(TimekeepingViewModel.class);
    new ViewModelProvider(activity, activity.factory()).get(AuthViewModel.class).state.observe(getViewLifecycleOwner(), s -> vm.sessionChanged());
    b.tkFrom.setOnClickListener(v -> chooseDate(true)); b.tkTo.setOnClickListener(v -> chooseDate(false));
    b.tkToday.setOnClickListener(v -> vm.today());
    b.tkApply.setOnClickListener(v -> vm.filter(vm.fromDate, vm.toDate, selectedStaff()));
    b.tkRefresh.setOnClickListener(v -> vm.refresh(0));
    b.tkTimeIn.setOnClickListener(v -> vm.reviewLive("clock-in", selectedStaff()));
    b.tkTimeOut.setOnClickListener(v -> vm.reviewLive("clock-out", selectedStaff()));
    b.tkManual.setOnClickListener(v -> { vm.edit(null, selectedStaff()); reveal(b.tkEditor); });
    b.tkReview.setOnClickListener(v -> vm.reviewDraft()); b.tkConfirm.setOnClickListener(v -> vm.confirm());
    b.tkCancel.setOnClickListener(v -> vm.cancel()); b.tkCancelConfirm.setOnClickListener(v -> vm.cancel());
    b.tkPrevious.setOnClickListener(v -> { if (latest.sheet != null) vm.refresh(latest.sheet.page - 1); });
    b.tkNext.setOnClickListener(v -> { if (latest.sheet != null) vm.refresh(latest.sheet.page + 1); });
    b.tkAuditPrevious.setOnClickListener(v -> vm.audit(latest.auditId, latest.audit.page - 1));
    b.tkAuditNext.setOnClickListener(v -> vm.audit(latest.auditId, latest.audit.page + 1));
    watch(b.tkStart, value -> { if (vm.draft != null) vm.draft.in = value; });
    watch(b.tkEnd, value -> { if (vm.draft != null) vm.draft.out = value; });
    watch(b.tkReason, value -> { if (vm.draft != null) vm.draft.reason = value; });
    b.tkLeaveOpen.setOnCheckedChangeListener((button, checked) -> {
      if (!filling && vm.draft != null) { vm.draft.open = checked; b.tkEnd.setEnabled(!checked); b.tkPickEnd.setEnabled(!checked); }
    });
    b.tkPickStart.setOnClickListener(v -> chooseTimestamp(b.tkStart));
    b.tkPickEnd.setOnClickListener(v -> chooseTimestamp(b.tkEnd));
    b.tkStaff.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        if (!filling) vm.staffId = selectedStaff();
      }
      @Override public void onNothingSelected(AdapterView<?> p) {}
    });
    vm.state.observe(getViewLifecycleOwner(), this::render);
    vm.sessionChanged();
  }
  private void watch(EditText field, java.util.function.Consumer<String> update) {
    field.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) { if (!filling) update.accept(s.toString()); }
      public void afterTextChanged(Editable value) {}
    });
  }
  private String selectedStaff() {
    int position = b.tkStaff.getSelectedItemPosition();
    return position > 0 && position <= choices.size() ? choices.get(position - 1).id : null;
  }
  private void render(TimekeepingViewModel.State s) {
    if (b == null) return;
    latest = s;
    b.tkTitle.setText(s.admin ? "Timekeeping" : "My Timesheet");
    b.tkZone.setText(s.zone == null ? "Loading restaurant timezone…" : "Restaurant timezone: " + s.zone);
    b.tkMessage.setText(s.message + (!s.fresh && !s.busy ? "\nAttendance may be stale. Refresh before making changes." : ""));
    b.tkMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    b.tkProgress.setVisibility(s.busy ? View.VISIBLE : View.GONE);
    b.tkAdmin.setVisibility(s.admin ? View.VISIBLE : View.GONE);
    b.tkApply.setText(s.admin ? "Apply date range / staff filter" : "Apply date range");
    b.tkFrom.setText("From date: " + vm.fromDate); b.tkTo.setText("Through date: " + vm.toDate);
    for (View control : new View[]{b.tkFrom, b.tkTo, b.tkToday, b.tkApply, b.tkStaff}) control.setEnabled(!s.busy && s.zone != null);
    b.tkRefresh.setEnabled(!s.busy);
    List<Session.User> active = new ArrayList<>();
    for (Session.User user : s.staff) if (user.enabled) active.add(user);
    active.sort(Comparator.comparing(user -> user.username));
    String key = active.stream().map(user -> user.id + user.username + user.role).collect(java.util.stream.Collectors.joining("|"));
    if (!key.equals(spinnerKey)) {
      filling = true; choices = active; spinnerKey = key;
      List<String> labels = new ArrayList<>(); labels.add("All staff (select one for live/manual actions)");
      for (Session.User user : choices) labels.add(user.username + " · " + user.role);
      ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); b.tkStaff.setAdapter(adapter);
      int selection = 0;
      for (int i = 0; i < choices.size(); i++) if (choices.get(i).id.equals(vm.staffId)) selection = i + 1;
      b.tkStaff.setSelection(selection); filling = false;
    }
    for (View action : new View[]{b.tkTimeIn, b.tkTimeOut, b.tkManual}) action.setEnabled(s.canWrite());
    b.tkSummary.setText(s.sheet == null ? "No attendance loaded." : s.sheet.totalElements + " matching shifts · "
        + AttendanceTime.hours(s.sheet.completedSeconds) + " completed · " + s.sheet.openShiftCount + " open\nPage "
        + (s.sheet.page + 1) + ". Full matching shifts counted; incomplete shifts excluded from completed hours.");
    b.tkOpenSummary.setText("Currently open shifts — all dates and all staff (" + s.open.size() + ")");
    b.tkOpenList.removeAllViews();
    if (s.admin && s.open.isEmpty()) text(b.tkOpenList, "No currently open shifts.");
    for (Entry entry : s.open) text(b.tkOpenList, entryText(entry));
    b.tkEditor.setVisibility(s.admin && s.draft != null ? View.VISIBLE : View.GONE);
    b.tkEditorMessage.setText(s.draft == null ? "" : s.draft.error);
    if (s.draft != null && filled != s.draft) {
      filling = true; filled = s.draft;
      b.tkEditorTitle.setText((filled.existing == null ? "Manual entry for " : "Correct entry for ") + name(filled.staffId));
      b.tkStart.setText(filled.in); b.tkEnd.setText(filled.out); b.tkReason.setText(filled.reason);
      b.tkLeaveOpen.setChecked(filled.open); b.tkLeaveOpen.setVisibility(filled.existing == null ? View.GONE : View.VISIBLE); filling = false;
    }
    boolean edit = s.canWrite() && s.pending == null;
    for (View control : new View[]{b.tkStart, b.tkReason, b.tkPickStart, b.tkLeaveOpen, b.tkReview, b.tkCancel}) control.setEnabled(edit);
    b.tkEnd.setEnabled(edit && s.draft != null && !s.draft.open); b.tkPickEnd.setEnabled(b.tkEnd.isEnabled());
    b.tkConfirmation.setVisibility(s.admin && s.pending != null ? View.VISIBLE : View.GONE);
    b.tkConfirm.setEnabled(s.canWrite() && s.pending != null); b.tkCancelConfirm.setEnabled(!s.busy);
    if (s.pending != null) {
      AttendanceCommand c = s.pending;
      String before = s.draft != null && s.draft.existing != null ? "\nPrevious: " + entryText(s.draft.existing) : "";
      b.tkConfirmText.setText("Confirm " + c.operation + " for " + name(c.staffUserId) + "?" + before
          + (c.operation.startsWith("clock-") ? "\nThe server will supply the attendance timestamp." :
          "\nNew start: " + AttendanceTime.display((String) c.body.get("clockIn"), s.zone)
          + "\nNew end: " + AttendanceTime.display(c.body.get("clockOut") instanceof String ? (String) c.body.get("clockOut") : null, s.zone)
          + "\nReason: " + c.body.get("reason")));
      if (shownConfirmation != c) { shownConfirmation = c; reveal(b.tkConfirmation); }
    }
    b.tkEntries.removeAllViews();
    if (s.sheet != null && s.sheet.entries.isEmpty()) text(b.tkEntries, "No attendance matches these dates.");
    if (s.sheet != null) for (Entry entry : s.sheet.entries) {
      text(b.tkEntries, entryText(entry));
      if (s.admin) {
        button(b.tkEntries, "Correct attendance", "correct:" + entry.id, s.canWrite(), () -> { vm.edit(entry, null); reveal(b.tkEditor); });
        button(b.tkEntries, "View audit history", "audit:" + entry.id, s.canWrite(), () -> vm.audit(entry.id, 0));
      }
    }
    b.tkPrevious.setEnabled(!s.busy && s.sheet != null && s.sheet.page > 0);
    b.tkNext.setEnabled(!s.busy && s.sheet != null && (long) (s.sheet.page + 1) * s.sheet.size < s.sheet.totalElements);
    b.tkAudit.setVisibility(s.admin && s.audit != null ? View.VISIBLE : View.GONE);
    b.tkAuditEntries.removeAllViews();
    if (s.audit != null) {
      b.tkAuditTitle.setText("Audit history · " + s.auditId + " · page " + (s.audit.page + 1));
      for (Audit a : s.audit.entries) text(b.tkAuditEntries, a.action + " · " + name(a.actorId) + "\n"
          + AttendanceTime.display(a.occurredAt, s.zone) + "\nBefore: " + values(a.previousValues)
          + "\nAfter: " + values(a.newValues) + (a.reason == null ? "" : "\nReason: " + a.reason));
      b.tkAuditPrevious.setEnabled(s.canWrite() && s.audit.page > 0);
      b.tkAuditNext.setEnabled(s.canWrite() && (long) (s.audit.page + 1) * s.audit.size < s.audit.totalElements);
      if (shownAudit != s.audit) { shownAudit = s.audit; reveal(b.tkAudit); }
    }
  }
  private String values(Values v) {
    return v == null ? "No previous entry" : AttendanceTime.display(v.clockIn, latest.zone) + " → "
        + AttendanceTime.display(v.clockOut, latest.zone) + " · version " + v.version;
  }
  private String name(String id) {
    if (latest != null) for (Session.User user : latest.staff) if (user.id.equals(id)) return user.username;
    return id == null ? "Select an active staff account" : id;
  }
  private String entryText(Entry e) {
    return (latest.admin ? name(e.staffUserId) + "\n" : "") + AttendanceTime.display(e.clockIn, latest.zone)
        + " → " + AttendanceTime.display(e.clockOut, latest.zone) + "\n"
        + (e.open ? "OPEN — excluded from completed hours" : "Completed: " + AttendanceTime.hours(e.workedSeconds));
  }
  private void text(LinearLayout container, String value) {
    TextView text = new TextView(requireContext()); text.setText(value); text.setTextIsSelectable(true);
    int pad = (int) (12 * getResources().getDisplayMetrics().density); text.setPadding(0, pad, 0, pad);
    container.addView(text, new LinearLayout.LayoutParams(-1, -2));
  }
  private void button(LinearLayout container, String title, String tag, boolean enabled, Runnable action) {
    Button button = new com.google.android.material.button.MaterialButton(requireContext());
    button.setText(title); button.setTag(tag); button.setEnabled(enabled);
    button.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
    button.setOnClickListener(v -> action.run()); container.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }
  private void reveal(View target) {
    target.post(() -> {
      if (b != null && target.getVisibility() == View.VISIBLE)
        target.requestRectangleOnScreen(new android.graphics.Rect(0, 0, target.getWidth(), Math.min(target.getHeight(), 120)), false);
    });
  }
  private void chooseDate(boolean start) {
    if (latest == null || latest.zone == null) return;
    LocalDate day;
    try { day = LocalDate.parse(start ? vm.fromDate : vm.toDate); }
    catch (DateTimeException e) { return; }
    pickerDialog = new DatePickerDialog(requireContext(), (picker, year, month, date) -> {
      if (b == null) return;
      String value = LocalDate.of(year, month + 1, date).toString();
      if (start) { vm.fromDate = value; b.tkFrom.setText("From date: " + value); }
      else { vm.toDate = value; b.tkTo.setText("Through date: " + value); }
    }, day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
    pickerDialog.show();
  }
  private void chooseTimestamp(EditText target) {
    if (latest == null || latest.zone == null || vm.draft == null) return;
    ZoneId zone = latest.zone;
    LocalDate date = LocalDate.parse(vm.fromDate);
    try { date = OffsetDateTime.parse(target.getText().toString()).toLocalDate(); } catch (DateTimeException ignored) { }
    pickerDialog = new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
      if (b == null || vm.draft == null) return;
      pickerDialog = new TimePickerDialog(requireContext(), (clock, hour, minute) -> {
        if (b == null || vm.draft == null) return;
        LocalDateTime local = LocalDateTime.of(year, month + 1, day, hour, minute);
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) { vm.error("This local time does not exist because of daylight saving. Choose another time."); return; }
        if (offsets.size() == 1) target.setText(local.atOffset(offsets.get(0)).toString());
        else {
          pickerDialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Repeated time — choose UTC offset")
            .setItems(offsets.stream().map(Object::toString).toArray(String[]::new), (dialog, which) -> {
              if (b != null && vm.draft != null) target.setText(local.atOffset(offsets.get(which)).toString());
            }).setNegativeButton("Cancel", null).create();
          pickerDialog.show();
        }
      }, 9, 0, true);
      pickerDialog.show();
    }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
    pickerDialog.show();
  }
  @Override public void onDestroyView() {
    if (pickerDialog != null) pickerDialog.dismiss();
    pickerDialog = null;
    b = null; filled = null; shownConfirmation = null; shownAudit = null; spinnerKey = null; super.onDestroyView();
  }
}
