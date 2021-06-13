package com.mwmd.core.services.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.beans.ExpirationResult;
import com.mwmd.core.beans.Update;
import com.mwmd.core.beans.User;
import com.mwmd.core.exceptions.RejectedException;
import com.mwmd.core.exceptions.UserNotFoundException;
import com.mwmd.core.util.CollabUtil;

/**
 * Holds all collaboration data for a single page, and provides functionality to
 * interact with it.
 */
public class CollabPageStatus {

    private static final Logger LOG = LoggerFactory.getLogger(CollabPageStatus.class);

    /**
     * currently active users on this page
     */
    private Map<String, User> users = new ConcurrentHashMap<>();

    /**
     * lookup map to find active leases for content paths
     */
    private Map<String, String> lesseeUidByPath = new ConcurrentHashMap<>();

    /**
     * history of updates on this page
     */
    private List<Update> updates = Collections.synchronizedList(new ArrayList<>());

    /**
     * Registers a heartbeat for a page edit session.
     * 
     * @param uid page edit session ID
     * @throws UserNotFoundException if the user data isn't registered in this page
     */
    public void ping(String uid) throws UserNotFoundException {

        User user = users.get(uid);
        if (user != null) {
            user.ping();
        } else {
            throw new UserNotFoundException();
        }
    }

    /**
     * Returns the content path which a page edit session is leasing, if any.
     * 
     * @param uid page edit session ID
     * @return the content path leased by this session, or null
     */
    public String getLease(String uid) {

        User user = users.get(uid);
        if (user != null) {
            return user.getLeasePath();
        }
        return null;
    }

    /**
     * Leases a content path to a page edit session ID.
     * 
     * @param uid  page edit session ID requesting the lease
     * @param path content path for which the lease is requested
     * @return if the page edit session ID previously had a lease for another path,
     *         it gets returned so its release can be broadcasted; otherwise null
     * @throws UserNotFoundException if the user requesting the lease isn't tracked
     *                               yet for this page
     * @throws RejectedException     if the lease cannot be granted because there's
     *                               another active lease on it
     */
    public String lease(String uid, String path) throws UserNotFoundException, RejectedException {

        User user = users.get(uid);
        if (user != null) {
            user.ping();
            String oldLeasePath;
            synchronized (users) { // necessary?
                if (!mayLease(uid, path)) {
                    throw new RejectedException();
                }
                oldLeasePath = user.getLeasePath();
                user.setLeasePath(path);
                lesseeUidByPath.put(path, uid);
            }
            return oldLeasePath;
        } else {
            throw new UserNotFoundException();
        }
    }

    /**
     * Releases the lease of a page edit session, if there is any.
     * 
     * @param uid page edit session ID holding the lease
     * @return content path which got released; null if no lease existed
     * @throws UserNotFoundException if the user requesting the release isn't
     *                               tracked yet for this page
     */
    public String release(String uid) throws UserNotFoundException {

        User user = users.get(uid);
        if (user != null) {
            String oldLeasePath = user.getLeasePath();
            user.setLeasePath(null);
            user.ping();
            if (StringUtils.isNotBlank(oldLeasePath)) {
                synchronized (users) { // necessary?
                    String lesseeUid = lesseeUidByPath.get(oldLeasePath);
                    if (StringUtils.equals(uid, lesseeUid)) {
                        lesseeUidByPath.remove(oldLeasePath);
                    }
                }
            }
            return oldLeasePath;
        } else {
            throw new UserNotFoundException();
        }
    }

    /**
     * Terminates a page edit session.
     * 
     * @param uid page edit session ID to terminate
     * @return if the page edit session ID previously had a lease, it gets returned
     *         so its release can be broadcasted; otherwise null
     */
    public String exit(String uid) {

        User user = users.get(uid);
        String oldLeasePath = null;
        if (user != null) {
            user.setExpired(true);
            oldLeasePath = user.getLeasePath();
            if (StringUtils.isNotBlank(oldLeasePath)) {
                synchronized (users) { // necessary?
                    String lesseeUid = lesseeUidByPath.get(oldLeasePath);
                    if (StringUtils.equals(uid, lesseeUid)) {
                        lesseeUidByPath.remove(oldLeasePath);
                    }
                }
            }
        }
        return oldLeasePath;
    }

    /**
     * Captures content updates.
     * 
     * @param paths        content paths modified in the operation
     * @param refreshPaths paths within the page content to refresh for these
     *                     updates
     * @return timestamp for these updates
     */
    public long update(Collection<String> paths, Collection<String> refreshPaths) {

        Update u = new Update(paths, refreshPaths);
        updates.add(u);
        return u.getTime();
    }

