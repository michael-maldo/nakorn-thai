package au.com.nakornthai.staffapp;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.printing.data.PrinterConfigurationRepository;
import au.com.nakornthai.staffapp.printing.discovery.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PrinterUiTest {
  ActivityScenario<MainActivity> scenario;
  FixtureApplication app;

  void login(String user) {
    app =
        (FixtureApplication)
            InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    new PrinterConfigurationRepository(app).save(Collections.emptyList());
    scenario = ActivityScenario.launch(MainActivity.class);
    StaffUiTest.waitVisible(R.id.signIn);
    onView(withId(R.id.username)).perform(replaceText(user));
    onView(withId(R.id.password))
        .perform(
            replaceText("fixture-password"),
            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.signIn)).perform(click());
    StaffUiTest.waitVisible(R.id.refresh);
  }

  @After
  public void close() throws Exception {
    if (app != null) {
      app.graph()
          .executor
          .submit(() -> app.graph().identity.clear())
          .get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    if (scenario != null) scenario.close();
  }

  @Test
  public void adminCanSaveUnknownPrinterAndRestoreScreen() {
    login("fixture-admin");
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.managePrinters)).perform(click());
    StaffUiTest.waitVisible(R.id.addPrinter);
    onView(withId(R.id.addPrinter)).perform(click());
    onView(withId(R.id.printerName)).perform(scrollTo(), replaceText("Fixture kitchen"));
    onView(withId(R.id.printerEndpoint))
        .perform(
            scrollTo(),
            replaceText("printer.invalid"),
            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    onView(withId(R.id.savePrinter)).perform(scrollTo(), click());
    StaffUiTest.retry(
        () -> Assert.assertEquals(1, new PrinterConfigurationRepository(app).load().size()));
    scenario.recreate();
    StaffUiTest.waitVisible(R.id.addPrinter);
    onView(withText("Print Test Docket")).check(matches(not(isEnabled())));
    onView(withId(R.id.footer)).check(matches(isDisplayed()));
    onView(withId(R.id.home)).perform(click());
    StaffUiTest.waitVisible(R.id.refresh);
  }

  @Test
  public void fohCannotOpenPrintersEvenByDirectNavigation() {
    login("fixture-foh");
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.managePrinters)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    androidx.test.espresso.Espresso.pressBack();
    scenario.onActivity(a -> a.navigate(R.id.printerManagementFragment));
    scenario.recreate();
    StaffUiTest.waitVisible(R.id.refresh);
  }

  @Test
  public void discoveryCanBeCancelledWithoutHardware() {
    login("fixture-admin");
    scenario.onActivity(
        a -> {
          PrinterDiscovery discovery = new NetworkPrinterDiscovery(a);
          discovery.start(d -> {}, s -> {});
          discovery.stop();
          discovery.stop();
        });
  }

  @Test
  public void bohDirectNavControllerEntryIsGuardedAfterRecreation() {
    login("fixture-boh");
    scenario.onActivity(
        a -> {
          androidx.navigation.fragment.NavHostFragment host =
              (androidx.navigation.fragment.NavHostFragment)
                  a.getSupportFragmentManager().findFragmentById(R.id.navHost);
          host.getNavController().navigate(R.id.printerManagementFragment);
        });
    StaffUiTest.waitVisible(R.id.refresh);
    scenario.recreate();
    StaffUiTest.waitVisible(R.id.refresh);
  }

  @Test
  public void unsavedAdminEditorSurvivesActivityRecreation() {
    login("fixture-admin");
    onView(withId(R.id.manage)).perform(click());
    onView(withId(R.id.managePrinters)).perform(click());
    StaffUiTest.waitVisible(R.id.addPrinter);
    onView(withId(R.id.addPrinter)).perform(click());
    onView(withId(R.id.printerName))
        .perform(
            scrollTo(),
            replaceText("Retained draft"),
            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    scenario.recreate();
    onView(withId(R.id.printerName)).perform(scrollTo()).check(matches(withText("Retained draft")));
  }
}
