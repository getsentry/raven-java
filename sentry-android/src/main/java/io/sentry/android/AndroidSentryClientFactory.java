package io.sentry.android;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import io.sentry.DefaultSentryClientFactory;
import io.sentry.SentryClient;
import io.sentry.android.event.helper.AndroidEventBuilderHelper;
import io.sentry.buffer.Buffer;
import io.sentry.buffer.DiskBuffer;
import io.sentry.event.EventBuilder;
import io.sentry.Sentry;
import io.sentry.config.Lookup;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.dsn.Dsn;
import io.sentry.util.Util;
import io.sentry.event.interfaces.ExceptionMechanism;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.ExceptionMechanismThrowable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SentryClientFactory that handles Android-specific construction, like taking advantage
 * of the Android Context instance.
 */
public class AndroidSentryClientFactory extends DefaultSentryClientFactory {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidSentryClientFactory.class.getName();

    /**
     * Default Buffer directory name.
     */
    private static final String DEFAULT_BUFFER_DIR = "sentry-buffered-events";

    private static volatile ANRWatchDog anrWatchDog;

    private Context ctx;

    /**
     * Construct an AndroidSentryClientFactory using the base Context from the specified Android Application.
     *
     * @param app Android Application
     */
    public AndroidSentryClientFactory(Application app) {
        Log.d(TAG, "Construction of Android Sentry from Android Application.");

        this.ctx = app.getApplicationContext();
    }

    /**
     * Construct an AndroidSentryClientFactory using the specified Android Context.
     *
     * @param ctx Android Context.
     */
    public AndroidSentryClientFactory(Context ctx) {
        Log.d(TAG, "Construction of Android Sentry from Android Context.");

        this.ctx = ctx.getApplicationContext();
        if (this.ctx == null) {
            this.ctx = ctx;
        }
    }

    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        if (!checkPermission(Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Log.d(TAG, "Sentry init with ctx='" + ctx.toString() + "'");

        String protocol = dsn.getProtocol();
        if (protocol.equalsIgnoreCase("noop")) {
            Log.w(TAG, "*** Couldn't find a suitable DSN, Sentry operations will do nothing!"
                + " See documentation: https://docs.sentry.io/clients/java/modules/android/ ***");
        } else if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            String async = Lookup.lookup(DefaultSentryClientFactory.ASYNC_OPTION, dsn);
            if (async != null && async.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Sentry Android cannot use synchronous connections, remove '"
                    + DefaultSentryClientFactory.ASYNC_OPTION + "=false' from your options.");
            }

            throw new IllegalArgumentException("Only 'http' or 'https' connections are supported in"
                + " Sentry Android, but received: " + protocol);
        }

        SentryClient sentryClient = super.createSentryClient(dsn);
        sentryClient.addBuilderHelper(new AndroidEventBuilderHelper(ctx));

        boolean enableAnrTracking = "true".equalsIgnoreCase(Lookup.lookup("anr.enable", dsn));
        Log.d(TAG, "ANR is='" + String.valueOf(enableAnrTracking) + "'");
        if (enableAnrTracking && anrWatchDog == null) {
            String timeIntervalMsConfig = Lookup.lookup("anr.timeoutIntervalMs", dsn);
            int timeoutIntervalMs = timeIntervalMsConfig != null
                    ? Integer.parseInt(timeIntervalMsConfig)
                    //CHECKSTYLE.OFF: MagicNumber
                    : 5000;
                    //CHECKSTYLE.ON: MagicNumber

            Log.d(TAG, "ANR timeoutIntervalMs is='" + String.valueOf(timeoutIntervalMs) + "'");

            anrWatchDog = new ANRWatchDog(timeoutIntervalMs, new ANRWatchDog.ANRListener() {
                @Override public void onAppNotResponding(ApplicationNotResponding error) {
                    Log.d(TAG, "ANR triggered='" + error.getMessage() + "'");

                    EventBuilder builder = new EventBuilder();
                    builder.withTag("thread_state", error.getState().toString());
                    ExceptionMechanism mechanism = new ExceptionMechanism("anr", false);
                    Throwable throwable = new ExceptionMechanismThrowable(mechanism, error);
                    builder.withSentryInterface(new ExceptionInterface(throwable));

                    Sentry.capture(builder);
                }
            });
            anrWatchDog.start();
        }

        return sentryClient;
    }

    @Override
    protected Collection<String> getInAppFrames(Dsn dsn) {
        Collection<String> inAppFrames = super.getInAppFrames(dsn);

        if (inAppFrames.isEmpty()) {
            PackageInfo info = null;
            try {
                info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Error getting package information.", e);
            }

            if (info != null && !Util.isNullOrEmpty(info.packageName)) {
                List<String> newPackages = new ArrayList<>(1);
                newPackages.add(info.packageName);
                return newPackages;
            }
        }

        return inAppFrames;
    }

    @Override
    protected Buffer getBuffer(Dsn dsn) {
        File bufferDir;
        String bufferDirOpt = Lookup.lookup(BUFFER_DIR_OPTION, dsn);
        if (bufferDirOpt != null) {
            bufferDir = new File(bufferDirOpt);
        } else {
            bufferDir = new File(ctx.getCacheDir().getAbsolutePath(), DEFAULT_BUFFER_DIR);
        }

        Log.d(TAG, "Using buffer dir: " + bufferDir.getAbsolutePath());
        return new DiskBuffer(bufferDir, getBufferSize(dsn));
    }

    @Override
    protected ContextManager getContextManager(Dsn dsn) {
        return new SingletonContextManager();
    }

    /**
     * Check whether the application has been granted a certain permission.
     *
     * @param permission Permission as a string
     *
     * @return true if permissions is granted
     */
    private boolean checkPermission(String permission) {
        int res = ctx.checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }
}
