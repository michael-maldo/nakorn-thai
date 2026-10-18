package au.com.nakornthai.staffapp.shared.dashboard;

/** Decisions use the available safe viewport and font scaling, never tablet model/orientation. */
public final class DashboardPresentation {
  private DashboardPresentation() {}
  public static final String[] STATES = {"NEW", "ACCEPTED", "PREPARING", "READY"};
  public static boolean permanentNavigation(float width, float font) { return width / Math.max(1f,font) >= 840; }
  public static boolean wideQueues(float width, float font) { return width / Math.max(1f,font) >= 960; }
  public static boolean compact(float height, float font) { return height / Math.max(1f,font) < 420; }
  public static int summaryColumns(float width, float font) { return width / Math.max(1f,font) >= 600 ? 4 : 2; }
  public static int statusIndex(String status) {
    for (int i=0;i<STATES.length;i++) if (STATES[i].equals(status)) return i;
    return -1;
  }
}
