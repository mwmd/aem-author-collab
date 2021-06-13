package com.mwmd.core.beans;

/**
 * Payload of a received Beacon request upon exit of a user from the page.
 */
public class Beacon {

    /**
     * page edit session ID
     */
    private String uid;

    /**
     * path of the exited page
     */
    private String pagePath;

    public String getUid() {
        return uid;
    }

    public String getPagePath() {
        return pagePath;
    }

}
