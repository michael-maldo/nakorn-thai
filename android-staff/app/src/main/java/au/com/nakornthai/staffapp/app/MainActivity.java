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
  private boolean expanded = true, authenticated, narrow;
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
    binding.sidebarOrders.setOnClickListener(v -> navigate(R.id.ordersFragment));
    binding.overlayOrders.setOnClickListener(v -> navigate(R.id.ordersFragment));
    binding.sidebarReservations.setOnClickListener(v -> navigate(R.id.reservationsFragment));
    binding.overlayReservations.setOnClickListener(v -> navigate(R.id.reservationsFragment));
    binding.sidebarOrders.setTooltipText("Orders");
    binding.sidebarReservations.setTooltipText("Reservations");
    binding.footer.setOnItemSelectedListener(
        item -> {
          if (item.getItemId() == R.id.manage) {
            if (getSupportFragmentManager().findFragmentByTag("manage") == null)
              new ManageSheet().show(getSupportFragmentManager(), "manage");
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
              boolean small = width / getResources().getDisplayMetrics().density < 840;
              if (small != narrow) {
                narrow = small;
                renderShell();
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
              authenticated ? state.session.user.username + "\n" + state.session.user.role : "");
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
          binding.alertBar.setVisibility(
              authenticated && state.unacknowledged > 0 ? View.VISIBLE : View.GONE);
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

  private void renderShell() {
    if (binding == null) return;
    binding.footer.setVisibility(authenticated ? View.VISIBLE : View.GONE);
    binding.footerDivider.setVisibility(authenticated ? View.VISIBLE : View.GONE);
    binding.shellConnection.setVisibility(authenticated ? View.VISIBLE : View.GONE);
    binding.sidebar.setVisibility(authenticated && !narrow ? View.VISIBLE : View.GONE);
    binding.drawerToggle.setVisibility(authenticated && narrow ? View.VISIBLE : View.GONE);
    binding.drawer.setDrawerLockMode(
        authenticated && narrow
            ? DrawerLayout.LOCK_MODE_UNLOCKED
            : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    ViewGroup.LayoutParams params = binding.sidebar.getLayoutParams();
    params.width =
        getResources()
            .getDimensionPixelSize(
                expanded ? R.dimen.sidebar_expanded_width : R.dimen.sidebar_collapsed_width);
    binding.sidebar.setLayoutParams(params);
    binding.brand.setVisibility(expanded ? View.VISIBLE : View.GONE);
    binding.identityLabel.setVisibility(expanded ? View.VISIBLE : View.GONE);
    binding.sidebarToggle.setContentDescription(
        expanded ? "Collapse navigation" : "Expand navigation");
    binding.sidebarToggle.setTooltipText(expanded ? "Collapse navigation" : "Expand navigation");
    binding.sidebarOrders.setText(expanded ? "Orders" : "");
    binding.sidebarReservations.setText(expanded ? "Reservations" : "");
    int padding = (int) ((expanded ? 12 : 8) * getResources().getDisplayMetrics().density);
    binding.sidebarOrders.setPadding(padding, 0, padding, 0);
    binding.sidebarReservations.setPadding(padding, 0, padding, 0);
    binding.sidebarOrders.setIconPadding(expanded ? padding : 0);
    binding.sidebarReservations.setIconPadding(expanded ? padding : 0);

    int destination =
        navigation.getCurrentDestination() == null ? 0 : navigation.getCurrentDestination().getId();
    selected(binding.sidebarOrders, destination == R.id.ordersFragment);
    selected(binding.sidebarReservations, destination == R.id.reservationsFragment);
    selected(binding.overlayOrders, destination == R.id.ordersFragment);
    selected(binding.overlayReservations, destination == R.id.reservationsFragment);
    binding.footer.getMenu().setGroupCheckable(0, false, false);
    for (int i = 0; i < binding.footer.getMenu().size(); i++) {
      android.view.MenuItem item = binding.footer.getMenu().getItem(i);
      item.setCheckable(true);
      item.setChecked(
          (item.getItemId() == R.id.home && destination == R.id.ordersFragment)
              || (item.getItemId() == R.id.analyticsAction
                  && destination == R.id.analyticsFragment));
    }
    if (!authenticated) binding.alertBar.setVisibility(View.GONE);
  }

  private void selected(MaterialButton button, boolean value) {
    button.setSelected(value);
    button.setAlpha(1f);
    button.setStrokeWidth(0);
    button.setContentDescription(
        (button == binding.sidebarOrders || button == binding.overlayOrders
                ? "Orders"
                : "Reservations")
            + (value ? ", selected" : ""));
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
