package com.mwmd.core.listeners;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.exceptions.RejectedException;
import com.mwmd.core.exceptions.UserNotFoundException;
import com.mwmd.core.services.CollabService;

/**
 * Handles {@link Event} instances specific to this extension. Mostly collects
 * the event data and forwards it to {@link CollabService} for processing. Note
 * that the events can be local or external, depending on the type of event.
 */
@Component(service = EventHandler.class, immediate = true, property = {
        EventConstants.EVENT_TOPIC + "=" + CollabEvent.TOPIC,
        Constants.SERVICE_DESCRIPTION + "=Collab OSGi event handler" })
public class CollabHandler implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CollabHandler.class);

    @Reference
    private CollabService collab;

    @Override
    public void handleEvent(Event event) {

        String action = getString(event, CollabEvent.PROPERTY_ACTION);
        String page = getString(event, CollabEvent.PROPERTY_PAGE);
        String uid = getString(event, CollabEvent.PROPERTY_UID);
        if (LOG.isTraceEnabled()) {
            String app = StringUtils.defaultIfBlank(getString(event, CollabEvent.PROPERTY_APPLICATION), "local");
            LOG.trace("Consuming event [{}] {} / {} / {}", app, page, action, uid);
        }
        if (StringUtils.isBlank(action)) {
            LOG.trace("Skipping null type event");
            return;
        }
        try {
            String user = getString(event, CollabEvent.PROPERTY_USER);
            switch (action) {
            case CollabEvent.ACTION_LEASE:
                String path = getString(event, CollabEvent.PROPERTY_PATH);
                try {
                    try {
                        collab.lease(page, uid, path, user);
                    } catch (UserNotFoundException e) {
                        LOG.trace("User doesn't exist, creating: {}", uid);
                        collab.addUser(page, user, uid);
                        collab.lease(page, uid, path, user);
                    }
                } catch (RejectedException e) {
                    LOG.info("Rejecting lease for {} at {}", uid, path);
                }
                LOG.debug("Consumed '{}' for {} at {}", action, uid, path);
                break;
            case CollabEvent.ACTION_RELEASE:
                try {
                    collab.release(page, uid);
                } catch (UserNotFoundException e) {
                    LOG.trace("User doesn't exist, creating: {}", uid);
                    collab.addUser(page, user, uid);
                    collab.release(page, uid);
                }
                LOG.debug("Consumed '{}' for {}", action, uid);
                break;
            case CollabEvent.ACTION_UPDATE:
                Collection<String> paths = getCollection(event, CollabEvent.PROPERTY_PATHS);
                Collection<String> refreshPaths = getCollection(event, CollabEvent.PROPERTY_REFRESH_PATHS);
                collab.update(page, paths, refreshPaths);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Consumed '{}' at {}", action, ArrayUtils.toString(paths));
                }
                break;
            case CollabEvent.ACTION_EXIT:
                collab.exit(page, uid);
                LOG.debug("Consumed '{}' for {}", action, uid);
                break;
            default:
                LOG.trace("Unknown action: {}", action);
            }
        } catch (UserNotFoundException e) {
            LOG.error("Unexpected missing user", e);
        } catch (RuntimeException e) {
            LOG.error("Unexpected error", e);
        }
    }

    private static String getString(Event event, String key) {

        Object value = event.getProperty(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    private static Collection<String> getCollection(Event event, String key) {

        Object value = event.getProperty(key);
        if (value instanceof Collection) {
            return (Collection<String>) value;
        }
        return Collections.emptySet();
    }

}
