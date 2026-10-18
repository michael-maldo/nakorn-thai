package au.com.nakornthai.staffapp;

import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.app.MainActivity;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardUiTest {
  ActivityScenario<MainActivity> scenario;
  FixtureApplication app;
  @Before public void start() {
    app=(FixtureApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.status="NEW";app.offline=false;app.expired=false;
    scenario=ActivityScenario.launch(MainActivity.class);
    StaffUiTest.waitVisible(R.id.signIn);
    onView(withId(R.id.username)).perform(replaceText("fixture-foh"));
    onView(withId(R.id.password)).perform(replaceText("fixture-password"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.signIn)).perform(click());StaffUiTest.waitText("#TEST0001");
  }
  @After public void stop() throws Exception {
    app.graph().executor.submit(()->app.graph().identity.clear()).get(5,TimeUnit.SECONDS);scenario.close();
  }
  @Test public void unifiedNavigationUsesRoleLabelsAndProtectsPrinters() {
    onView(withId(R.id.dashboardTopBar)).check(matches(isDisplayed()));
    scenario.onActivity(a->{
      assertEquals("My Timesheet",((TextView)a.findViewById(R.id.overlayTimekeeping)).getText().toString());
      assertEquals(View.GONE,a.findViewById(R.id.overlayPrinters).getVisibility());
      assertEquals(View.GONE,a.findViewById(R.id.sidebarPrinters).getVisibility());
      a.navigate(R.id.printerManagementFragment);
    });
    StaffUiTest.waitVisible(R.id.refresh);
    scenario.onActivity(a->a.navigate(R.id.timekeepingFragment));
    StaffUiTest.waitVisible(R.id.tkTitle);onView(withId(R.id.tkTitle)).check(matches(withText("My Timesheet")));
  }
  @Test public void analyticsUsesExistingLiveQueueAndReservationsAreHonestAboutCapability() {
    scenario.onActivity(a->a.navigate(R.id.analyticsFragment));StaffUiTest.waitVisible(R.id.analyticsTitle);
    onView(withTagValue(is((Object)"queue-summary:NEW"))).check(matches(hasDescendant(allOf(withId(R.id.metricCount),withText("1")))));
    onView(withId(R.id.analyticsStatus)).check(matches(withText(R.string.dashboard_active)));
    scenario.onActivity(a->a.navigate(R.id.reservationsFragment));StaffUiTest.waitVisible(R.id.reservationsTitle);
    onView(withText(R.string.dashboard_reservations_status)).check(matches(isDisplayed()));
    onView(withId(R.id.reservationOrders)).perform(scrollTo(),click());StaffUiTest.waitText("#TEST0001");
  }
  @Test public void alertsCanBeAcknowledgedFromAnyScreenWithoutAcceptingAnOrder() {
    int mutations=app.mutations.get();scenario.onActivity(a->a.navigate(R.id.reservationsFragment));
    onView(withId(R.id.orderAlerts)).perform(click());
    onView(withText(R.string.label_acknowledge_alert)).inRoot(isDialog()).perform(click());
    StaffUiTest.retry(()->assertEquals(0,app.graph().monitor.snapshot().unacknowledged));
    assertEquals("NEW",app.status);assertEquals(mutations,app.mutations.get());
  }
  @Test public void navigatingBetweenWorkspacesKeepsSameMonitorAndPolling() {
    Object monitor=app.graph().monitor;int before=app.reads.get();
    scenario.onActivity(a->a.navigate(R.id.analyticsFragment));
    onView(withId(R.id.analyticsRefresh)).perform(scrollTo(),click());
    StaffUiTest.retry(()->assertTrue(app.reads.get()>before));assertSame(monitor,app.graph().monitor);
    scenario.onActivity(a->a.navigate(R.id.reservationsFragment));
    scenario.recreate();StaffUiTest.waitVisible(R.id.reservationsTitle);assertSame(monitor,app.graph().monitor);
    onView(withId(R.id.home)).perform(click());StaffUiTest.waitText("#TEST0001");
  }
}
