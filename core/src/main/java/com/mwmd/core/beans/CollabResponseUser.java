package com.mwmd.core.beans;

/**
 * User data combining user ID and display name.
 */
public class CollabResponseUser {

    /**
     * display name of the user
     */
    private String name;

    /**
     * id of the user, typically AEM user name
     */
    private String id;

    public CollabResponseUser(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

}
