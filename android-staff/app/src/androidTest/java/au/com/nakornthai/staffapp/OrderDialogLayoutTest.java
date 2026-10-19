package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OrderDialogLayoutTest {
  @Test public void enlargedTextShortDialogKeepsConfirmAndDismissReachable() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
      Configuration config = new Configuration(base.getResources().getConfiguration()); config.fontScale = 2f;
      Context context = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_NakornStaff);
      float density = context.getResources().getDisplayMetrics().density;
      int width = (int)(320 * density), height = (int)(180 * density);
      for (boolean retained : new boolean[] {false, true}) {
        ScrollView scroll = (ScrollView)LayoutInflater.from(context).inflate(R.layout.dialog_order_action, null);
        scroll.findViewById(R.id.discardDraft).setVisibility(retained ? View.VISIBLE : View.GONE);
        ((TextView)scroll.findViewById(R.id.actionDescription)).setText("Move order #TEST0001 to Preparing? Review this order before confirming.");
        ((TextView)scroll.findViewById(R.id.actionError)).setText("Order changed. Review the latest server state.");
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        scroll.layout(0,0,width,height);
        assertTrue(scroll.canScrollVertically(1));
        for (int id : new int[] {R.id.confirmAction, R.id.closeAction}) {
          View button = scroll.findViewById(id);
          assertTrue(button.getHeight() >= 48 * density);
          Rect rect = new Rect(0,0,button.getWidth(),button.getHeight());
          scroll.offsetDescendantRectToMyCoords(button,rect);
          scroll.scrollTo(0,Math.max(0,rect.bottom-height));
          assertTrue(rect.bottom-scroll.getScrollY() <= height);
          assertTrue(rect.top-scroll.getScrollY() >= 0);
        }
      }
    });
  }
}
