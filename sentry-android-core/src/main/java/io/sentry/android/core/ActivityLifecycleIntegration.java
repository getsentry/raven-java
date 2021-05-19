package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Integration;
import io.sentry.Scope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

public final class ActivityLifecycleIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;
  private final @NotNull IHandler handler;

  private boolean performanceEnabled = false;

  private boolean isAllActivityCallbacksAvailable;

  private boolean firstActivityCreated = false;
  private boolean firstActivityResumed = false;
  private boolean hasSavedState = false;

  // WeakHashMap isn't thread safe but ActivityLifecycleCallbacks is only called from the
  // main-thread
  private final @NotNull WeakHashMap<Activity, ITransaction> activitiesWithOngoingTransactions =
      new WeakHashMap<>();

  public ActivityLifecycleIntegration(
      final @NotNull Application application,
      final @NotNull IBuildInfoProvider buildInfoProvider,
      final @NotNull IHandler handler) {
    this.application = Objects.requireNonNull(application, "Application is required");
    Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.handler = Objects.requireNonNull(handler, "Handler is required");

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.Q) {
      isAllActivityCallbacksAvailable = true;
    }
  }

  public ActivityLifecycleIntegration(
      final @NotNull Application application, final @NotNull IBuildInfoProvider buildInfoProvider) {
    this(application, buildInfoProvider, new MainLooperHandler());
  }

  @SuppressWarnings("deprecation")
  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.hub = Objects.requireNonNull(hub, "Hub is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "ActivityLifecycleIntegration enabled: %s",
            this.options.isEnableActivityLifecycleBreadcrumbs());

    performanceEnabled = isPerformanceEnabled(this.options);

    if (this.options.isEnableActivityLifecycleBreadcrumbs() || performanceEnabled) {
      application.registerActivityLifecycleCallbacks(this);
      this.options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration installed.");

      // this is called after the Activity is created, so we know if the App is a warm or cold
      // start.
      handler.post(
          () -> {
            if (firstActivityCreated) {
              AppStartState.getInstance().setColdStart(!hasSavedState);
            }
          });
    }
  }

  private boolean isPerformanceEnabled(final @NotNull SentryAndroidOptions options) {
    return options.isTracingEnabled() && options.isEnableAutoActivityLifecycleTracing();
  }

  @Override
  public void close() throws IOException {
    ActivityFramesState.getInstance().close();
    application.unregisterActivityLifecycleCallbacks(this);

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "ActivityLifecycleIntegration removed.");
    }
  }

  private void addBreadcrumb(final @NonNull Activity activity, final @NotNull String state) {
    if (options != null && hub != null && options.isEnableActivityLifecycleBreadcrumbs()) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("navigation");
      breadcrumb.setData("state", state);
      breadcrumb.setData("screen", getActivityName(activity));
      breadcrumb.setCategory("ui.lifecycle");
      breadcrumb.setLevel(SentryLevel.INFO);
      hub.addBreadcrumb(breadcrumb);
    }
  }

  private @NotNull String getActivityName(final @NonNull Activity activity) {
    return activity.getClass().getSimpleName();
  }

  private void stopPreviousTransactions() {
    for (final Map.Entry<Activity, ITransaction> entry :
        activitiesWithOngoingTransactions.entrySet()) {
      final ITransaction transaction = entry.getValue();
      finishTransaction(transaction);
    }
  }

  private void startTracing(final @NonNull Activity activity) {
    if (performanceEnabled && !isRunningTransaction(activity) && hub != null) {
      // as we allow a single transaction running on the bound Scope, we finish the previous ones
      stopPreviousTransactions();

      // we can only bind to the scope if there's no running transaction
      final ITransaction transaction =
          hub.startTransaction(getActivityName(activity), "navigation");

      // lets bind to the scope so other integrations can pick it up
      hub.configureScope(
          scope -> {
            applyScope(scope, transaction);
          });

      activitiesWithOngoingTransactions.put(activity, transaction);
    }
  }

  @VisibleForTesting
  void applyScope(final @NotNull Scope scope, final @NotNull ITransaction transaction) {
    scope.withTransaction(
        scopeTransaction -> {
          // we'd not like to overwrite existent transactions bound to the Scope
          // manually.
          if (scopeTransaction == null) {
            scope.setTransaction(transaction);
          } else if (options != null) {
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Transaction '%s' won't be bound to the Scope since there's one already in there.",
                    transaction.getName());
          }
        });
  }

  private boolean isRunningTransaction(final @NonNull Activity activity) {
    return activitiesWithOngoingTransactions.containsKey(activity);
  }

  private void stopTracing(final @NonNull Activity activity, final boolean shouldFinishTracing) {
    if (performanceEnabled && shouldFinishTracing) {
      final ITransaction transaction = activitiesWithOngoingTransactions.get(activity);
      finishTransaction(transaction);
    }
  }

  private void finishTransaction(final @Nullable ITransaction transaction) {
    if (transaction != null) {
      SpanStatus status = transaction.getStatus();
      // status might be set by other integrations, let's not overwrite it
      if (status == null) {
        status = SpanStatus.OK;
      }

      transaction.finish(status);
    }
  }

  @Override
  public synchronized void onActivityPreCreated(
      final @NonNull Activity activity, final @Nullable Bundle savedInstanceState) {

    // only executed if API >= 29 otherwise it happens on onActivityCreated
    if (isAllActivityCallbacksAvailable) {
      // if activity has global fields being init. and
      // they are slow, this won't count the whole fields/ctor initialization time, but only
      // when onCreate is actually called.
      startTracing(activity);
    }
  }

  @Override
  public synchronized void onActivityCreated(
      final @NonNull Activity activity, final @Nullable Bundle savedInstanceState) {
    if (!firstActivityCreated) {
      hasSavedState = savedInstanceState != null;
      firstActivityCreated = true;
    }

    addBreadcrumb(activity, "created");

    // fallback call for API < 29 compatibility, otherwise it happens on onActivityPreCreated
    if (!isAllActivityCallbacksAvailable) {
      startTracing(activity);
    }
  }

  @Override
  public synchronized void onActivityStarted(final @NonNull Activity activity) {
    if (performanceEnabled) {
      ActivityFramesState.getInstance().addActivity(activity);
    }

    addBreadcrumb(activity, "started");
  }

  //  private boolean isHardwareAccelerated(Activity activity) {
  //    // we can't observe frame rates for a non hardware accelerated view
  //    return activity.getWindow() != null
  //        && ((activity.getWindow().getAttributes().flags
  //                & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
  //            != 0);
  //  }

  @Override
  public synchronized void onActivityResumed(final @NonNull Activity activity) {
    if (!firstActivityResumed) {
      long millis = SystemClock.uptimeMillis();
      AppStartState.getInstance().setAppStartEnd(millis);
      firstActivityResumed = true;
    }

    addBreadcrumb(activity, "resumed");

    // fallback call for API < 29 compatibility, otherwise it happens on onActivityPostResumed
    if (!isAllActivityCallbacksAvailable && options != null) {
      stopTracing(activity, options.isEnableActivityLifecycleTracingAutoFinish());
    }
  }

  @Override
  public synchronized void onActivityPostResumed(final @NonNull Activity activity) {
    // only executed if API >= 29 otherwise it happens on onActivityResumed
    if (isAllActivityCallbacksAvailable && options != null) {
      // this should be called only when onResume has been executed already, which means
      // the UI is responsive at this moment.
      stopTracing(activity, options.isEnableActivityLifecycleTracingAutoFinish());
    }
  }

  @Override
  public synchronized void onActivityPaused(final @NonNull Activity activity) {
    addBreadcrumb(activity, "paused");
  }

  @Override
  public synchronized void onActivityStopped(final @NonNull Activity activity) {
    addBreadcrumb(activity, "stopped");

    if (performanceEnabled) {
      ActivityFramesState.getInstance().removeActivity(activity);
    }
  }

  @Override
  public synchronized void onActivitySaveInstanceState(
      final @NonNull Activity activity, final @NonNull Bundle outState) {
    addBreadcrumb(activity, "saveInstanceState");
  }

  @Override
  public synchronized void onActivityDestroyed(final @NonNull Activity activity) {
    addBreadcrumb(activity, "destroyed");

    // in case people opt-out enableActivityLifecycleTracingAutoFinish and forgot to finish it,
    // we make sure to finish it when the activity gets destroyed.
    stopTracing(activity, true);

    // clear it up, so we don't start again for the same activity if the activity is in the activity
    // stack still.
    // if the activity is opened again and not in memory, transactions will be created normally.
    if (performanceEnabled) {
      activitiesWithOngoingTransactions.remove(activity);
    }
  }

  @TestOnly
  @NotNull
  WeakHashMap<Activity, ITransaction> getActivitiesWithOngoingTransactions() {
    return activitiesWithOngoingTransactions;
  }
}
