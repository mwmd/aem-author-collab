package com.mwmd.core.beans;

import com.mwmd.core.util.CollabUtil;

/**
 * Internal data structure containing user data.
 */
public class User {

    /**
     * display name of the user
     */
    private String name;

    /**
     * timestamp of last received heartbeat
     */
    private long lastPing;

    /**
     * content path leased by this user in this page edit session
     */
    private String leasePath;

    /**
     * flag indicating if the data is expired and needs to get cleaned up
     */
    private boolean expired;

    public User(String name) {

        this.name = name;
        this.lastPing = CollabUtil.getTime();
    }

    public String getName() {
        return name;
    }

    public long getLastPing() {
        return lastPing;
    }

    public String getLeasePath() {
        return leasePath;
    }

    public void setLeasePath(String leasePath) {
        this.leasePath = leasePath;
    }

    public void ping() {
        this.expired = false;
        this.lastPing = CollabUtil.getTime();
    }

    public boolean isExpired() {

        return expired;
    }

    public void setExpired(boolean expired) {

        this.expired = expired;
    }

}
