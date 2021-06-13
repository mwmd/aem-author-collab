package com.mwmd.core.listeners;

import org.osgi.service.event.Event;

/**
 * Constants for generation of {@link Event} instances.
 */
public final class CollabEvent {

    public static final String TOPIC = "com/mwmd/collab";

    public static final String ACTION_LEASE = "lease";

    public static final String ACTION_RELEASE = "release";

    public static final String ACTION_UPDATE = "update";

    public static final String ACTION_EXIT = "exit";

    public static final String PROPERTY_ACTION = "action";

    public static final String PROPERTY_PATH = "path";

    public static final String PROPERTY_PATHS = "paths";

    public static final String PROPERTY_REFRESH_PATHS = "refreshpaths";

    public static final String PROPERTY_UID = "uid";

    public static final String PROPERTY_USER = "user";

    public static final String PROPERTY_PAGE = "page";

    public static final String PROPERTY_DISTRIBUTED = "event.distribute";

    public static final String PROPERTY_APPLICATION = "event.application";

    private CollabEvent() {
        // nothing
    }

}
