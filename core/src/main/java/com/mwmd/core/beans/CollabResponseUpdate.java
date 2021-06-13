package com.mwmd.core.beans;

import java.util.Collection;

/**
 * Push event data to notify of a content change and its type.
 */
public class CollabResponseUpdate {

    /**
     * content paths which were modified in the content change
     */
    private Collection<String> paths;

    /**
     * server timestamp of this update. Note that this will only be stable for the
     * same cluster node.
     */
    private long time;

    /**
     * paths in the page structure which should get refreshed. In case of updates,
     * this will be the updated node; in case of add/remove/move container paths are
     * passed.
     */
    private Collection<String> refreshPaths;

    /**
     * updated annotation data of the content page
     */
    private AnnotationInfo annotations;

    public CollabResponseUpdate(Collection<String> paths, Collection<String> refreshPaths, AnnotationInfo annotations,
            long time) {
        this.paths = paths;
        this.refreshPaths = refreshPaths;
        this.time = time;
        this.annotations = annotations;
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

    public AnnotationInfo getAnnotations() {
        return annotations;
    }

}
