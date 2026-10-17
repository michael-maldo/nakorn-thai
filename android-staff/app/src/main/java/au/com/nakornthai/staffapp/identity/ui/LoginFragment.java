package au.com.nakornthai.staffapp.identity.ui;

import android.os.Bundle;
import android.view.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import au.com.nakornthai.staffapp.app.MainActivity;
import au.com.nakornthai.staffapp.databinding.FragmentLoginBinding;

public final class LoginFragment extends Fragment {
  private FragmentLoginBinding binding;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
    binding = FragmentLoginBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle saved) {
    AuthViewModel vm =
        new ViewModelProvider(requireActivity(), ((MainActivity) requireActivity()).factory())
            .get(AuthViewModel.class);
    vm.state.observe(
        getViewLifecycleOwner(),
        state -> {
          binding.signIn.setEnabled(!state.busy);
          binding.username.setEnabled(!state.busy);
          binding.password.setEnabled(!state.busy);
          binding.loginMessage.setText(
              state.message == null ? "Sign in with your staff account." : state.message);
        });
    binding.signIn.setOnClickListener(
        v -> {
          String username = binding.username.getText().toString(),
              password = binding.password.getText().toString();
          if (username.trim().isEmpty() || password.isEmpty()) {
            binding.loginMessage.setText("Enter your username and password.");
            return;
          }
          binding.password.setText("");
          vm.login(username, password);
        });
  }

  @Override
  public void onDestroyView() {
    binding = null;
    super.onDestroyView();
  }
}
