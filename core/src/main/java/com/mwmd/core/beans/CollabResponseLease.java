package com.mwmd.core.beans;

/**
 * Push event data to indicate who holds a lease on a content path.
 */
public class CollabResponseLease {

    /**
     * leased content path
     */
    private String path;

    /**
     * user owning the lease
     */
    private CollabResponseUser user;

    public CollabResponseLease(String path, CollabResponseUser user) {
        this.path = path;
        this.user = user;
    }

    public String getPath() {
        return path;
    }

    public CollabResponseUser getUser() {
        return user;
    }

}
