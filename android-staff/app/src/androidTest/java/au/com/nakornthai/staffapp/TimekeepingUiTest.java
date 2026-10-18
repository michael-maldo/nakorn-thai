package au.com.nakornthai.staffapp;

import android.content.pm.ActivityInfo;
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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class TimekeepingUiTest {
  FixtureApplication app;
  ActivityScenario<MainActivity> scenario;
  @Before public void setup() {
    app=(FixtureApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.offline=false;app.expired=false;app.status="NEW";app.resetAttendance();
    scenario=ActivityScenario.launch(MainActivity.class);
  }
  @After public void close() throws Exception {
    app.graph().executor.submit(()->app.graph().identity.clear()).get(5,TimeUnit.SECONDS);scenario.close();
  }
  void login(String role) {
    StaffUiTest.waitVisible(R.id.signIn);
    onView(withId(R.id.username)).perform(replaceText("fixture-"+role));
    onView(withId(R.id.password)).perform(replaceText("fixture-password"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.signIn)).perform(click());StaffUiTest.waitVisible(R.id.refresh);
  }
  void open(String title) {
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.manageTimekeeping)).check(matches(withText(title))).perform(scrollTo(),click());
    StaffUiTest.retry(()->onView(withId(R.id.tkRefresh)).check(matches(isEnabled())));
    onView(withId(R.id.tkRefresh)).perform(scrollTo());
    StaffUiTest.retry(()->onView(withId(R.id.tkMessage)).check(matches(withText(containsString("Attendance refreshed")))));
  }
  void selectStaff() {
    onView(withId(R.id.tkStaff)).perform(scrollTo(),click());onData(anything()).atPosition(1).perform(click());
  }
  void manual() {
    selectStaff();onView(withId(R.id.tkManual)).perform(scrollTo(),click());
    onView(withId(R.id.tkStart)).perform(scrollTo(),replaceText("2026-10-17T22:00:00+11:00"));
    onView(withId(R.id.tkEnd)).perform(scrollTo(),replaceText("2026-10-18T02:00:00+11:00"));
    onView(withId(R.id.tkReason)).perform(scrollTo(),replaceText("Historical overnight shift"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.tkReview)).perform(scrollTo(),click());
    assertEquals(0,app.attendanceMutations.get());
    onView(withId(R.id.tkConfirm)).perform(scrollTo(),click());waitSaved(1);
  }
  void waitSaved(int count) {
    StaffUiTest.retry(()->assertEquals(count,app.attendanceMutations.get()));
    StaffUiTest.retry(()->onView(withId(R.id.tkMessage)).check(matches(withText(containsString("Confirmed by server")))));
    StaffUiTest.retry(()->onView(withId(R.id.tkTimeIn)).check(matches(isEnabled())));
  }
  @Test public void adminLiveActionsConfirmOnceAndUseServerTimes() {
    login("admin");open("Timekeeping");selectStaff();
    onView(withId(R.id.tkTimeIn)).perform(scrollTo(),click());assertEquals(0,app.attendanceMutations.get());
    onView(withId(R.id.tkConfirm)).perform(scrollTo(),click());waitSaved(1);
    onView(withId(R.id.tkMessage)).check(matches(withText(containsString("16:00:00 +11:00"))));
    onView(withId(R.id.tkTimeOut)).perform(scrollTo(),click());onView(withId(R.id.tkConfirm)).perform(scrollTo(),click());waitSaved(2);
    onView(withId(R.id.tkSummary)).check(matches(withText(containsString("1 h completed"))));
  }
  @Test public void manualOvernightCorrectionRequiresReasonAndAuditShowsBothVersions() {
    login("admin");open("Timekeeping");manual();
    onView(withId(R.id.tkSummary)).check(matches(withText(containsString("4 h completed"))));
    onView(withTagValue(is((Object)"correct:fixture-entry"))).perform(scrollTo(),click());
    onView(withId(R.id.tkReview)).perform(scrollTo(),click());assertEquals(1,app.attendanceMutations.get());
    onView(withId(R.id.tkMessage)).check(matches(withText(containsString("audit reason"))));
    onView(withId(R.id.tkReason)).perform(scrollTo(),replaceText("Correct recorded minutes"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.tkReview)).perform(scrollTo(),click());onView(withId(R.id.tkConfirm)).perform(scrollTo(),click());waitSaved(2);
    onView(withTagValue(is((Object)"audit:fixture-entry"))).perform(scrollTo(),click());
    StaffUiTest.retry(()->onView(withId(R.id.tkAudit)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
    onView(withId(R.id.tkAuditEntries)).check(matches(hasDescendant(withText(containsString("Correct recorded minutes")))));
  }
  @Test public void staffTimesheetHasNoAdminControlsOrAuditActions() {
    login("foh");open("My Timesheet");
    onView(withId(R.id.tkAdmin)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    onView(withId(R.id.tkEditor)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    onView(withText("Correct attendance")).check(doesNotExist());
    onView(withText("View audit history")).check(doesNotExist());assertEquals(0,app.attendanceMutations.get());
    onView(withId(R.id.tkEntries)).check(matches(hasDescendant(withText("No attendance matches these dates."))));
  }
  @Test public void kitchenStaffAlsoNavigateToReadOnlyTimesheet() {
    login("boh");open("My Timesheet");onView(withId(R.id.tkAdmin)).check(matches(withEffectiveVisibility(Visibility.GONE)));
  }
  @Test public void conflictRequiresRefreshAndNeverShowsSuccess() {
    login("admin");open("Timekeeping");selectStaff();app.attendanceWriteFailure=409;
    onView(withId(R.id.tkTimeIn)).perform(scrollTo(),click());onView(withId(R.id.tkConfirm)).perform(scrollTo(),click());
    StaffUiTest.retry(()->onView(withId(R.id.tkMessage)).check(matches(withText(containsString("Fixture attendance conflict")))));
    assertEquals(1,app.attendanceMutations.get());onView(withId(R.id.tkTimeIn)).check(matches(not(isEnabled())));
    app.attendanceWriteFailure=0;onView(withId(R.id.tkRefresh)).perform(scrollTo(),click());
    StaffUiTest.retry(()->onView(withId(R.id.tkTimeIn)).check(matches(isEnabled())));
    assertEquals(1,app.attendanceMutations.get());
  }
  @Test public void forbiddenMutationIsVisibleAndNotRetried() {
    login("admin");open("Timekeeping");selectStaff();app.attendanceWriteFailure=403;
    onView(withId(R.id.tkTimeOut)).perform(scrollTo(),click());onView(withId(R.id.tkConfirm)).perform(scrollTo(),click());
    StaffUiTest.retry(()->onView(withId(R.id.tkTimeOut)).check(matches(not(isEnabled()))));
    assertEquals(1,app.attendanceMutations.get());
  }
  @Test public void draftSurvivesRecreationAndLandscapeWhileKeyboardControlsStayReachable() {
    login("admin");open("Timekeeping");selectStaff();onView(withId(R.id.tkManual)).perform(scrollTo(),click());
    onView(withId(R.id.tkReason)).perform(scrollTo(),replaceText("Retained reason"));
    // The normal activity root owns IME insets; this form must scroll with the keyboard open.
    onView(withId(R.id.tkReview)).perform(scrollTo()).check(matches(isDisplayed()));androidx.test.espresso.Espresso.closeSoftKeyboard();
    scenario.recreate();onView(withId(R.id.tkReason)).perform(scrollTo()).check(matches(withText("Retained reason")));
    scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    StaffUiTest.retry(()->onView(withId(R.id.tkCancel)).perform(scrollTo()).check(matches(isDisplayed())));
    assertEquals(0,app.attendanceMutations.get());
  }
}
