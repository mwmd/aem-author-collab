package com.mwmd.core.beans;

import java.util.Collection;

import com.mwmd.core.util.CollabUtil;

/**
 * Internal structure to keep update history.
 */
public class Update {

    /**
     * content paths that got modified
     */
    private Collection<String> paths;

    /**
     * content paths within the page that should get refreshed
     */
    private Collection<String> refreshPaths;

    /**
     * timestamp when this AEM instance observed the change
     */
    private long time;

    public Update(Collection<String> paths, Collection<String> refreshPaths) {

        this.paths = paths;
        this.refreshPaths = refreshPaths;
        time = CollabUtil.getTime();
    }

    public Collection<String> getPaths() {
        return paths;
    }

    public long getTime() {
        return time;
    }

    public Collection<String> getRefreshPaths() {
        return refreshPaths;
    }

}
