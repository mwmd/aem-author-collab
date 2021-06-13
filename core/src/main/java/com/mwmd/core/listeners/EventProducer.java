package com.mwmd.core.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for {@link Event} instances. Note that the events can be local or
 * external, depending on the type of event.
 */
public class EventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventProducer.class);

    private EventAdmin eventAdmin;

    public EventProducer(EventAdmin eventAdmin) {

        this.eventAdmin = eventAdmin;
    }

    public void buildLease(String page, String uid, String user, String path) {

        build(CollabEvent.ACTION_LEASE, page, uid, user, path, null, null, true);
    }

    public void buildRelease(String page, String uid, String user) {

        build(CollabEvent.ACTION_RELEASE, page, uid, user, null, null, null, true);
    }

    public void buildUpdate(String page, Collection<String> paths, Collection<String> refreshPaths) {

        build(CollabEvent.ACTION_UPDATE, page, null, null, null, paths, refreshPaths, false);
    }

    public void buildExit(String page, String uid) {

        build(CollabEvent.ACTION_EXIT, page, uid, null, null, null, null, true);
    }

    private void build(String action, String page, String uid, String user, String path, Collection<String> paths,
            Collection<String> refreshPaths, boolean distributed) {

        Map<String, Object> properties = new HashMap<>();
        addNonEmpty(properties, CollabEvent.PROPERTY_ACTION, action);
        addNonEmpty(properties, CollabEvent.PROPERTY_PAGE, page);
        addNonEmpty(properties, CollabEvent.PROPERTY_UID, uid);
        addNonEmpty(properties, CollabEvent.PROPERTY_USER, user);
        addNonEmpty(properties, CollabEvent.PROPERTY_PATH, path);
        addNonEmpty(properties, CollabEvent.PROPERTY_PATHS, paths);
        addNonEmpty(properties, CollabEvent.PROPERTY_REFRESH_PATHS, refreshPaths);
        if (distributed) {
            properties.put(CollabEvent.PROPERTY_DISTRIBUTED, "");
        }
        eventAdmin.postEvent(new Event(CollabEvent.TOPIC, properties));
        LOG.trace("Event posted: {} {} {}", page, action, path);
    }

    private static void addNonEmpty(Map<String, Object> map, String key, String value) {

        if (StringUtils.isNotBlank(value)) {
            map.put(key, value);
        }
    }

    private static void addNonEmpty(Map<String, Object> map, String key, Collection<String> value) {

        if (CollectionUtils.isNotEmpty(value)) {
            map.put(key, value);
        }
    }

}
