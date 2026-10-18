package au.com.nakornthai.staffapp.timekeeping.ui;

import android.content.*;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance.Entry;
import com.google.android.material.card.MaterialCardView;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TimekeepingLayoutTest {
  @Test public void wideRowsAndNarrowLargeTextCardsShareDataAndAccessibleOverflow() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      for (float[] viewport : new float[][]{{1000,1},{700,1},{320,1},{1000,2},{320,2}}) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration(base.getResources().getConfiguration());config.fontScale=viewport[1];
        Context context=new ContextThemeWrapper(base.createConfigurationContext(config),R.style.Theme_NakornStaff);
        LinearLayout parent=new LinearLayout(context);parent.setOrientation(LinearLayout.VERTICAL);
        Entry entry=new Entry();entry.id="entry";entry.staffUserId="staff";entry.clockIn="2026-10-17T11:00:00Z";
        entry.clockOut="2026-10-17T15:00:00Z";entry.workedSeconds=new BigDecimal("14400");
        boolean table=TimekeepingPresentation.table(viewport[0],viewport[1]);
        new AttendanceRows(context).render(parent,List.of(entry),table,true,true,ZoneId.of("Australia/Melbourne"),id->"Fixture Staff",e->{},e->{});
        int width=(int)(viewport[0]*context.getResources().getDisplayMetrics().density);
        parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        parent.layout(0,0,width,parent.getMeasuredHeight());
        View row=parent.getChildAt(0);assertEquals(!table,row instanceof MaterialCardView);
        assertEquals("Fixture Staff",((TextView)row.findViewById(R.id.shiftStaff)).getText().toString());
        assertEquals(context.getString(R.string.tk_status_completed),((TextView)row.findViewById(R.id.shiftStatus)).getText().toString());
        View more=row.findViewById(R.id.shiftOverflow);assertTrue(more.isEnabled());
        assertTrue(more.getContentDescription().toString().contains("Fixture Staff"));
        assertTrue(more.getHeight()>=48*context.getResources().getDisplayMetrics().density);
        assertTrue(row.findViewById(R.id.shiftIn).getMeasuredWidth()>0);
        assertTrue(row.findViewById(R.id.shiftOut).getMeasuredWidth()>0);
      }
    });
  }
  @Test public void staffRowsHideActionsAndEmptyStateIsReadable() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      Context context=new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),R.style.Theme_NakornStaff);
      LinearLayout parent=new LinearLayout(context);
      Entry entry=new Entry();entry.id="open";entry.staffUserId="staff";entry.clockIn="2026-10-18T00:00:00Z";entry.open=true;
      AttendanceRows renderer=new AttendanceRows(context);
      renderer.render(parent,List.of(entry),false,false,false,ZoneId.of("Australia/Melbourne"),id->"Me",e->{throw new AssertionError();},e->{throw new AssertionError();});
      assertEquals(View.GONE,parent.getChildAt(0).findViewById(R.id.shiftActions).getVisibility());
      assertEquals(context.getString(R.string.tk_incomplete),((TextView)parent.getChildAt(0).findViewById(R.id.shiftDuration)).getText().toString());
      renderer.render(parent,List.of(),false,false,false,ZoneId.of("Australia/Melbourne"),id->"Me",e->{},e->{});
      assertEquals(1,parent.getChildCount());
      assertTrue(parent.getChildAt(0) instanceof LinearLayout);
    });
  }
}
