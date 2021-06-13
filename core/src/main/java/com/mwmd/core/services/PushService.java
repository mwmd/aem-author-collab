package com.mwmd.core.services;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.servlet.AsyncContext;

import com.mwmd.core.beans.AnnotationInfo;
import com.mwmd.core.beans.CollabResponseLease;
import com.mwmd.core.beans.CollabResponseUpdate;
import com.mwmd.core.beans.CollabResponseUser;

/**
 * Controls Server-Sent Event sessions and distribution of messages to them.
 */
public interface PushService {

    /**
     * Distributes a lease notification.
     * 
     * @param page     page the lease occurred on
     * @param uid      page edit session ID which triggered the lease. This page
     *                 edit session will not receive the event.
     * @param path     leased content path
     * @param userId   id of the user who acquired the lease
     * @param userName display name of the user who acquired the lease
     */
    void lease(String page, String uid, String path, String userId, String userName);

    /**
     * Distributes a release notification.
     * 
     * @param page page the release occurred on
     * @param uid  page edit session ID which returned the lease. This edit session
     *             will not receive the event.
     * @param path returned content path
     */
    void release(String page, String uid, String path);

    /**
     * Distributes content update notifications.
     * 
     * @param page         page the updates occurred on
     * @param paths        content paths which got modified in the updates
     * @param refreshPaths paths within the page content which should get refreshed
     * @param annotations  current annotation status of the page after these updates
     * @param time         timestamp when the updates occurred
     */
    void update(String page, Collection<String> paths, Collection<String> refreshPaths, AnnotationInfo annotations,
            long time);

    /**
     * Track a newly established Server-Sent Event session.
     * 
     * @param page page for which the session is requesting events
     * @param uid  page edit session ID of this SSE session
     * @param ctx  the context which got established for this session
     */
    void register(String page, String uid, AsyncContext ctx);

    /**
     * Removes a Server-Sent Event session to stop receiving events.
     * 
     * @param ctx context to be removed
     */
    void drop(AsyncContext ctx);

    /**
     * Removes a Server-Sent Event session by its page edit session ID to stop
     * receiving events.
     * 
     * @param uid page edit session ID to remove
     */
    void drop(String uid);

    /**
     * Distributes an exit notification for users.
     * 
     * @param page    page to distribute the notification for
     * @param userIds list of the IDs of the users who left the page
     */
    void exit(String page, Set<String> userIds);

    /**
     * Distributes an entry notification for users.
     * 
     * @param page        page to distribute the notification for
     * @param userIdNames mapping of user IDs to user display names for all entering
     *                    users
     */
    void enter(String page, Map<String, String> userIdNames);

    /**
     * Sends a setup message to a newly established Server-Sent Event session
     * containing the current state of the page.
     * 
     * @param asyncCtx context of the newly established session
     * @param leases   all active leases on this page
     * @param updates  recent updates on this page. This is used to ensure updated
     *                 during page load, or during SSE restart are distributed.
     * @param users    the users active on this page
     */
    void setup(AsyncContext asyncCtx, Set<CollabResponseLease> leases, Set<CollabResponseUpdate> updates,
            Set<CollabResponseUser> users);

}
