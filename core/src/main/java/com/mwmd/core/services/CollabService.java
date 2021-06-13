package com.mwmd.core.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mwmd.core.beans.Update;
import com.mwmd.core.exceptions.RejectedException;
import com.mwmd.core.exceptions.UserNotFoundException;

/**
 * The central service to process user interactions with the extension during a
 * page edit session.
 */
public interface CollabService {

    /**
     * Requesting a lease of for a content path.
     * 
     * @param page   page path where this content is located in
     * @param uid    page edit session ID requesting the lease
     * @param path   path of the content to be leased
     * @param userId ID of the requesting user
     * @throws UserNotFoundException if the user data has not been created on this
     *                               instance yet
     * @throws RejectedException     if the lease cannot be granted
     */
    void lease(String page, String uid, String path, String userId) throws UserNotFoundException, RejectedException;

    /**
     * Return a previously granted lease.
     * 
     * @param page page path where the lease applied
     * @param uid  page edit session ID requesting the release
     * @throws UserNotFoundException if the user data has not been created on this
     *                               instance yet
     */
    void release(String page, String uid) throws UserNotFoundException;

    /**
     * Capturing that a page edit session of a user is terminated.
     * 
     * @param page page path where the page edit session was active on
     * @param uid  page edit session ID to terminate
     */
    void exit(String page, String uid);

    /**
     * Processes a content change on a page.
     * 
     * @param page         page path where the change occurred
     * @param paths        content paths which got modified
     * @param refreshPaths the paths within the page content which should get
     *                     refreshed
     */
    void update(String page, Collection<String> paths, Collection<String> refreshPaths);

    /**
     * Inquiring if content is available for lease.
     * 
     * @param page page containing the content
     * @param uid  page edit session ID inquiring about the lease
     * @param path path of the content to be potentially leased
     * @return if the content is either available for lease, or the active lease
     *         belongs to the same user
     */
    boolean mayLease(String page, String uid, String path);

    /**
     * Tracks a new user active on this page.
     * 
     * @param page   page where the user is active on
     * @param userId ID of the new user
     * @param uid    new page edit session ID for this user
     */
    void addUser(String page, String userId, String uid);

    /**
     * Provides the currently active users on a page.
     * 
     * @param page page for which to return the users
     * @return display names of all current users on this page
     */
    Set<String> getUsers(String page);

    /**
     * Get all active lease on a page for other page edit session IDs.
     * 
     * @param page       page for which to return the leases
     * @param excludeUid page edit session ID for which not to return any lease.
     *                   This should be the requesting session's ID.
     * @return mapping between leased content paths and their lease owner's display
     *         name
     */
    Map<String, String> getLeases(String page, String excludeUid);

    /**
     * Retrieve update history for a page after a given minimum time.
     * 
     * @param page    page for which to return the updates
     * @param minTime will only return updates newer than this timestamp
     * @return list of all newer updates on this page
     */
    List<Update> getUpdates(String page, long minTime);

    /**
     * Checks if the extension has collected data about a specific page.
     * 
     * @param page path of the page
     * @return if any collaboration data has been collected for this page
     */
    boolean hasPage(String page);

    /**
     * Returns the display name for a user ID.
     * 
     * @param userId ID of the user
     * @return display name of the user
     */
    String getUserName(String userId);

}
