package au.com.nakornthai.staffapp;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

import android.content.pm.ActivityInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.app.MainActivity;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StaffUiTest {
  ActivityScenario<MainActivity> scenario;
  FixtureApplication app;

  @Before
  public void setup() {
    app =
        (FixtureApplication)
            InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.status = "NEW";
    app.offline = false;
    app.expired = false;
    scenario = ActivityScenario.launch(MainActivity.class);
    waitVisible(R.id.signIn);
    onView(withId(R.id.username)).perform(replaceText("fixture-foh"));
    onView(withId(R.id.password))
        .perform(
            replaceText("fixture-password"),
            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.signIn)).perform(click());
    waitVisible(R.id.refresh);
    waitText("#TEST0001");
  }

  @After
  public void close() throws Exception {
    if (app.graph().identity.current() != null) {
      java.util.concurrent.Future<?> done =
          app.graph().executor.submit(() -> app.graph().identity.clear());
      done.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    scenario.close();
  }

  static void waitVisible(int id) {
    retry(() -> onView(withId(id)).check(matches(isDisplayed())));
  }

  static void waitText(String text) {
    retry(() -> onView(withText(text)).check(matches(isDisplayed())));
  }

  static void retry(Runnable assertion) {
    AssertionError last = null;
    for (int i = 0; i < 80; i++) {
      try {
        assertion.run();
        return;
      } catch (AssertionError | androidx.test.espresso.NoMatchingViewException failure) {
        last = new AssertionError(failure);
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
      }
    }
    throw last;
  }

  @Test
  public void footerAndManageShareDestinationsWithoutRestartingBoard() {
    onView(withId(R.id.analyticsAction)).perform(click());
    waitVisible(R.id.analyticsTitle);
    onView(withId(R.id.home)).check(matches(not(isSelected())));
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.manageReservations)).perform(scrollTo(), click());
    waitVisible(R.id.reservationsTitle);
    onView(withId(R.id.footer)).check(matches(isDisplayed()));
    onView(withId(R.id.home)).perform(click());
    waitText("#TEST0001");
  }

  @Test
  public void sidebarAndOrientationPreserveNavigation() {
    scenario.onActivity(
        activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    waitVisible(R.id.footer);
    scenario.recreate();
    waitVisible(R.id.footer);
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.manageAnalytics)).perform(scrollTo(), click());
    scenario.recreate();
    waitVisible(R.id.analyticsTitle);
    scenario.onActivity(
        activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    waitVisible(R.id.analyticsTitle);
    onView(withId(R.id.footer)).check(matches(isDisplayed()));
  }

  @Test
  public void acceptValidatesThenReloads204WithoutDuplicateMutation() {
    onView(withText("#TEST0001")).perform(click());
    waitText("Accept order");
    onView(withText("Accept order")).perform(click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.actionInput))
        .perform(replaceText("4"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.confirmAction)).perform(click());
    waitText("Pickup estimate must be 5–180 whole minutes.");
    int before = app.mutations.get();
    onView(withId(R.id.actionInput))
        .perform(replaceText("25"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.confirmAction)).perform(click());
    retry(() -> org.junit.Assert.assertEquals(before + 1, app.mutations.get()));
  }

  @Test public void ordinaryCancelDismissesWithoutMutationOrDraftRestoration() {
    onView(withText("#TEST0001")).perform(click());
    onView(withText("Accept order")).perform(click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.discardDraft)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    onView(withId(R.id.closeAction)).check(matches(withText("Cancel")));
    onView(withId(R.id.confirmAction)).check(matches(withText("Confirm")));
    onView(withId(R.id.actionInput)).perform(replaceText("55"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    int before = app.mutations.get();
    onView(withId(R.id.closeAction)).perform(scrollTo(), click());
    org.junit.Assert.assertEquals(before, app.mutations.get());
    org.junit.Assert.assertEquals("NEW", app.status);
    onView(withText("Accept order")).perform(click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.actionInput)).check(matches(withText("20")));
    onView(withId(R.id.actionError)).check(matches(withText("")));
  }

  @Test public void explicitCancellationIsSeparateAndKeepOrderDoesNotSubmit() {
    onView(withText("#TEST0001")).perform(click());
    onView(withId(R.id.cancelOrderAction)).perform(scrollTo(), click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.confirmAction)).check(matches(withText("Cancel Order")));
    onView(withId(R.id.closeAction)).check(matches(withText("Keep Order")));
    onView(withId(R.id.actionInput)).perform(replaceText("Sold out"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    int before = app.mutations.get();
    onView(withId(R.id.closeAction)).perform(scrollTo(), click());
    org.junit.Assert.assertEquals(before, app.mutations.get());
    org.junit.Assert.assertEquals("NEW", app.status);
    onView(withId(R.id.cancelOrderAction)).perform(scrollTo(), click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.actionInput)).check(matches(withText("Sold out")));
    onView(withId(R.id.actionError)).check(matches(withText("")));
    onView(withId(R.id.confirmAction)).perform(scrollTo(), click());
    retry(() -> org.junit.Assert.assertEquals("CANCELLED", app.status));
    org.junit.Assert.assertEquals(before + 1, app.mutations.get());
  }

  @Test public void validationFeedbackDoesNotLeakIntoNewRoutineDialog() {
    onView(withText("#TEST0001")).perform(click());
    onView(withText("Accept order")).perform(click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.actionInput)).perform(replaceText("4"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.confirmAction)).perform(scrollTo(), click());
    waitText("Pickup estimate must be 5–180 whole minutes.");
    onView(withId(R.id.closeAction)).perform(scrollTo(), click());
    onView(withText("Accept order")).perform(click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.actionError)).check(matches(withText("")));
  }

  @Test public void serverSuccessDoesNotPopulateNextOperationDialog() {
    onView(withText("#TEST0001")).perform(click());
    onView(withText("Accept order")).perform(click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.confirmAction)).perform(scrollTo(), click());
    retry(() -> onView(withId(R.id.detailBody)).check(matches(withText(containsString("ACCEPTED")))));
    onView(withId(R.id.cancelOrderAction)).perform(scrollTo(), click());
    waitVisible(R.id.actionInput);
    onView(withId(R.id.actionError)).check(matches(withText("")));
    onView(withId(R.id.closeAction)).check(matches(withText("Keep Order")));
  }

  @Test
  public void manageSignOutHidesFooter() {
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.manageSignOut)).perform(scrollTo(), click());
    waitVisible(R.id.signIn);
    onView(withId(R.id.footer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
  }

  @Test
  public void expiryReturnsToLogin() {
    app.expired = true;
    onView(withId(R.id.refresh)).perform(click());
    waitVisible(R.id.signIn);
    onView(withId(R.id.loginMessage)).check(matches(withText(containsString("sign in"))));
  }

  @Test
  public void sidebarToggleOrOverlayAndFooterDoNotOverlapContent() {
    java.util.concurrent.atomic.AtomicBoolean permanent =
        new java.util.concurrent.atomic.AtomicBoolean();
    scenario.onActivity(
        activity -> {
          permanent.set(
              activity.findViewById(R.id.sidebar).getVisibility() == android.view.View.VISIBLE);
        });
    if (permanent.get()) {
      onView(withId(R.id.sidebarToggle)).perform(click());
      String description[] = new String[1];
      scenario.onActivity(
          activity ->
              description[0] =
                  activity.findViewById(R.id.sidebarToggle).getContentDescription().toString());
      scenario.recreate();
      onView(withId(R.id.sidebarToggle)).check(matches(withContentDescription(description[0])));
      onView(withId(R.id.sidebarReservations)).perform(click());
    } else {
      onView(withId(R.id.drawerToggle)).perform(click());
      onView(withId(R.id.overlayReservations)).perform(click());
    }
    waitVisible(R.id.reservationsTitle);
    scenario.onActivity(
        activity -> {
          android.graphics.Rect content = new android.graphics.Rect(),
              footer = new android.graphics.Rect();
          activity.findViewById(R.id.navHost).getGlobalVisibleRect(content);
          activity.findViewById(R.id.footer).getGlobalVisibleRect(footer);
          org.junit.Assert.assertTrue(content.bottom <= footer.top);
        });
  }

  @Test
  public void drawerAndContentStayInsideWindowInsetsAfterRotation() {
    for (int orientation : new int[] {ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}) {
      scenario.onActivity(a -> a.setRequestedOrientation(orientation));
      waitVisible(R.id.footer);
      retry(() -> scenario.onActivity(activity -> {
        android.view.View root = activity.findViewById(R.id.windowContent);
        android.view.View drawer = activity.findViewById(R.id.drawer);
        androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(root);
        org.junit.Assert.assertNotNull(insets);
        androidx.core.graphics.Insets safe = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                | androidx.core.view.WindowInsetsCompat.Type.ime());
        org.junit.Assert.assertEquals(safe.left, drawer.getLeft());
        org.junit.Assert.assertEquals(safe.top, drawer.getTop());
        org.junit.Assert.assertEquals(root.getWidth() - safe.right, drawer.getRight());
        org.junit.Assert.assertEquals(root.getHeight() - safe.bottom, drawer.getBottom());
      }));
    }
  }

  @Test
  public void boardColumnsAndCenteredFooterRespectAvailableWindow() {
    retry(
        () ->
            scenario.onActivity(
                activity -> {
                  android.view.View workspace = activity.findViewById(R.id.columns);
                  int[] lists = {
                    R.id.orderListNew,
                    R.id.orderListAccepted,
                    R.id.orderListPreparing,
                    R.id.orderListReady
                  };
                  boolean wide =
                      au.com.nakornthai.staffapp.shared.dashboard.DashboardPresentation.wideQueues(workspace.getWidth() / activity.getResources().getDisplayMetrics().density, activity.getResources().getConfiguration().fontScale);
                  int visible = 0, previousRight = -1, firstWidth = -1, firstTop = -1;
                  for (int id : lists) {
                    android.view.View list = activity.findViewById(id);
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    if (list.getGlobalVisibleRect(bounds)) {
                      visible++;
                      if (wide) {
                        if (firstWidth < 0) {
                          firstWidth = bounds.width();
                          firstTop = bounds.top;
                        }
                        org.junit.Assert.assertTrue(Math.abs(firstWidth - bounds.width()) <= 1);
                        org.junit.Assert.assertEquals(firstTop, bounds.top);
                        org.junit.Assert.assertTrue(bounds.left > previousRight);
                        previousRight = bounds.right;
                      }
                    }
                  }
                  org.junit.Assert.assertEquals(wide ? 4 : 1, visible);
                  android.view.View footer = activity.findViewById(R.id.footer),
                      parent = (android.view.View) footer.getParent();
                  org.junit.Assert.assertTrue(
                      footer.getWidth()
                          <= activity
                              .getResources()
                              .getDimensionPixelSize(R.dimen.footer_content_max_width));
                  org.junit.Assert.assertTrue(
                      Math.abs(footer.getLeft() * 2 + footer.getWidth() - parent.getWidth()) <= 1);
                  org.junit.Assert.assertTrue(
                      activity.findViewById(R.id.refresh).getHeight()
                          >= 48 * activity.getResources().getDisplayMetrics().density);
                }));
  }
}
