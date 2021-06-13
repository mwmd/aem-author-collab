package com.mwmd.core.services;

/**
 * Configuration service for features of this extension.
 */
public interface CollabSettings {

    /**
     * If profile pictures should be shown. Alternatively, only user name based
     * placeholders are shown.
     * 
     * @return if pictures should be shown
     */
    boolean isProfilePictures();

}
