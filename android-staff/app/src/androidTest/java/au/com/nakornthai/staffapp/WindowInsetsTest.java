package au.com.nakornthai.staffapp;

import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.com.nakornthai.staffapp.shared.navigation.WindowInsetsSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class WindowInsetsTest {
  @Test
  public void enlargedTextLayoutsKeepLastActionsReachableInReducedViewport() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      android.content.Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
      android.content.res.Configuration config = new android.content.res.Configuration(
          base.getResources().getConfiguration());
      config.fontScale = 2f;
      android.content.Context context = new android.view.ContextThemeWrapper(
          base.createConfigurationContext(config), R.style.Theme_NakornStaff);
      int width = (int) (320 * context.getResources().getDisplayMetrics().density);
      int height = (int) (180 * context.getResources().getDisplayMetrics().density);
      for (int layout : new int[] {R.layout.sheet_manage, R.layout.fragment_printers}) {
        android.view.ViewGroup scroll = (android.view.ViewGroup)
            android.view.LayoutInflater.from(context).inflate(layout, null);
        if (layout == R.layout.fragment_printers)
          scroll.findViewById(R.id.printerEditor).setVisibility(android.view.View.VISIBLE);
        scroll.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, width, height);
        assertTrue(scroll.canScrollVertically(1));
        android.view.View action = scroll.findViewById(
            layout == R.layout.sheet_manage ? R.id.manageSignOut : R.id.cancelPrinter);
        android.graphics.Rect rect = new android.graphics.Rect(0, 0, action.getWidth(), action.getHeight());
        scroll.offsetDescendantRectToMyCoords(action, rect);
        scroll.scrollTo(0, Math.max(0, rect.bottom - height));
        assertTrue(scroll.getScrollY() > 0);
        assertTrue(rect.bottom - scroll.getScrollY() <= height);
        assertTrue(rect.top - scroll.getScrollY() >= 0);
      }
    });
  }

  @Test
  @androidx.test.filters.SdkSuppress(minSdkVersion = 30)
  public void safeViewportUsesUnionAndDoesNotAccumulateOrDispatchToChildren() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      FrameLayout root = new FrameLayout(InstrumentationRegistry.getInstrumentation().getTargetContext());
      FrameLayout child = new FrameLayout(root.getContext());
      root.addView(child);
      root.setPadding(1, 2, 3, 4);
      WindowInsetsSupport.apply(root);
      // Gesture navigation, button navigation/taskbar, then resized window with side bars.
      for (Insets bars : new Insets[] {Insets.of(0, 24, 0, 16), Insets.of(0, 24, 0, 72),
          Insets.of(48, 32, 0, 0)}) {
        for (int keyboard : new int[] {0, 160, 0}) {
          WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
              .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
              .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 40, 12, 0))
              .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard)).build();
          for (int repeat = 0; repeat < 2; repeat++) {
            assertTrue(ViewCompat.dispatchApplyWindowInsets(root, insets).isConsumed());
            assertEquals(1 + bars.left, root.getPaddingLeft());
            assertEquals(2 + Math.max(bars.top, 40), root.getPaddingTop());
            assertEquals(3 + Math.max(bars.right, 12), root.getPaddingRight());
            assertEquals(4 + Math.max(bars.bottom, keyboard), root.getPaddingBottom());
            assertEquals(0, child.getPaddingBottom());
          }
        }
      }
    });
  }
}
