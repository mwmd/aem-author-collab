package com.mwmd.core.services.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.beans.AnnotationInfo;
import com.mwmd.core.beans.ExpirationResult;
import com.mwmd.core.beans.Update;
import com.mwmd.core.exceptions.RejectedException;
import com.mwmd.core.exceptions.UserNotFoundException;
import com.mwmd.core.services.CollabService;
import com.mwmd.core.services.CollabSettings;
import com.mwmd.core.services.PushService;
import com.mwmd.core.util.CollabUtil;
import com.mwmd.core.util.Constants;

@Component
public class CollabServiceImpl implements CollabService {

    private static final Logger LOG = LoggerFactory.getLogger(CollabServiceImpl.class);

    private static final String USER_EXPIRATION_JOB_NAME = "collabUserExpirationJob";

    private static final int USER_EXPIRATION_JOB_SECONDS = 1;

    @Reference
    private PushService messaging;

    @Reference
    private CollabSettings settings;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private Scheduler scheduler;

    private ConcurrentHashMap<String, CollabPageStatus> pages = new ConcurrentHashMap<>();

    private Map<String, String> userNames = new ConcurrentHashMap<>();

    @Override
    public void lease(String page, String uid, String path, String user)
            throws UserNotFoundException, RejectedException {

        CollabPageStatus pageStatus = getPageStatus(page);
        if (StringUtils.isBlank(path) || StringUtils.equals(path, pageStatus.getLease(uid))) {
            pageStatus.ping(uid);
        } else {
            String oldLeasePath = pageStatus.lease(uid, path);
            if (StringUtils.isNotBlank(oldLeasePath) && !StringUtils.equals(oldLeasePath, path)) {
                messaging.release(page, uid, oldLeasePath);
            }
            messaging.lease(page, uid, path, user, getUserName(user));
        }
    }

    @Override
    public void release(String page, String uid) throws UserNotFoundException {

        String path = getPageStatus(page).release(uid);
        if (StringUtils.isNotBlank(path)) {
            messaging.release(page, uid, path);
        }
    }

    @Override
    public void update(String page, Collection<String> paths, Collection<String> refreshPaths) {

        long time = getPageStatus(page).update(paths, refreshPaths);

        // Get current page annotations to include in push
        AnnotationInfo annotations = new AnnotationInfo();
        try (ResourceResolver resolver = CollabUtil.getServiceResolver(resolverFactory)) {
            buildAnnotationInfo(annotations, resolver.getResource(page + Constants.JCR_CONTENT_SUFFIX));
        } catch (LoginException e) {
            LOG.error("Error retrieving service resolver", e);
        }

        messaging.update(page, paths, refreshPaths, annotations, time);
    }

    @Override
    public Set<String> getUsers(String page) {

        return getPageStatus(page).getUsers();
    }

    @Override
    public Map<String, String> getLeases(String page, String excludeUid) {

        return getPageStatus(page).getLeases(excludeUid);
    }

    @Override
    public List<Update> getUpdates(String page, long minTime) {

        return getPageStatus(page).getUpdates(minTime);
    }

    /**
     * Returns the page status object for a page, and creates it if not already
     * present.
     * 
     * @param pagePath path of the content page
     * @return existing or newly created page status
     */
    private CollabPageStatus getPageStatus(String pagePath) {

        CollabPageStatus status = pages.get(pagePath);
        if (status == null) {
            synchronized (pages) {
                status = pages.get(pagePath);
                if (status == null) {
                    status = new CollabPageStatus();
                    pages.put(pagePath, status);
                }
            }
        }
        return status;
    }

    @Override
    public void addUser(String page, String userId, String uid) {

        if (StringUtils.isAnyBlank(userId, uid)) {
            LOG.error("Cannot add user with missing data, userId={} , uid={}", userId, uid);
            return;
        }
        if (getPageStatus(page).addUser(uid, userId)) {
            Map<String, String> userIdNames = new HashMap<>();
            userIdNames.put(userId, getUserName(userId));
            messaging.enter(page, userIdNames);
        }
    }

