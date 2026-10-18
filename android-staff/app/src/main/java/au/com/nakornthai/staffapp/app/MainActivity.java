package au.com.nakornthai.staffapp.app;

import android.media.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.*;
import androidx.navigation.fragment.NavHostFragment;
import au.com.nakornthai.staffapp.R;
import au.com.nakornthai.staffapp.databinding.ActivityMainBinding;
import au.com.nakornthai.staffapp.identity.ui.AuthViewModel;
import au.com.nakornthai.staffapp.ordering.ui.OrdersViewModel;
import au.com.nakornthai.staffapp.shared.navigation.ManageSheet;
import au.com.nakornthai.staffapp.shared.navigation.WindowInsetsSupport;
import com.google.android.material.button.MaterialButton;

public final class MainActivity extends AppCompatActivity {
  private ActivityMainBinding binding;
  private NavController navigation;
  private AuthViewModel auth;
  private OrdersViewModel orders;
  private boolean expanded = true, authenticated, narrow, compact;
  private int availableWidth;
  private int unacknowledged;
  private long heard;
  private ToneGenerator tone;

  public AppGraph graph() {
    return ((StaffApplication) getApplication()).graph();
  }

  public StaffViewModelFactory factory() {
    return new StaffViewModelFactory(graph());
  }

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    expanded =
        saved == null
            ? getPreferences(MODE_PRIVATE).getBoolean("sidebarExpanded", true)
            : saved.getBoolean("expanded", true);
    heard = graph().heardAlertSequence;
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    WindowInsetsSupport.apply(binding.getRoot());
    navigation =
        ((NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHost))
            .getNavController();
    auth = new ViewModelProvider(this, factory()).get(AuthViewModel.class);
    orders = new ViewModelProvider(this, factory()).get(OrdersViewModel.class);
    binding.sidebarToggle.setOnClickListener(
        v -> {
          expanded = !expanded;
          getPreferences(MODE_PRIVATE).edit().putBoolean("sidebarExpanded", expanded).apply();
          renderShell();
        });
    binding.drawerToggle.setOnClickListener(
        v -> binding.drawer.openDrawer(binding.overlayNavigation));
    bindNavigation(binding.sidebarOrders,binding.overlayOrders,R.id.ordersFragment);
    bindNavigation(binding.sidebarReservations,binding.overlayReservations,R.id.reservationsFragment);
    bindNavigation(binding.sidebarTimekeeping,binding.overlayTimekeeping,R.id.timekeepingFragment);
    bindNavigation(binding.sidebarAnalytics,binding.overlayAnalytics,R.id.analyticsFragment);
    bindNavigation(binding.sidebarPrinters,binding.overlayPrinters,R.id.printerManagementFragment);
    binding.sidebarManage.setOnClickListener(v -> showManage());
    binding.overlayManage.setOnClickListener(v -> showManage());
    binding.orderAlerts.setOnClickListener(v -> {
      if (getSupportFragmentManager().findFragmentByTag("orderAlerts") == null)
        new au.com.nakornthai.staffapp.ordering.ui.OrderAlertsDialog().show(getSupportFragmentManager(),"orderAlerts");
    });
    binding.footer.setOnItemSelectedListener(
        item -> {
          if (item.getItemId() == R.id.manage) {
            showManage();
            return false;
          }
          navigate(item.getItemId() == R.id.home ? R.id.ordersFragment : R.id.analyticsFragment);
          return false;
        });
    binding.acknowledge.setOnClickListener(v -> orders.acknowledge());
    navigation.addOnDestinationChangedListener((controller, destination, args) -> renderShell());
    binding.drawer
        .addOnLayoutChangeListener(
            (v, l, t, r, b, ol, ot, or, ob) -> {
              int width = Math.max(0, r - l - v.getPaddingLeft() - v.getPaddingRight());
              int footerWidth =
                  Math.min(
                      width,
                      getResources().getDimensionPixelSize(R.dimen.footer_content_max_width));
              if (footerWidth > 0 && binding.footer.getLayoutParams().width != footerWidth) {
                ViewGroup.LayoutParams footerParams = binding.footer.getLayoutParams();
                footerParams.width = footerWidth;
                binding.footer.setLayoutParams(footerParams);
              }
              float density=getResources().getDisplayMetrics().density;
              float font=getResources().getConfiguration().fontScale;
              boolean small=!au.com.nakornthai.staffapp.shared.dashboard.DashboardPresentation.permanentNavigation(width/density,font);
              boolean shortWindow=au.com.nakornthai.staffapp.shared.dashboard.DashboardPresentation.compact((b-t)/density,font);
              if (small!=narrow || shortWindow!=compact || width!=availableWidth) {
                narrow=small;compact=shortWindow;availableWidth=width;renderShell();
              }
            });
    auth.state.observe(
        this,
        state -> {
          authenticated = state.session != null;
          if (!authenticated) {
            // Remove protected dialogs/back stack on expiry, not just hide the workspace.
            closeProtectedDialogs(getSupportFragmentManager());
            if (navigation.getCurrentDestination() != null
                && navigation.getCurrentDestination().getId() != R.id.loginFragment)
              resetTo(R.id.loginFragment);
          } else if (navigation.getCurrentDestination() == null
              || navigation.getCurrentDestination().getId() == R.id.loginFragment)
            resetTo(R.id.ordersFragment);
          binding.identityLabel.setText(
              authenticated ? getString(R.string.dashboard_identity,state.session.user.username,state.session.user.role) : "");
          binding.topIdentity.setText(binding.identityLabel.getText());
          binding.drawerIdentity.setText(binding.identityLabel.getText());
          renderShell();
        });
    orders.state.observe(
        this,
        state -> {
          binding.shellConnection.setText(
              (state.error != null
                      ? "Monitoring unavailable — stale data"
                      : state.loading
                          ? "Synchronizing orders…"
                          : state.lastSync == 0
                              ? "Waiting for first synchronization"
                              : "Foreground monitoring")
                  + " · "
                  + state.column("NEW").size()
                  + " pending"
                  + (state.lastSync == 0
                      ? ""
                      : " · Last sync "
                          + au.com.nakornthai.staffapp.ordering.ui.OrderPresentation.time(
                              java.time.Instant.ofEpochMilli(state.lastSync).toString())
                          + " (Melbourne)"));
          unacknowledged=state.unacknowledged;
          renderAlerts();
          binding.alertText.setText(
              state.unacknowledged
                  + " new pending order(s). Acknowledging does not accept an order.");
          if (state.alertSequence > heard) {
            heard = state.alertSequence;
            graph().heardAlertSequence = heard;
            if (authenticated && state.foreground) {
              try {
                if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600);
              } catch (RuntimeException unavailable) {
                /* Visible alert remains available. */
              }
            }
          }
        });
    renderShell();
  }

  private void closeProtectedDialogs(androidx.fragment.app.FragmentManager manager) {
    for (androidx.fragment.app.Fragment fragment : manager.getFragments()) {
      if (fragment instanceof androidx.fragment.app.DialogFragment) {
        ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
      }
      closeProtectedDialogs(fragment.getChildFragmentManager());
    }
  }

  private void resetTo(int id) {
    navigation.navigate(
        id,
        null,
        new NavOptions.Builder()
            .setPopUpTo(R.id.staff_navigation, true)
            .setLaunchSingleTop(true)
            .build());
  }

  public void navigate(int id) {
    if (!authenticated) return;
    if (id == R.id.printerManagementFragment
        && !au.com.nakornthai.staffapp.printing.ui.PrinterManagementViewModel.isAdmin(graph().identity.current())) return;
    binding.drawer.closeDrawers();
    if (navigation.getCurrentDestination() != null
        && navigation.getCurrentDestination().getId() == id) return;
    navigation.navigate(
        id,
        null,
        new NavOptions.Builder()
            .setPopUpTo(R.id.ordersFragment, false)
            .setLaunchSingleTop(true)
            .build());
  }

  public void signOut() {
    auth.logout();
  }

  private void bindNavigation(MaterialButton rail, MaterialButton drawer, int destination) {
    rail.setOnClickListener(v -> navigate(destination));drawer.setOnClickListener(v -> navigate(destination));
  }
  private void showManage() {
    if (!authenticated) return;
    binding.drawer.closeDrawers();
    if (getSupportFragmentManager().findFragmentByTag("manage")==null)
      new ManageSheet().show(getSupportFragmentManager(),"manage");
  }
  private void renderAlerts() {
    binding.alertBar.setVisibility(authenticated && unacknowledged>0 && !compact ? View.VISIBLE : View.GONE);
    binding.orderAlerts.setText(unacknowledged>0?String.valueOf(unacknowledged):"");
    binding.orderAlerts.setContentDescription(getString(R.string.dashboard_notifications,unacknowledged));
    binding.orderAlerts.setEnabled(authenticated);
  }
  private void renderShell() {
    if(binding==null)return;
    binding.dashboardTopBar.setVisibility(authenticated?View.VISIBLE:View.GONE);
    binding.dashboardTopBar.setMinimumHeight((int)((compact?48:64)*getResources().getDisplayMetrics().density));
    binding.topIdentity.setVisibility(!narrow && !compact?View.VISIBLE:View.GONE);
    binding.footer.setVisibility(authenticated?View.VISIBLE:View.GONE);
    binding.footerDivider.setVisibility(authenticated?View.VISIBLE:View.GONE);
    binding.shellConnection.setVisibility(authenticated && !compact?View.VISIBLE:View.GONE);
    binding.footer.setLabelVisibilityMode(compact?com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_UNLABELED:com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
    ViewGroup.LayoutParams footerParams=binding.footer.getLayoutParams();
    int footerHeight=compact?(int)(56*getResources().getDisplayMetrics().density):ViewGroup.LayoutParams.WRAP_CONTENT;
    if(footerParams.height!=footerHeight){footerParams.height=footerHeight;binding.footer.setLayoutParams(footerParams);}
    binding.sidebar.setVisibility(authenticated && !narrow?View.VISIBLE:View.GONE);
    binding.sidebarToggle.setVisibility(authenticated && !narrow?View.VISIBLE:View.GONE);
    binding.drawerToggle.setVisibility(authenticated && narrow?View.VISIBLE:View.GONE);
    binding.drawer.setDrawerLockMode(authenticated && narrow?DrawerLayout.LOCK_MODE_UNLOCKED:DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    ViewGroup.LayoutParams params=binding.sidebar.getLayoutParams();
    int railWidth=getResources().getDimensionPixelSize(expanded?R.dimen.sidebar_expanded_width:R.dimen.sidebar_collapsed_width);
    if(params.width!=railWidth){params.width=railWidth;binding.sidebar.setLayoutParams(params);}
    ViewGroup.LayoutParams overlay=binding.overlayNavigation.getLayoutParams();
    int drawerWidth=Math.min((int)(288*getResources().getDisplayMetrics().density),Math.max(0,availableWidth));
    if(drawerWidth>0 && overlay.width!=drawerWidth){overlay.width=drawerWidth;binding.overlayNavigation.setLayoutParams(overlay);}
    binding.brand.setVisibility(expanded?View.VISIBLE:View.GONE);
    binding.identityLabel.setVisibility(expanded?View.VISIBLE:View.GONE);
    binding.sidebarToggle.setContentDescription(getString(expanded?R.string.dashboard_collapse:R.string.dashboard_expand));
    binding.sidebarToggle.setTooltipText(binding.sidebarToggle.getContentDescription());
    boolean admin=au.com.nakornthai.staffapp.printing.ui.PrinterManagementViewModel.isAdmin(graph().identity.current());
    binding.sidebarPrinters.setVisibility(admin?View.VISIBLE:View.GONE);binding.overlayPrinters.setVisibility(admin?View.VISIBLE:View.GONE);
    String timekeeping=au.com.nakornthai.staffapp.timekeeping.data.TimekeepingRepository.title(graph().identity.current());
    int current=navigation.getCurrentDestination()==null?0:navigation.getCurrentDestination().getId();
    navStyle(binding.sidebarOrders,binding.overlayOrders,getString(R.string.orders),R.id.ordersFragment,current);
    navStyle(binding.sidebarReservations,binding.overlayReservations,getString(R.string.reservations),R.id.reservationsFragment,current);
    navStyle(binding.sidebarTimekeeping,binding.overlayTimekeeping,timekeeping,R.id.timekeepingFragment,current);
    navStyle(binding.sidebarAnalytics,binding.overlayAnalytics,getString(R.string.analytics),R.id.analyticsFragment,current);
    navStyle(binding.sidebarPrinters,binding.overlayPrinters,getString(R.string.printers_title),R.id.printerManagementFragment,current);
    navStyle(binding.sidebarManage,binding.overlayManage,getString(R.string.manage),0,current);
    binding.footer.getMenu().setGroupCheckable(0,false,false);
    for(int i=0;i<binding.footer.getMenu().size();i++) {
      android.view.MenuItem item=binding.footer.getMenu().getItem(i);item.setCheckable(true);
      item.setChecked((item.getItemId()==R.id.home && current==R.id.ordersFragment)||(item.getItemId()==R.id.analyticsAction && current==R.id.analyticsFragment));
    }
    renderAlerts();
  }
  private void navStyle(MaterialButton rail,MaterialButton overlay,String label,int destination,int current) {
    boolean selected=destination!=0 && destination==current;
    rail.setText(expanded?label:"");overlay.setText(label);
    int padding=(int)((expanded?12:8)*getResources().getDisplayMetrics().density);
    rail.setPadding(padding,0,padding,0);rail.setIconPadding(expanded?padding:0);
    for(MaterialButton button:new MaterialButton[]{rail,overlay}) {
      button.setSelected(selected);button.setStrokeWidth(0);button.setTooltipText(label);
      button.setContentDescription(selected?getString(R.string.dashboard_selected,label):label);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putBoolean("expanded", expanded);
    out.putLong("heard", heard);
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onDestroy() {
    if (tone != null) tone.release();
    super.onDestroy();
  }
}
