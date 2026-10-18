package au.com.nakornthai.staffapp.shared.dashboard;

import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.ordering.monitoring.BoardState;
import com.google.android.material.card.MaterialCardView;
import java.util.function.IntConsumer;

/** A shared four-metric component for the board and live queue analytics. */
public final class QueueSummaries {
  public static final int[] LABELS={R.string.dashboard_pending,R.string.dashboard_accepted,R.string.dashboard_preparing,R.string.dashboard_ready};
  public static final int[] SURFACES={R.drawable.dashboard_pending,R.drawable.dashboard_accepted,R.drawable.dashboard_preparing,R.drawable.dashboard_ready};
  private final GridLayout grid;
  private final MaterialCardView[] cards=new MaterialCardView[4];
  private final TextView[] values=new TextView[4];
  private int columnCount;
  public QueueSummaries(GridLayout grid, IntConsumer select) {
    this.grid=grid;grid.setColumnCount(2);
    for(int i=0;i<4;i++) {
      int index=i;
      MaterialCardView card=(MaterialCardView)LayoutInflater.from(grid.getContext()).inflate(R.layout.item_dashboard_metric,grid,false);
      cards[i]=card; values[i]=card.findViewById(R.id.metricCount);
      ((TextView)card.findViewById(R.id.metricLabel)).setText(LABELS[i]);
      card.setClickable(select!=null);card.setFocusable(select!=null);
      if(select!=null)card.setOnClickListener(v->select.accept(index));
      card.setTag("queue-summary:"+DashboardPresentation.STATES[i]);grid.addView(card);
    }
    grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->adapt());
    adapt();
  }
  private void adapt() {
    float width=grid.getWidth()/grid.getResources().getDisplayMetrics().density;
    if(width<=0)return;
    int next=DashboardPresentation.summaryColumns(width,grid.getResources().getConfiguration().fontScale);
    if(next==columnCount)return;
    columnCount=next;grid.setColumnCount(next);
    for(int i=0;i<4;i++) {
      GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(i/next),GridLayout.spec(i%next,Gravity.FILL,1f));
      p.width=0;p.height=-2;
      int margin=(int)(4*grid.getResources().getDisplayMetrics().density);p.setMargins(margin,margin,margin,margin);
      cards[i].setLayoutParams(p);
    }
  }
  public void render(BoardState state) {
    for(int i=0;i<4;i++) {
      int count=state.column(DashboardPresentation.STATES[i]).size();values[i].setText(String.valueOf(count));
      cards[i].setContentDescription(grid.getContext().getString(R.string.dashboard_metric,grid.getContext().getString(LABELS[i]),count));
    }
  }
  public void selected(int selected) {
    for(int i=0;i<4;i++) {
      cards[i].setSelected(i==selected);
      cards[i].setStrokeColor(ContextCompat.getColor(grid.getContext(),i==selected?R.color.staff_accent:R.color.staff_line));
      cards[i].setCardBackgroundColor(ContextCompat.getColor(grid.getContext(),i==selected?R.color.staff_selected:R.color.staff_surface));
    }
  }
}
