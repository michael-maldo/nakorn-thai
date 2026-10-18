package au.com.nakornthai.staffapp;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.shared.network.ApiFailure;
import au.com.nakornthai.staffapp.timekeeping.data.*;
import au.com.nakornthai.staffapp.timekeeping.model.*;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance.*;
import au.com.nakornthai.staffapp.timekeeping.ui.TimekeepingViewModel;
import java.math.BigDecimal;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TimekeepingViewModelTest {
  @Rule public InstantTaskExecutorRule live = new InstantTaskExecutorRule();
  IdentityRepository identity; TimekeepingRepository repository; TimekeepingViewModel vm;
  final Queue<Runnable> jobs = new ArrayDeque<>();
  @Before public void setup() throws Exception {
    identity = mock(IdentityRepository.class); repository = mock(TimekeepingRepository.class);
    when(identity.current()).thenReturn(TimekeepingRulesTest.session("ADMIN")); when(identity.generation()).thenReturn(9L);
    Restaurant restaurant = new Restaurant(); restaurant.timezone = "Australia/Melbourne"; restaurant.evaluatedAt = "2026-10-18T00:00:00Z";
    when(repository.restaurant(9L)).thenReturn(restaurant);
    when(repository.staff(9L)).thenReturn(List.of(TimekeepingRulesTest.session("FOH").user));
    Timesheet sheet = new Timesheet(); sheet.size=25; sheet.completedSeconds=BigDecimal.ZERO;
    when(repository.sheet(eq(9L),any(),any(),any(),anyInt())).thenReturn(sheet);
    when(repository.openShifts(9L)).thenReturn(List.of());
    vm = new TimekeepingViewModel(repository,identity,jobs::add); vm.sessionChanged(); run();
  }
  void run() { while (!jobs.isEmpty()) jobs.remove().run(); }
  @Test public void duplicateConfirmationSubmitsOnceAndUsesServerResult() throws Exception {
    Entry result = new Entry(); result.id="entry"; result.clockIn="2026-10-18T01:00:00Z"; result.open=true;
    when(repository.mutate(eq(9L),any())).thenReturn(result);
    vm.reviewLive("clock-in","staff"); assertNotNull(vm.state.getValue().pending);
    assertTrue(vm.confirm()); assertFalse(vm.confirm()); assertTrue(vm.state.getValue().busy);
    run(); verify(repository,times(1)).mutate(eq(9L),any());
    assertTrue(vm.state.getValue().message.contains("Confirmed by server"));
    assertTrue(vm.state.getValue().message.contains("12:00:00 +11:00")); assertFalse(vm.state.getValue().busy);
  }
  @Test public void uncertainWritesBlockReplayUntilExplicitReadRefresh() throws Exception {
    when(repository.mutate(eq(9L),any())).thenThrow(new ApiFailure(0,"Outcome unknown. Refresh and inspect attendance.",true));
    vm.reviewLive("clock-out","staff"); vm.confirm(); run();
    assertTrue(vm.state.getValue().uncertain); assertFalse(vm.state.getValue().canWrite());
    vm.reviewLive("clock-out","staff"); assertFalse(vm.confirm()); verify(repository,times(1)).mutate(eq(9L),any());
    vm.refresh(0);run(); assertFalse(vm.state.getValue().uncertain); assertTrue(vm.state.getValue().canWrite());
  }
  @Test public void staffHaveReadOnlyStateAndNeverLoadUsersOrOpenAdminData() throws Exception {
    when(identity.current()).thenReturn(TimekeepingRulesTest.session("BOH")); when(identity.generation()).thenReturn(10L);
    Restaurant r=new Restaurant();r.timezone="Australia/Melbourne";r.evaluatedAt="2026-10-18T00:00:00Z";
    when(repository.restaurant(10L)).thenReturn(r);Timesheet t=new Timesheet();t.size=25;t.completedSeconds=BigDecimal.ZERO;
    when(repository.sheet(eq(10L),any(),any(),any(),anyInt())).thenReturn(t);
    vm.sessionChanged();run(); assertFalse(vm.state.getValue().admin);assertFalse(vm.state.getValue().canWrite());
    vm.reviewLive("clock-in","staff");vm.edit(null,"staff");vm.audit("entry",0);assertFalse(vm.confirm());
    verify(repository,never()).mutate(anyLong(),any());verify(repository,never()).staff(10L);verify(repository,never()).openShifts(10L);
  }
  @Test public void mandatoryReasonAndStaleVersionCannotSilentlySubmit() throws Exception {
    Entry e=new Entry();e.id="entry";e.staffUserId="staff";e.clockIn="2026-10-18T00:00:00Z";e.clockOut="2026-10-18T01:00:00Z";e.version=4;
    vm.edit(e,null);vm.reviewDraft();assertNull(vm.state.getValue().pending);assertTrue(vm.state.getValue().message.contains("reason"));
    vm.draft.reason="Correct missed minutes";vm.reviewDraft();assertEquals(4L,vm.state.getValue().pending.body.get("version"));
    when(repository.mutate(eq(9L),any())).thenThrow(new ApiFailure(409,"Attendance changed; reload before saving"));
    vm.confirm();run();assertFalse(vm.state.getValue().canWrite());assertTrue(vm.state.getValue().message.contains("latest version"));
  }
  @Test public void forbiddenAndNetworkReadsRemainRetryableWithoutAnyWrite() throws Exception {
    when(repository.restaurant(9L)).thenThrow(new ApiFailure(403,"Forbidden"));
    vm.refresh(0);run();assertFalse(vm.state.getValue().fresh);assertFalse(vm.state.getValue().busy);
    verify(repository,never()).mutate(anyLong(),any());
  }
  @Test public void principalChangesDiscardDraftsAndLateResults() {
    vm.reviewLive("clock-in","staff");when(identity.current()).thenReturn(null);when(identity.generation()).thenReturn(10L);
    vm.sessionChanged();assertNull(vm.state.getValue().sheet);assertNull(vm.state.getValue().pending);assertNull(vm.draft);
  }
  @Test public void serverDateDefaultsAndExplicitRefreshDiscardOldVersions() {
    assertEquals("2026-10-18",vm.fromDate);assertEquals("2026-10-18",vm.toDate);
    vm.edit(null,"staff");vm.draft.in="2026-10-17T22:00:00+11:00";
    vm.draft.out="2026-10-18T02:00:00+11:00";vm.draft.reason="Historical";vm.reviewDraft();
    assertNotNull(vm.state.getValue().pending);vm.refresh(0);run();
    assertNull(vm.draft);assertNull(vm.state.getValue().pending);
  }
}
