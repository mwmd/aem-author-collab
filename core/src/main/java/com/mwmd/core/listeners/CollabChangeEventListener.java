package com.mwmd.core.listeners;

import static com.mwmd.core.util.Constants.CONTENT_DAM;
import static com.mwmd.core.util.Constants.JCR_CONTENT_INFIX;
import static com.mwmd.core.util.Constants.SLASH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.NameConstants;
import com.mwmd.core.services.CollabService;
import com.mwmd.core.util.Constants;

/**
 * JCR observer for content changes. When detecting changes, the listener will
 * generate a local {@link org.osgi.service.event.Event} to capture the content
 * change and initiate messaging. Based on the nature of the event
 * (create/update/delete/move), this class will apply logic to identify which
 * content paths should get refreshed on the page.
 */
@Component(immediate = true)
public class CollabChangeEventListener implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(CollabChangeEventListener.class);

    private static final String MOVED_ABSOLUTE_PATH_FROM = "srcAbsPath";

    private static final String MOVED_ABSOLUTE_PATH_TO = "destAbsPath";

    @Reference
    private SlingRepository slingRepository;

    @Reference
    private CollabService collab;

    @Reference
    private EventAdmin eventAdmin;

    private ObservationManager observationMgr;

    @SuppressWarnings("AEM Rules:AEM-3")
    private Session session;

    @Activate
    protected void activate() {

        try {
            session = slingRepository.loginService(Constants.SERVICE_USER, null);
            observationMgr = session.getWorkspace().getObservationManager();

            int eventTypes = Event.NODE_ADDED | Event.NODE_MOVED | Event.NODE_REMOVED | Event.PROPERTY_ADDED
                    | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED;

            observationMgr.addEventListener(this, eventTypes, "/content", true, null, null, true);
        } catch (RepositoryException e) {
            LOG.error("Error creating listener", e);
        }
    }

    @Deactivate
    protected void deactivate() {

        try {
            if (observationMgr != null) {
                observationMgr.removeEventListener(this);
            }
        } catch (RepositoryException e) {
            LOG.error("Error removing listener", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public void onEvent(EventIterator events) {

        LOG.trace("onEvent start");
        Map<String, Set<String>> updatePaths = new HashMap<>();
        Map<String, Set<String>> refreshPaths = new HashMap<>();
        Set<String> includesMove = new HashSet<>();
        while (events.hasNext()) {
            try {
                Event event = events.nextEvent();
                String path = event.getPath();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Event {} at {}", getTypeLabel(event.getType()), path);
                }

                if (!ignoreUpdate(path, event.getType())) {
                    String page = StringUtils.substringBefore(path, JCR_CONTENT_INFIX);
                    if (!collab.hasPage(page)) {
                        continue;
                    }
                    trackUpdates(updatePaths, refreshPaths, page, path, event);
                    if (Event.NODE_MOVED == event.getType()) {
                        includesMove.add(page);
                    }
                }
            } catch (RepositoryException e) {
                LOG.error("Error processing event", e);
            }
        }

        for (Entry<String, Set<String>> entry : refreshPaths.entrySet()) {

            String page = entry.getKey();

            Collection<String> paths;
            // for move operations, dont't track updated paths as many nodes are touched
            if (includesMove.contains(page)) {
                paths = new HashSet<>();
            } else {
                paths = updatePaths.get(page);
            }

            // collapse refreshPaths to independent paths
            List<String> refreshPathList = new ArrayList<>(entry.getValue());
            Collections.sort(refreshPathList);
            boolean modified;
            do {
                modified = false;
                for (int i = refreshPathList.size() - 1; i > 0; i--) {
                    if (StringUtils.startsWith(refreshPathList.get(i), refreshPathList.get(i - 1).concat(SLASH))) {
                        refreshPathList.remove(i);
                        modified = true;
                    }
                }
            } while (modified);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Producing update event: page={}, updatePaths={}, refreshPaths={}", page,
                        ArrayUtils.toString(paths.toArray()), ArrayUtils.toString(refreshPathList.toArray()));
            }
            new EventProducer(eventAdmin).buildUpdate(page, paths, refreshPathList);
        }
        LOG.trace("onEvent end");
    }

    private static void trackUpdates(Map<String, Set<String>> updatePaths, Map<String, Set<String>> refreshPaths,
            String page, String path, Event event) {

        int annotationIndex = StringUtils.indexOf(path, Constants.NN_CQ_ANNOTATIONS);
        if (annotationIndex >= 0) {
            // track the annotations root node, whatever the detail change within the
            // annotations is
            put(updatePaths, page,
                    StringUtils.substring(path, 0, annotationIndex + Constants.NN_CQ_ANNOTATIONS.length()));
            // don't refresh anything, but add the page with empty list so update is
            // returned
            put(refreshPaths, page, null);
            LOG.trace("Skip change tracking of annotation path: {}", path);
            return;
        }

        String trimmedPath = trimPath(path);
        String parentPath = StringUtils.substringBeforeLast(trimmedPath, SLASH);
        switch (event.getType()) {
        case Event.NODE_REMOVED:
        case Event.NODE_ADDED:
            put(updatePaths, page, trimmedPath);
            put(refreshPaths, page, parentPath);
            break;
        case Event.NODE_MOVED:
            put(updatePaths, page, trimmedPath);
            put(refreshPaths, page, parentPath);
            try {
                Map<String, String> info = event.getInfo();
                LOG.debug("Move operation for {}", trimmedPath);
                LOG.trace("Move operation srcAbsPath {}", info.get(MOVED_ABSOLUTE_PATH_FROM));
                LOG.trace("Move operation destAbsPath {}", info.get(MOVED_ABSOLUTE_PATH_TO));

                String absFrom = info.get(MOVED_ABSOLUTE_PATH_FROM);
                if (StringUtils.isNotBlank(absFrom)) {
                    // moved from one to the other container
                    // only add removal update, if moved from separate or parent container
                    String fromContainer = StringUtils.substringBeforeLast(absFrom, SLASH);
                    if (!StringUtils.equals(fromContainer, parentPath)
                            && (StringUtils.startsWith(parentPath, fromContainer.concat(SLASH))
                                    || !StringUtils.startsWith(fromContainer, parentPath.concat(SLASH)))) {
                        LOG.trace("Adding refresh path for cross-container move: {}", fromContainer);
                        put(refreshPaths, page, fromContainer);
                    }
                }
            } catch (RepositoryException e) {
                LOG.error("Error retrieving move info", e);
            }
            break;
        case Event.PROPERTY_ADDED:
        case Event.PROPERTY_CHANGED:
        case Event.PROPERTY_REMOVED:
            if (isResponsive(path)) {
                // special case: if responsive config was modified, property is already removed
                put(updatePaths, page, trimmedPath);
                put(refreshPaths, page, parentPath);
            } else {
                // parent path is component, because normal path includes property
                put(updatePaths, page, parentPath);
                put(refreshPaths, page, parentPath);
            }
            break;
        default:
            LOG.trace("Unexpected event type: {}", event.getType());
        }
    }

    private static String getTypeLabel(int type) {

        switch (type) {
        case Event.NODE_ADDED:
            return "NODE_ADDED";
        case Event.NODE_MOVED:
            return "NODE_MOVED";
        case Event.NODE_REMOVED:
            return "NODE_REMOVED";
        case Event.PROPERTY_ADDED:
            return "PROPERTY_ADDED";
        case Event.PROPERTY_CHANGED:
            return "PROPERTY_CHANGED";
        case Event.PROPERTY_REMOVED:
            return "PROPERTY_REMOVED";
        default:
            return "";
        }
    }

    private static boolean ignoreUpdate(String path, int type) {

        boolean ignore = false;
        ignore |= StringUtils.startsWith(path, CONTENT_DAM);
        ignore |= !StringUtils.contains(path, JCR_CONTENT_INFIX);
        switch (type) {
        case Event.NODE_REMOVED:
        case Event.NODE_ADDED:
        case Event.NODE_MOVED:
            break;
        case Event.PROPERTY_ADDED:
        case Event.PROPERTY_CHANGED:
        case Event.PROPERTY_REMOVED:
            // ignore page property updates
            ignore |= StringUtils.endsWith(StringUtils.substringBeforeLast(path, SLASH),
                    SLASH.concat(JcrConstants.JCR_CONTENT));
            break;
        default:
            // nothing
        }
        return ignore;
    }

    private static String trimPath(String path) {

        if (isResponsive(path)) {
            return StringUtils.substring(path, 0,
                    StringUtils.indexOf(path, SLASH.concat(NameConstants.NN_RESPONSIVE_CONFIG)));
        }
        return path;
    }

    private static boolean isResponsive(String path) {

        return StringUtils.contains(path, SLASH.concat(NameConstants.NN_RESPONSIVE_CONFIG));
    }

    private static void put(Map<String, Set<String>> map, String key, String value) {

        if (!map.containsKey(key)) {
            map.put(key, new HashSet<>());
        }
        if (StringUtils.isNotBlank(value)) {
            map.get(key).add(value);
        }
    }

}
