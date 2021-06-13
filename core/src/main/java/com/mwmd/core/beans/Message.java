package com.mwmd.core.beans;

import java.util.Set;

/**
 * Data structure for a server push event message.
 */
public class Message {

    /**
     * content leases for this page. Depending on context, can indicate a new lease
     * or all active ones.
     */
    private Set<CollabResponseLease> leases;

    /**
     * return of previously active content leases
     */
    private Set<CollabResponseUpdate> releases;

    /**
     * updates collected on the server. Should trigger content refreshes.
     */
    private Set<CollabResponseUpdate> updates;

    /**
     * user IDs that have left the page
     */
    private Set<String> userExit;

    /**
     * user IDs on this page. Depending on context, can indicate a new entered user
     * or all active ones.
     */
    private Set<CollabResponseUser> userEnter;

    /**
     * returns <code>true</code> if this message is sent as initial status during
     * connection setup
     */
    private Boolean setup;

    public Set<CollabResponseLease> getLeases() {
        return leases;
    }

    public void setLeases(Set<CollabResponseLease> leases) {
        this.leases = leases;
    }

    public Set<CollabResponseUpdate> getReleases() {
        return releases;
    }

    public void setReleases(Set<CollabResponseUpdate> releases) {
        this.releases = releases;
    }

    public Boolean getSetup() {
        return setup;
    }

    public Set<CollabResponseUpdate> getUpdates() {
        return updates;
    }

    public void setUpdates(Set<CollabResponseUpdate> updates) {
        this.updates = updates;
    }

    public Set<String> getUserExit() {
        return userExit;
    }

    public void setUserExit(Set<String> userExit) {
        this.userExit = userExit;
    }

    public Set<CollabResponseUser> getUserEnter() {
        return userEnter;
    }

    public void setUserEnter(Set<CollabResponseUser> userEnter) {
        this.userEnter = userEnter;
    }

    public void setSetup(Boolean setup) {
        this.setup = setup;
    }

}
