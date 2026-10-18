package au.com.nakornthai.staffapp.timekeeping.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance.Entry;
import au.com.nakornthai.staffapp.timekeeping.model.AttendanceTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.*;

/** Inflated rows/cards share the same data and contextual actions. No nested scrolling list. */
final class AttendanceRows {
  private final Context context;
  private final LayoutInflater inflater;
  AttendanceRows(Context context) { this.context = context; this.inflater = LayoutInflater.from(context); }

  void render(LinearLayout parent, List<Entry> entries, boolean table, boolean admin, boolean enabled,
      ZoneId zone, Function<String,String> names, Consumer<Entry> correct, Consumer<Entry> audit) {
    parent.removeAllViews();
    if (entries.isEmpty()) { inflater.inflate(R.layout.item_timekeeping_empty, parent, true); return; }
    for (Entry entry : entries) {
      View row = inflater.inflate(table ? R.layout.item_timekeeping_shift_row : R.layout.item_timekeeping_shift_card, parent, false);
      row.setTag("shift:" + entry.id);
      set(row, R.id.shiftStaff, names.apply(entry.staffUserId));
      set(row, R.id.shiftIn, TimekeepingPresentation.timestamp(entry.clockIn, zone, Locale.getDefault()));
      set(row, R.id.shiftOut, entry.open ? context.getString(R.string.tk_no_end)
          : TimekeepingPresentation.timestamp(entry.clockOut, zone, Locale.getDefault()));
      row.findViewById(R.id.shiftIn).setContentDescription(AttendanceTime.display(entry.clockIn, zone));
      row.findViewById(R.id.shiftOut).setContentDescription(entry.open ? context.getString(R.string.tk_no_end) : AttendanceTime.display(entry.clockOut, zone));
      set(row, R.id.shiftDuration, entry.open ? context.getString(R.string.tk_incomplete) : AttendanceTime.hours(entry.workedSeconds));
      TextView status = row.findViewById(R.id.shiftStatus);
      status.setText(entry.open ? R.string.tk_status_open : R.string.tk_status_completed);
      status.setBackgroundResource(entry.open ? R.drawable.tk_status_open : R.drawable.tk_status_completed);
      status.setTextColor(ContextCompat.getColor(context, entry.open ? R.color.tk_open_ink : R.color.tk_success_ink));
      View actions = row.findViewById(R.id.shiftActions);
      actions.setVisibility(admin ? View.VISIBLE : View.GONE);
      View more = row.findViewById(R.id.shiftOverflow);
      more.setTag("overflow:" + entry.id); more.setEnabled(enabled);
      more.setContentDescription(context.getString(R.string.tk_more_actions, names.apply(entry.staffUserId), AttendanceTime.display(entry.clockIn, zone)));
      more.setOnClickListener(anchor -> {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.inflate(R.menu.timekeeping_shift);
        menu.setOnMenuItemClickListener(item -> {
          if (item.getItemId() == R.id.tkCorrectAttendance) correct.accept(entry);
          else if (item.getItemId() == R.id.tkViewAudit) audit.accept(entry);
          else return false;
          return true;
        });
        menu.show();
      });
      parent.addView(row);
    }
  }
  private void set(View row, int id, String value) { ((TextView) row.findViewById(id)).setText(value); }
}
