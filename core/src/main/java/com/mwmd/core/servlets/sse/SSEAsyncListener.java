package com.mwmd.core.servlets.sse;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.services.PushService;

/**
 * {@link AsyncListener} for SSE sessions
 */
public class SSEAsyncListener implements AsyncListener {

    private static final Logger LOG = LoggerFactory.getLogger(SSEAsyncListener.class);

    private PushService pushService;

    public SSEAsyncListener(PushService pushService) {

        this.pushService = pushService;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        LOG.trace("completed");
    }

    /**
     * Triggered server side by the timeout setting when establishing the session.
     */
    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        LOG.trace("timeout");
        final AsyncContext ctx = event.getAsyncContext();
        pushService.drop(ctx);
        ctx.complete();
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        LOG.trace("error");
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        LOG.trace("start");
    }

}
