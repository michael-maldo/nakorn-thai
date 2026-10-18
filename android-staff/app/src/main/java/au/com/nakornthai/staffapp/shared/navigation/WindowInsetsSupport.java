package au.com.nakornthai.staffapp.shared.navigation;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** One inset owner per window. Children (including Material navigation) receive no insets. */
public final class WindowInsetsSupport {
  private WindowInsetsSupport() {}

  public static void apply(View container) {
    int left = container.getPaddingLeft(), top = container.getPaddingTop();
    int right = container.getPaddingRight(), bottom = container.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(container, (view, insets) -> {
      // systemBars includes freeform caption bars. The union uses max, not sum, for IME/nav.
      Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
          | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
      view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
      return WindowInsetsCompat.CONSUMED;
    });
    ViewCompat.requestApplyInsets(container);
  }
}