    /**
     * Adds a user as active on this page.
     * 
     * @param uid  ID of the user
     * @param name display name of the user
     * @return true if the user got added, false if the user already existed
     */
    public boolean addUser(String uid, String name) {

        if (!users.containsKey(uid)) {
            synchronized (users) { // necessary?
                users.put(uid, new User(name));
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Returns all active users on this page.
     * 
     * @return display names of all active users
     */
    public Set<String> getUsers() {

        Set<String> userNames = new HashSet<>();
        for (Entry<String, User> entry : users.entrySet()) {
            userNames.add(entry.getValue().getName());
        }
        return userNames;
    }

    /**
     * Returns all active leases on the page, excluding one page edit session ID
     * (typically the requesting one).
     * 
     * @param excludeUid page edit session ID for which not to return its lease
     * @return mapping of content path to lease owner display name
     */
    public Map<String, String> getLeases(String excludeUid) {

        Map<String, String> leases = new HashMap<>();
        for (Entry<String, User> entry : users.entrySet()) {
            String uid = entry.getKey();
            if (!StringUtils.equals(uid, excludeUid)) {
                User user = users.get(uid);
                if (user != null) {
                    String leasePath = user.getLeasePath();
                    if (StringUtils.isNotBlank(leasePath)) {
                        leases.put(leasePath, user.getName());
                    }
                }
            }
        }
        return leases;
    }

    /**
     * Retrieve update history for a page after a given minimum time.
     * 
     * @param minTime will only return updates newer than this timestamp
     * @return list of all newer updates on this page
     */
    public List<Update> getUpdates(long minTime) {

        List<Update> updateList = new ArrayList<>();
        synchronized (this.updates) {
            Iterator<Update> updateIter = this.updates.iterator();
            while (updateIter.hasNext()) {
                Update update = updateIter.next();
                if (CollabUtil.checkExpired(update)) {
                    updateIter.remove();
                }
                if (update.getTime() > minTime) {
                    updateList.add(update);
                }
            }
        }
        return updateList;
    }

    /**
     * Inquiring if content is available for lease.
     * 
     * @param uid  page edit session ID inquiring about the lease
     * @param path path of the content to be potentially leased
     * @return if the content is either available for lease, or the active lease
     *         belongs to the same user
     */
    public boolean mayLease(String uid, String path) {

        String currentLesseeUid = lesseeUidByPath.get(path);
        if (currentLesseeUid != null) {
            User currentLessee = users.get(currentLesseeUid);
            if (currentLessee != null) {
                if (StringUtils.isBlank(uid)) {
                    return false;
                }
                User user = users.get(uid);
                return user != null && StringUtils.equals(user.getName(), currentLessee.getName());
            }
        }
        return true;
    }

    /**
     * Cleanup of expired page edit sessions. This occurs when a session gets
     * explicitly marked as expired (i.e. the user closed the page) or if no
     * heartbeat was received for too long duration.
     * 
     * @return dataset of removed page edit sessions and users which are now not
     *         active on the page anymore
     */
    public ExpirationResult removeExpired() {

        Set<String> removedUids = new HashSet<>();
        Set<String> removedUsers = new HashSet<>();
        synchronized (users) {
            Iterator<Map.Entry<String, User>> userIterator = users.entrySet().iterator();
            while (userIterator.hasNext()) {
                Map.Entry<String, User> entry = userIterator.next();
                User user = entry.getValue();
                String uid = entry.getKey();
                if (user.isExpired() || CollabUtil.checkExpired(user)) {
                    if (user.isExpired()) {
                        LOG.debug("Removing uid due to expiration mark: {} / {}", uid, user.getName());
                    } else {
                        LOG.debug("Removing uid due to time expiration: {} / {}", uid, user.getName());
                    }
                    userIterator.remove();
                    removedUids.add(uid);
                    removedUsers.add(user.getName());
                    // release
                    String leasePath = user.getLeasePath();
                    if (StringUtil.isNotBlank(leasePath)) {
                        // necessary to synchronize?
                        String lesseeUid = lesseeUidByPath.get(leasePath);
                        if (StringUtils.equals(uid, lesseeUid)) {
                            lesseeUidByPath.remove(leasePath);
                        }
                    }
                }
            }
        }

        // only output user as fully removed if no other uid is open for the same
        for (User user : users.values()) {

            if (removedUsers.contains(user.getName())) {
                removedUsers.remove(user.getName());
            }
        }

        return new ExpirationResult(removedUids, removedUsers);
    }

}
