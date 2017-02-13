package com.getsentry.raven.event;

import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenContext;

/**
 * Helpers for dealing with {@link Breadcrumb}s.
 */
public final class Breadcrumbs {

    /**
     * Private constructor because this is a utility class.
     */
    private Breadcrumbs() {

    }

    /**
     * Record a {@link Breadcrumb} into all of this thread's active contexts.
     *
     * @param breadcrumb Breadcrumb to record
     */
    public static void record(Breadcrumb breadcrumb) {
        for (RavenContext context : Raven.getContexts()) {
            context.recordBreadcrumb(breadcrumb);
        }
    }

}
