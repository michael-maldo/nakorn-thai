package au.com.nakornthai.staffapp;

import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.app.MainActivity;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/** Real post-login layout regression; Android JVM stubs cannot validate grid spans. */
@RunWith(AndroidJUnit4.class)
public class LoginDashboardRegressionTest {
  @Test public void administratorLogin() throws Exception { login("admin"); }
  @Test public void frontOfHouseLogin() throws Exception { login("foh"); }
  @Test public void kitchenLogin() throws Exception { login("boh"); }

  private void login(String role) throws Exception {
    FixtureApplication app=(FixtureApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.graph().executor.submit(()->app.graph().identity.clear()).get(5,TimeUnit.SECONDS);
    app.status="NEW";app.offline=false;app.expired=false;
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
      try {
        StaffUiTest.waitVisible(R.id.signIn);
        onView(withId(R.id.username)).perform(replaceText("fixture-"+role));
        onView(withId(R.id.password)).perform(replaceText("fixture-password"),closeSoftKeyboard());
        onView(withId(R.id.signIn)).perform(click());
        StaffUiTest.waitVisible(R.id.refresh);
        StaffUiTest.retry(()->scenario.onActivity(a->{
          assertGrid(a.findViewById(R.id.orderSummaries));
          assertEquals(View.VISIBLE,a.findViewById(R.id.dashboardTopBar).getVisibility());
          assertEquals(role.equals("admin")?View.VISIBLE:View.GONE,a.findViewById(R.id.overlayPrinters).getVisibility());
          assertEquals(role.equals("admin")?"Timekeeping":"My Timesheet",((TextView)a.findViewById(R.id.overlayTimekeeping)).getText().toString());
        }));
        scenario.onActivity(a->a.navigate(R.id.analyticsFragment));
        StaffUiTest.waitVisible(R.id.analyticsTitle);
        StaffUiTest.retry(()->scenario.onActivity(a->assertGrid(a.findViewById(R.id.analyticsSummaries))));
        scenario.onActivity(a->a.navigate(R.id.timekeepingFragment));
        StaffUiTest.waitVisible(R.id.tkTitle);
      } finally {
        app.graph().executor.submit(()->app.graph().identity.clear()).get(5,TimeUnit.SECONDS);
      }
    }
  }
  private static void assertGrid(GridLayout grid) {
    assertNotNull(grid);assertEquals(4,grid.getChildCount());
    for(int i=0;i<4;i++)assertTrue(grid.getChildAt(i).getWidth()>0);
  }
}
