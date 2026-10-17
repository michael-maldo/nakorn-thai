package au.com.nakornthai.staffapp;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;

public final class TestRunner extends AndroidJUnitRunner {
  @Override
  public Application newApplication(ClassLoader loader, String name, Context context)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    return super.newApplication(loader, FixtureApplication.class.getName(), context);
  }
}
