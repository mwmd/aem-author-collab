package com.mwmd.core.beans;

/**
 * Payload of a received Lease request. The request can either signal a lease or
 * release of a content path; or be a simple heart beat without lease operation.
 */
public class CollabRequest {

    /**
     * page editing session ID
     */
    private String uid;

    /**
     * path of requested or current lease content
     */
    private String leasePath;

    /**
     * indicates if an active leased should be returned
     */
    private boolean release;

    public String getUid() {
        return uid;
    }

    public String getLeasePath() {
        return leasePath;
    }

    public boolean isRelease() {
        return release;
    }

}
