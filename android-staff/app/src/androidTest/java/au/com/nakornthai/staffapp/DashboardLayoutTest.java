package au.com.nakornthai.staffapp;

import android.content.*;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.ordering.monitoring.BoardState;
import au.com.nakornthai.staffapp.ordering.model.Order;
import au.com.nakornthai.staffapp.shared.dashboard.QueueSummaries;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardLayoutTest {
  @Test public void sharedMetricsWrapAndAlwaysContainFourActualCounts() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
      for(float[] size:new float[][]{{320,1},{1000,1},{320,2},{1000,2}}) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration c=new Configuration(base.getResources().getConfiguration());c.fontScale=size[1];
        Context context=new ContextThemeWrapper(base.createConfigurationContext(c),R.style.Theme_NakornStaff);
        GridLayout grid=new GridLayout(context);QueueSummaries metrics=new QueueSummaries(grid,null);
        Order order=new Order("fixture","TEST","NEW",0,"PAY_AT_RESTAURANT",null,List.of());
        metrics.render(new BoardState(List.of(order),false,false,true,false,1,1,1,null,null));
        int width=(int)(size[0]*context.getResources().getDisplayMetrics().density);
        // Layout twice: the first measured content width selects the grid column count.
        for(int pass=0;pass<2;pass++) {
          grid.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
          grid.layout(0,0,width,grid.getMeasuredHeight());
        }
        assertEquals(4,grid.getChildCount());
        for(int i=0;i<4;i++) {
          View card=grid.getChildAt(i);assertTrue(card.getMeasuredWidth()>0);assertTrue(card.getRight()<=width);
          assertFalse(card.isClickable());
          assertEquals(i==0?"1":"0",((TextView)card.findViewById(R.id.metricCount)).getText().toString());
        }
      }
    });
  }
  @Test public void sharedMetricsSurviveRepeatedWideAndNarrowResizing() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
      Context context=new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),R.style.Theme_NakornStaff);
      GridLayout grid=new GridLayout(context);
      new QueueSummaries(grid,index->{});
      View first=grid.getChildAt(0);
      for(int dp:new int[]{1000,320,1000,320}) {
        int width=(int)(dp*context.getResources().getDisplayMetrics().density);
        for(int pass=0;pass<3;pass++) {
          grid.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
          grid.layout(0,0,width,grid.getMeasuredHeight());
        }
        assertEquals(dp==1000?4:2,grid.getColumnCount());
        assertEquals(4,grid.getChildCount());assertSame(first,grid.getChildAt(0));
        for(int i=0;i<4;i++) {
          View card=grid.getChildAt(i);
          assertTrue(card.getMeasuredWidth()>0);assertTrue(card.getRight()<=width);
          assertTrue(card.isClickable());
        }
      }
    });
  }
  @Test public void reservationsAnalyticsAndManageFitShortLargeFontWindowsAndScroll() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
      Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
      Configuration config=new Configuration(base.getResources().getConfiguration());config.fontScale=2;
      Context context=new ContextThemeWrapper(base.createConfigurationContext(config),R.style.Theme_NakornStaff);
      int width=(int)(320*context.getResources().getDisplayMetrics().density),height=(int)(180*context.getResources().getDisplayMetrics().density);
      for(int layout:new int[]{R.layout.fragment_reservations,R.layout.fragment_analytics,R.layout.sheet_manage}) {
        View scroll=LayoutInflater.from(context).inflate(layout,null);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        scroll.layout(0,0,width,height);assertTrue(scroll.canScrollVertically(1));
      }
    });
  }
}
