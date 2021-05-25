package io.sentry.android.core;

import android.os.SystemClock;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AppStartState {

  private static final @NotNull AppStartState instance = new AppStartState();

  private @Nullable Long appStart;
  private @Nullable Long appStartEnd;
  private @Nullable Boolean coldStart;
  private @Nullable Date appStartTime;

  private AppStartState() {}

  static @NotNull AppStartState getInstance() {
    return instance;
  }

  void setAppStartEnd() {
    appStartEnd = SystemClock.uptimeMillis();
  }

  @Nullable
  Long getAppStartInterval() {
    if (appStart == null || appStartEnd == null || coldStart == null) {
      return null;
    }
    return appStartEnd - appStart;
  }

  boolean getColdStart() {
    return Boolean.TRUE.equals(coldStart);
  }

  void setColdStart(final boolean coldStart) {
    this.coldStart = coldStart;
  }

  @Nullable
  Date getAppStartTime() {
    return appStartTime;
  }

  synchronized void setAppStartTime(final long appStart, final @NotNull Date appStartTime) {
    // method is synchronized because the SDK may by init. on a background thread.
    if (this.appStartTime != null && this.appStart != null) {
      return;
    }
    this.appStartTime = appStartTime;
    this.appStart = appStart;
  }
}
