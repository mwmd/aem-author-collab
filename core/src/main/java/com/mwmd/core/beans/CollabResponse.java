package com.mwmd.core.beans;

/**
 * Response payload for a {@link CollabRequest}. If a lease was requested, can
 * indicate a rejection.
 */
public class CollabResponse {

    /**
     * will be <code>false</code> if a requested lease cannot be given.
     */
    private Boolean rejected;

    public Boolean getRejected() {
        return rejected;
    }

    public void setRejected(Boolean rejected) {
        this.rejected = rejected;
    }

}
