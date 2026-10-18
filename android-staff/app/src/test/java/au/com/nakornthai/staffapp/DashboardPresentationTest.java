package au.com.nakornthai.staffapp;

import au.com.nakornthai.staffapp.shared.dashboard.DashboardPresentation;
import org.junit.Test;
import static org.junit.Assert.*;

public class DashboardPresentationTest {
  @Test public void navigationUsesSafeWindowWidthAndFontScaling() {
    assertTrue(DashboardPresentation.permanentNavigation(1280,1));
    assertFalse(DashboardPresentation.permanentNavigation(700,1));
    assertFalse(DashboardPresentation.permanentNavigation(1280,2));
  }
  @Test public void narrowAndLargeFontQueuesStayReadable() {
    assertTrue(DashboardPresentation.wideQueues(1100,1));
    assertFalse(DashboardPresentation.wideQueues(700,1));
    assertFalse(DashboardPresentation.wideQueues(1100,2));
  }
  @Test public void shortWindowCompactsChromeAndCollapsesOnlyBoardHeader() {
    assertTrue(DashboardPresentation.compact(240,1));assertTrue(DashboardPresentation.compact(600,2));
    assertFalse(DashboardPresentation.compact(800,1));
  }
  @Test public void summaryGridWrapsWithoutDiscardingAnyStatus() {
    assertEquals(4,DashboardPresentation.summaryColumns(1000,1));
    assertEquals(2,DashboardPresentation.summaryColumns(320,1));
    assertEquals(2,DashboardPresentation.summaryColumns(1000,2));
    assertEquals(0,DashboardPresentation.statusIndex("NEW"));assertEquals(3,DashboardPresentation.statusIndex("READY"));
    assertEquals(-1,DashboardPresentation.statusIndex("COMPLETED"));
  }
}
