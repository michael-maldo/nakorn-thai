package au.com.nakornthai.staffapp.timekeeping.ui;

import android.app.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.content.ContextCompat;
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

/** One scroll/inset owner, with card/table presentation chosen from the actual content viewport. */
public final class TimekeepingFragment extends Fragment {
  private FragmentTimekeepingBinding b;
  private TimekeepingViewModel vm;
  private TimekeepingViewModel.State latest;
  private TimekeepingViewModel.Draft filled;
  private AttendanceCommand shownConfirmation;
  private AuditPage shownAudit;
  private AttendanceRows rows;
  private List<Session.User> choices = new ArrayList<>();
  private String spinnerKey;
  private boolean filling, table, horizontal, responsiveInitialized, openExpanded;
  private Dialog pickerDialog;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    b = FragmentTimekeepingBinding.inflate(inflater, parent, false);
    rows = new AttendanceRows(requireContext());
    openExpanded = saved != null && saved.getBoolean("openExpanded");
    return b.getRoot();
  }
  @Override public void onViewCreated(View view, Bundle saved) {
    MainActivity activity = (MainActivity) requireActivity();
    vm = new ViewModelProvider(this, activity.factory()).get(TimekeepingViewModel.class);
    new ViewModelProvider(activity, activity.factory()).get(AuthViewModel.class).state.observe(getViewLifecycleOwner(), s -> vm.sessionChanged());
    b.tkFrom.setOnClickListener(v -> chooseDate(true)); b.tkTo.setOnClickListener(v -> chooseDate(false));
    b.tkToday.setOnClickListener(v -> vm.selectRange("TODAY"));
    b.tkWeek.setOnClickListener(v -> vm.selectRange("WEEK"));
    b.tkMonth.setOnClickListener(v -> vm.selectRange("MONTH"));
    b.tkCustom.setOnClickListener(v -> vm.selectRange("CUSTOM"));
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
    b.tkOpenSummary.setOnClickListener(v -> { openExpanded = !openExpanded; renderOpen(latest); });
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
        if (!filling) vm.selectStaff(selectedStaff());
      }
      @Override public void onNothingSelected(AdapterView<?> p) {}
    });
    b.tkContent.addOnLayoutChangeListener((v,l,t,r,bottom,ol,ot,or,ob) -> adapt());
    vm.state.observe(getViewLifecycleOwner(), this::render);
    vm.sessionChanged();
  }
  private void adapt() {
    if (b == null) return;
    float density = getResources().getDisplayMetrics().density;
    float width = b.tkEntries.getWidth() / density;
    if (width <= 0) return;
    float font = getResources().getConfiguration().fontScale;
    boolean newTable = TimekeepingPresentation.table(width, font);
    boolean newHorizontal = TimekeepingPresentation.sideBySide(width, font);
    if (responsiveInitialized && table == newTable && horizontal == newHorizontal) return;
    table = newTable; horizontal = newHorizontal; responsiveInitialized = true;
    actionRow(b.tkLiveActions, true);
    actionRow(b.tkRangeDates, false);
    actionRow(b.tkPaging, false);
    if (latest != null) renderRecords(latest);
  }
  private void actionRow(LinearLayout row, boolean fillButtons) {
    row.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    for (int i = 0; i < row.getChildCount(); i++) {
      View child = row.getChildAt(i);
      boolean expand = horizontal && (fillButtons || child == b.tkFrom || child == b.tkTo || child == b.tkPage);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(expand ? 0 : (fillButtons ? -1 : -2), -2, expand ? 1f : 0f);
      if (i > 0) {
        if (horizontal) params.setMarginStart((int) (8 * getResources().getDisplayMetrics().density));
        else params.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
      }
      child.setLayoutParams(params);
    }
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
    b.tkTitle.setText(s.admin ? R.string.tk_page_title : R.string.tk_my_timesheet);
    b.tkSubtitle.setText(s.admin ? R.string.tk_page_subtitle : R.string.tk_my_subtitle);
    b.tkShiftsTitle.setText(s.admin ? R.string.tk_shifts_heading : R.string.tk_my_shifts_heading);
    b.tkDatesHelp.setText(s.filtersPending ? R.string.tk_filters_pending : s.admin ? R.string.tk_filter_help : R.string.tk_dates);
    b.tkZone.setText(s.zone == null ? getString(R.string.tk_zone_loading) : getString(R.string.tk_zone_format, s.zone));
    b.tkMessage.setText(!s.fresh && !s.busy ? getString(R.string.tk_stale_suffix, s.message) : s.message);
    int surface = s.notice == TimekeepingViewModel.Notice.ERROR ? R.drawable.tk_notice_error
        : s.notice == TimekeepingViewModel.Notice.SUCCESS ? R.drawable.tk_status_completed : R.drawable.tk_notice_info;
    b.tkMessage.setBackgroundResource(surface);
    b.tkMessage.setTextColor(ContextCompat.getColor(requireContext(), s.notice == TimekeepingViewModel.Notice.ERROR ? R.color.tk_error_ink : R.color.staff_ink));
    b.tkProgress.setVisibility(s.busy ? View.VISIBLE : View.GONE);
    b.tkAdmin.setVisibility(s.admin ? View.VISIBLE : View.GONE);
    b.tkManualCard.setVisibility(s.admin ? View.VISIBLE : View.GONE);
    b.tkFrom.setText(getString(R.string.tk_from_format, vm.fromDate)); b.tkTo.setText(getString(R.string.tk_to_format, vm.toDate));
    for (View control : new View[]{b.tkFrom,b.tkTo,b.tkToday,b.tkWeek,b.tkMonth,b.tkCustom,b.tkApply,b.tkStaff}) control.setEnabled(!s.busy && s.zone != null);
    int selectedRange = "WEEK".equals(s.rangePreset) ? R.id.tkWeek : "MONTH".equals(s.rangePreset) ? R.id.tkMonth : "CUSTOM".equals(s.rangePreset) ? R.id.tkCustom : R.id.tkToday;
    b.tkRangeChoices.check(selectedRange);
    b.tkRefresh.setEnabled(!s.busy);
    renderSelector(s);
    renderLive(s);
    renderOpen(s);
    b.tkManual.setEnabled(s.canWrite() && s.activeSelected() && s.pending == null);
    b.tkSummary.setText(s.sheet == null ? getString(R.string.tk_no_records_loaded)
        : getString(R.string.tk_summary_format, s.sheet.totalElements, AttendanceTime.hours(s.sheet.completedSeconds), s.sheet.openShiftCount));
    b.tkPage.setText(s.sheet == null ? "" : getString(R.string.tk_page_format, s.sheet.page + 1));
    renderEditor(s);
    renderRecords(s);
    renderAudit(s);
  }
  private void renderSelector(TimekeepingViewModel.State s) {
    List<Session.User> active = new ArrayList<>();
    for (Session.User user : s.staff) if (user.enabled) active.add(user);
    active.sort(Comparator.comparing(user -> user.username));
    String key = active.stream().map(user -> user.id + user.username + user.role).collect(java.util.stream.Collectors.joining("|"));
    if (key.equals(spinnerKey)) return;
    filling = true; choices = active; spinnerKey = key;
    List<String> labels = new ArrayList<>(); labels.add(getString(R.string.tk_all_staff));
    for (Session.User user : choices) labels.add(getString(R.string.tk_staff_option, user.username, user.role));
    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_timekeeping_staff, android.R.id.text1, labels);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); b.tkStaff.setAdapter(adapter);
    int selection = 0;
    for (int i = 0; i < choices.size(); i++) if (choices.get(i).id.equals(vm.staffId)) selection = i + 1;
    b.tkStaff.setSelection(selection); filling = false;
  }
  private void renderLive(TimekeepingViewModel.State s) {
    int status = !s.activeSelected() ? R.string.tk_status_select : !s.fresh || s.busy ? R.string.tk_status_stale
        : s.selectedOpen() ? R.string.tk_status_in : R.string.tk_status_out;
    b.tkStaffStatus.setText(status);
    b.tkStaffStatus.setBackgroundResource(s.selectedOpen() && s.fresh ? R.drawable.tk_status_open : R.drawable.tk_status_completed);
    b.tkStaffStatus.setTextColor(ContextCompat.getColor(requireContext(), s.selectedOpen() && s.fresh ? R.color.tk_open_ink : R.color.tk_success_ink));
    b.tkTimeIn.setEnabled(s.canClockIn()); b.tkTimeOut.setEnabled(s.canClockOut());
  }
  private void renderOpen(TimekeepingViewModel.State s) {
    if (s == null) return;
    b.tkOpenSummary.setVisibility(s.admin ? View.VISIBLE : View.GONE);
    b.tkOpenSummary.setText(getString(R.string.tk_open_format, s.open.size()));
    b.tkOpenSummary.setContentDescription(getString(R.string.tk_open_format, s.open.size()));
    b.tkOpenList.setVisibility(s.admin && openExpanded ? View.VISIBLE : View.GONE);
    b.tkOpenList.removeAllViews();
    if (s.open.isEmpty()) text(b.tkOpenList, getString(R.string.tk_no_open));
    for (Entry entry : s.open) text(b.tkOpenList, getString(R.string.tk_open_entry, name(entry.staffUserId), AttendanceTime.display(entry.clockIn, s.zone)));
  }
  private void renderRecords(TimekeepingViewModel.State s) {
    b.tkTableHeader.setVisibility(table && s.sheet != null && !s.sheet.entries.isEmpty() ? View.VISIBLE : View.GONE);
    b.tkHeaderActions.setVisibility(s.admin ? View.VISIBLE : View.GONE);
    if (s.sheet != null && s.zone != null) rows.render(b.tkEntries, s.sheet.entries, table, s.admin,
        s.canWrite() && s.pending == null, s.zone, this::name,
        entry -> { vm.edit(entry, null); reveal(b.tkEditor); }, entry -> vm.audit(entry.id, 0));
    else b.tkEntries.removeAllViews();
    b.tkPrevious.setEnabled(!s.busy && !s.filtersPending && s.sheet != null && s.sheet.page > 0);
    b.tkNext.setEnabled(!s.busy && !s.filtersPending && s.sheet != null && (long) (s.sheet.page + 1) * s.sheet.size < s.sheet.totalElements);
  }
  private void renderEditor(TimekeepingViewModel.State s) {
    b.tkEditor.setVisibility(s.admin && s.draft != null ? View.VISIBLE : View.GONE);
    b.tkEditorMessage.setText(s.draft == null ? "" : s.draft.error);
    if (s.draft != null && filled != s.draft) {
      filling = true; filled = s.draft;
      b.tkEditorTitle.setText(getString(filled.existing == null ? R.string.tk_manual_for : R.string.tk_correct_for, name(filled.staffId)));
      b.tkStart.setText(filled.in); b.tkEnd.setText(filled.out); b.tkReason.setText(filled.reason);
      b.tkLeaveOpen.setChecked(filled.open); b.tkLeaveOpen.setVisibility(filled.existing == null ? View.GONE : View.VISIBLE); filling = false;
    }
    boolean edit = s.canWrite() && s.pending == null;
    for (View control : new View[]{b.tkStart,b.tkReason,b.tkPickStart,b.tkLeaveOpen,b.tkReview,b.tkCancel}) control.setEnabled(edit);
    b.tkEnd.setEnabled(edit && s.draft != null && !s.draft.open); b.tkPickEnd.setEnabled(b.tkEnd.isEnabled());
    b.tkConfirmation.setVisibility(s.admin && s.pending != null ? View.VISIBLE : View.GONE);
    b.tkConfirm.setEnabled(s.canWrite() && s.pending != null); b.tkCancelConfirm.setEnabled(!s.busy);
    if (s.pending != null) {
      AttendanceCommand c = s.pending;
      String action = getString("clock-in".equals(c.operation) ? R.string.tk_action_in : "clock-out".equals(c.operation)
          ? R.string.tk_action_out : "manual".equals(c.operation) ? R.string.tk_action_manual : R.string.tk_action_correct);
      String before = s.draft != null && s.draft.existing != null ? getString(R.string.tk_previous_values, entryText(s.draft.existing)) : "";
      b.tkConfirmText.setText(c.operation.startsWith("clock-") ? getString(R.string.tk_live_confirm, action, name(c.staffUserId))
          : getString(R.string.tk_entry_confirm, action, name(c.staffUserId), before,
              AttendanceTime.display((String)c.body.get("clockIn"),s.zone),
              AttendanceTime.display(c.body.get("clockOut") instanceof String ? (String)c.body.get("clockOut") : null,s.zone), c.body.get("reason")));
      if (shownConfirmation != c) { shownConfirmation = c; reveal(b.tkConfirmation); }
    }
  }
  private void renderAudit(TimekeepingViewModel.State s) {
    b.tkAudit.setVisibility(s.admin && s.audit != null ? View.VISIBLE : View.GONE);
    b.tkAuditEntries.removeAllViews();
    if (s.audit == null) return;
    b.tkAuditTitle.setText(getString(R.string.tk_audit_title,s.auditId,s.audit.page+1));
    for (Audit a : s.audit.entries) text(b.tkAuditEntries,getString(R.string.tk_audit_line,a.action,name(a.actorId),
        AttendanceTime.display(a.occurredAt,s.zone),values(a.previousValues),values(a.newValues),a.reason==null?"":getString(R.string.tk_audit_reason,a.reason)));
    b.tkAuditPrevious.setEnabled(s.canWrite() && s.audit.page>0);
    b.tkAuditNext.setEnabled(s.canWrite() && (long)(s.audit.page+1)*s.audit.size<s.audit.totalElements);
    if (shownAudit != s.audit) { shownAudit=s.audit; reveal(b.tkAudit); }
  }
  private String values(Values value) {
    return value==null?getString(R.string.tk_no_previous):getString(R.string.tk_audit_values,AttendanceTime.display(value.clockIn,latest.zone),AttendanceTime.display(value.clockOut,latest.zone),value.version);
  }
  private String name(String id) {
    if (latest != null) for (Session.User user : latest.staff) if (user.id.equals(id)) return user.username;
    Session session = ((MainActivity)requireActivity()).graph().identity.current();
    if (session != null && session.user.id.equals(id)) return session.user.username;
    return id==null?getString(R.string.tk_select_hint):id;
  }
  private String entryText(Entry e) {
    return getString(R.string.tk_entry_summary,AttendanceTime.display(e.clockIn,latest.zone),AttendanceTime.display(e.clockOut,latest.zone),e.open?getString(R.string.tk_status_open):AttendanceTime.hours(e.workedSeconds));
  }
  private void text(LinearLayout parent,String value) {
    TextView note=(TextView)getLayoutInflater().inflate(R.layout.item_timekeeping_note,parent,false);
    note.setText(value);parent.addView(note);
  }
  @Override public void onSaveInstanceState(Bundle saved) { saved.putBoolean("openExpanded",openExpanded);super.onSaveInstanceState(saved); }
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
      vm.customDate(start, value);
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
        if (offsets.isEmpty()) { vm.error(getString(R.string.tk_gap_error)); return; }
        if (offsets.size() == 1) target.setText(local.atOffset(offsets.get(0)).toString());
        else {
          pickerDialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tk_pick_offset)
            .setItems(offsets.stream().map(Object::toString).toArray(String[]::new), (dialog, which) -> {
              if (b != null && vm.draft != null) target.setText(local.atOffset(offsets.get(which)).toString());
            }).setNegativeButton(android.R.string.cancel, null).create();
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
    b = null; filled = null; shownConfirmation = null; shownAudit = null; spinnerKey = null; rows = null; responsiveInitialized = false; super.onDestroyView();
  }
}
