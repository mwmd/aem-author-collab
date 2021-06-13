package com.mwmd.core.beans;

import java.util.Collections;
import java.util.Set;

/**
 * Holds data collected during periodic cleanup of page edit sessions.
 */
public class ExpirationResult {

    /**
     * page edit session IDs removed during cleanup
     */
    private Set<String> expiredUids;

    /**
     * IDs of users that should be removed from this page
     */
    private Set<String> exitUserIds;

    public ExpirationResult(Set<String> expiredUids, Set<String> exitUserIds) {
        this.expiredUids = Collections.unmodifiableSet(expiredUids);
        this.exitUserIds = Collections.unmodifiableSet(exitUserIds);
    }

    public Set<String> getExpiredUids() {
        return expiredUids;
    }

    public Set<String> getExitUserIds() {
        return exitUserIds;
    }

}