    @Override
    public boolean hasPage(String page) {

        return pages.containsKey(page);
    }

    @Override
    public String getUserName(String userId) {

        if (StringUtils.isBlank(userId)) {
            return null;
        }
        if (userNames.containsKey(userId)) {
            return StringUtils.trimToNull(userNames.get(userId));
        }
        loadUserProfile(userId);
        return StringUtils.defaultIfBlank(userNames.get(userId), userId);
    }

    /**
     * Initializes user information from the AEM user profile.
     * 
     * @param userId ID of the user to be initialized
     */
    private void loadUserProfile(String userId) {

        String userName = userId;
        try (ResourceResolver resolver = CollabUtil.getServiceResolver(resolverFactory)) {
            JackrabbitSession session = (JackrabbitSession) resolver.adaptTo(Session.class);
            Authorizable authorizable = session.getUserManager().getAuthorizable(userId);
            if (authorizable != null) {
                // user name
                String familyName = CollabUtil.getProfileProperty(authorizable, "familyName");
                if ("null".equals(familyName)) {
                    familyName = null;
                }
                String givenName = CollabUtil.getProfileProperty(authorizable, "givenName");
                if ("null".equals(givenName)) {
                    givenName = null;
                }
                if (!StringUtils.isAllBlank(familyName, givenName)) {
                    userName = StringUtils.trim(StringUtils.join(givenName, " ", familyName));
                }
            }
        } catch (LoginException e) {
            LOG.error("Error retrieving service resolver", e);
        } catch (RepositoryException e) {
            LOG.error("Error retrieving user data", e);
        }
        LOG.trace("Name for {} : {}", userId, userName);
        userNames.put(userId, userName);
    }

    @Override
    public boolean mayLease(String page, String uid, String path) {

        return getPageStatus(page).mayLease(uid, path);
    }

    @Override
    public void exit(String page, String uid) {

        String path = getPageStatus(page).exit(uid);
        if (StringUtils.isNotBlank(path)) {
            LOG.debug("UID left, releasing {} / {}", page, uid);
            messaging.release(page, uid, path);
        }
    }

    @Activate
    protected void activate() {

        ScheduleOptions opts = scheduler.NOW(-1, USER_EXPIRATION_JOB_SECONDS).name(USER_EXPIRATION_JOB_NAME);
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                for (Entry<String, CollabPageStatus> page : pages.entrySet()) {
                    ExpirationResult expired = page.getValue().removeExpired();
                    for (String uid : expired.getExpiredUids()) {
                        LOG.trace("Dropping uid from push messaging: {}", uid);
                        messaging.drop(uid);
                    }
                    if (!expired.getExitUserIds().isEmpty()) {
                        messaging.exit(page.getKey(), expired.getExitUserIds());
                    }
                }
            }
        }, opts);
    }

    @Deactivate
    protected void deactivate() {

        if (!scheduler.unschedule(USER_EXPIRATION_JOB_NAME)) {
            LOG.error("Couldn't stop the user expiration job");
        }
    }

    /**
     * Recursive method to generate the current annotation status of a page.
     * 
     * @param info summary data of annotations on the page
     * @param res  resource to check for annotations and proceed with its children
     */
    private static void buildAnnotationInfo(AnnotationInfo info, Resource res) {

        if (info == null || res == null) {
            return;
        }

        Iterator<Resource> children = res.listChildren();
        if (Constants.NN_CQ_ANNOTATIONS.equals(res.getName())) {
            if (children.hasNext()) {
                info.addComponent(res.getParent().getPath());
                while (children.hasNext()) {
                    info.addAnnotation();
                    LOG.trace("Counted annotation {}", children.next());
                }
            }
        } else {
            while (children.hasNext()) {
                buildAnnotationInfo(info, children.next());
            }
        }
    }

}
