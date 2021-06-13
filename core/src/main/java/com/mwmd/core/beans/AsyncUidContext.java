package com.mwmd.core.beans;

import javax.servlet.AsyncContext;

/**
 * Storage structure for active {@link AsyncContext} sessions.
 */
public class AsyncUidContext {

    /**
     * context of the open session, used to stream messages
     */
    private AsyncContext ctx;

    /**
     * page edit session ID
     */
    private String uid;

    public AsyncUidContext(AsyncContext ctx, String uid) {
        this.ctx = ctx;
        this.uid = uid;
    }

    public AsyncContext getCtx() {
        return ctx;
    }

    public String getUid() {
        return uid;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ctx == null) ? 0 : ctx.hashCode());
        result = prime * result + ((uid == null) ? 0 : uid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AsyncUidContext other = (AsyncUidContext) obj;
        if (ctx == null) {
            if (other.ctx != null) {
                return false;
            }
        } else if (!ctx.equals(other.ctx)) {
            return false;
        }
        if (uid == null) {
            if (other.uid != null) {
                return false;
            }
        } else if (!uid.equals(other.uid)) {
            return false;
        }
        return true;
    }

}
