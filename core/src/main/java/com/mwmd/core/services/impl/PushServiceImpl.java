package com.mwmd.core.services.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mwmd.core.beans.AnnotationInfo;
import com.mwmd.core.beans.AsyncUidContext;
import com.mwmd.core.beans.CollabResponseLease;
import com.mwmd.core.beans.CollabResponseUpdate;
import com.mwmd.core.beans.CollabResponseUser;
import com.mwmd.core.beans.Message;
import com.mwmd.core.services.CollabSettings;
import com.mwmd.core.services.PushService;

@Component(service = PushService.class)
public class PushServiceImpl implements PushService {

    private static final Logger LOG = LoggerFactory.getLogger(PushServiceImpl.class);

    private static final Gson GSON = new GsonBuilder().create();

    private static final String SSE_PING_JOB_NAME = "collabSSEPingJob";

    private static final int SSE_PING_JOB_SECONDS = 15;

    @Reference
    private CollabSettings settings;

    @Reference
    private Scheduler scheduler;

    /**
     * lookup map for SSE contexts. Organized first by page path, then by page edit
     * session ID.
     */
    private Map<String, Map<String, AsyncUidContext>> contexts = new ConcurrentHashMap<>();

    /**
     * collection of all contexts across all pages
     */
    private Set<AsyncUidContext> allContexts = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void lease(String page, String uid, String path, String userId, String userName) {

        Message msg = new Message();
        CollabResponseUser user = new CollabResponseUser(userId, userName);
        Set<CollabResponseLease> leases = new HashSet<>();
        leases.add(new CollabResponseLease(path, user));
        msg.setLeases(leases);
        sendMessage(page, msg, uid);
    }

    @Override
    public void release(String page, String uid, String path) {

        Message msg = new Message();
        Set<CollabResponseUpdate> releases = new HashSet<>();
        Set<String> paths = new HashSet<>();
        paths.add(path);
        releases.add(new CollabResponseUpdate(paths, null, null, 0));
        msg.setReleases(releases);
        sendMessage(page, msg, uid);
    }

    @Override
    public void update(String page, Collection<String> paths, Collection<String> refreshPaths,
            AnnotationInfo annotations, long time) {

        Message msg = new Message();
        Set<CollabResponseUpdate> updates = new HashSet<>();
        AnnotationInfo annotationInfo = annotations != null && annotations.getCount() > 0 ? annotations : null;
        updates.add(new CollabResponseUpdate(paths, refreshPaths, annotationInfo, time));
        msg.setUpdates(updates);
        sendMessage(page, msg, null);
    }

    @Override
    public void exit(String page, Set<String> userIds) {

        Message msg = new Message();
        msg.setUserExit(userIds);
        sendMessage(page, msg, null);
    }

    @Override
    public void enter(String page, Map<String, String> userIdNames) {

        Message msg = new Message();
        Set<CollabResponseUser> users = new HashSet<>();
        for (Map.Entry<String, String> user : userIdNames.entrySet()) {
            users.add(new CollabResponseUser(user.getKey(), user.getValue()));
        }
        msg.setUserEnter(users);
        sendMessage(page, msg, null);
    }

    @Override
    public void register(String page, String uid, AsyncContext ctx) {

        // drop any existing context with this uid (in case of browser resume)
        drop(uid);
        Map<String, AsyncUidContext> pageContexts = this.contexts.get(page);
        if (pageContexts == null) {
            synchronized (this.contexts) {
                pageContexts = this.contexts.get(page);
                if (pageContexts == null) {
                    pageContexts = new ConcurrentHashMap<>();
                    this.contexts.put(page, pageContexts);
                }
            }
        }
        AsyncUidContext uidCtx = new AsyncUidContext(ctx, uid);
        pageContexts.put(uid, uidCtx);
        allContexts.add(uidCtx);
    }

    @Override
    public void drop(AsyncContext dropCtx) {

        synchronized (this.contexts) {
            for (Map<String, AsyncUidContext> pageContexts : this.contexts.values()) {
                for (AsyncUidContext ctx : pageContexts.values()) {
                    if (dropCtx.equals(ctx.getCtx())) {
                        pageContexts.remove(ctx.getUid());
                        allContexts.remove(ctx);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void drop(String uid) {

        synchronized (this.contexts) {
            for (Map<String, AsyncUidContext> pageContexts : this.contexts.values()) {
                AsyncUidContext ctx = pageContexts.remove(uid);
                if (ctx != null) {
                    allContexts.remove(ctx);
                    break;
                }
            }
        }
    }

    /**
     * Sends a message to all contexts of a page.
     * 
     * 
     * @param page      path of the page to send the message to
     * @param msg       message text
     * @param ignoreUid optional page edit session ID to exclude from this message
     */
    private void sendMessage(String page, Message msg, String ignoreUid) {

        // broadcast to all contexts of the page
        Map<String, AsyncUidContext> pageContexts = this.contexts.get(page);
        if (pageContexts != null && !pageContexts.isEmpty()) {
            sendText(pageContexts.values(), msgToText(msg), ignoreUid);
        }
    }

    /**
     * Sends a message to a specific SSE context.
     * 
     * @param context SSE context to receive the message
     * @param msg     message text
     */
    private void sendMessage(AsyncUidContext context, Message msg) {

        // send only to provided context
        if (context != null) {
            Set<AsyncUidContext> pageContexts = new HashSet<>();
            pageContexts.add(context);
            sendText(pageContexts, msgToText(msg), null);
        }
    }

    /**
     * Sends a text message to multiple SSE contexts. The message is sent
     * concurrently.
     * 
     * @param contexts  all SSE contexts to receive the message
     * @param text      message text
     * @param ignoreUid optional page edit session ID to exclude from this message
     */
    private void sendText(Collection<AsyncUidContext> contexts, String text, String ignoreUid) {

        if (contexts != null && !contexts.isEmpty() && StringUtils.isNotBlank(text)) {
            LOG.trace("Sending text to {} contexts: {}", contexts.size(), text);
            Set<AsyncUidContext> removeContexts = Collections.synchronizedSet(new HashSet<>());
            Set<AsyncUidContext> messageContexts = new HashSet<>(contexts);

            messageContexts.parallelStream().forEach((AsyncUidContext ctx) -> {
                if (!StringUtils.equals(ctx.getUid(), ignoreUid)) {
                    try {
                        ServletResponse response = ctx.getCtx().getResponse();
                        PrintWriter out = response.getWriter();
                        out.print(StringUtils.trim(text));
                        out.print("\n\n\n");
                        out.flush();
                        response.flushBuffer();
                    } catch (IOException e) {
                        LOG.debug("Error sending message to context", e);
                        removeContexts.add(ctx);
                    }
                }
            });
            for (AsyncUidContext ctx : removeContexts) {
                drop(ctx.getUid());
                ctx.getCtx().complete();
            }
        }
    }

    /**
     * Initializes a scheduler which pings all SSE contexts periodically.
     */
    @Activate
    protected void activate() {

        ScheduleOptions opts = scheduler.NOW(-1, SSE_PING_JOB_SECONDS).name(SSE_PING_JOB_NAME);
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (!allContexts.isEmpty()) {
                    if (LOG.isTraceEnabled()) {
                        List<String> uids = new ArrayList<>();
                        for (AsyncUidContext ctx : allContexts) {
                            uids.add(ctx.getUid());
                        }
                        LOG.trace("Broadcasting SSE ping to: {}", Arrays.toString(uids.toArray()));
                    }
                    sendText(allContexts, "event: ping\ndata: ", null);
                }
            }
        }, opts);
    }

    /**
     * Tear down of the ping scheduler and closing of all SSE contexts.
     */
    @Deactivate
    protected void deactivate() {

        if (!scheduler.unschedule(SSE_PING_JOB_NAME)) {
            LOG.error("Couldn't stop the SSE Ping job");
        }

        allContexts.parallelStream().forEach((AsyncUidContext ctx) -> {
            try {
                ctx.getCtx().complete();
            } catch (Exception e) {
                LOG.error("Error closing SSE context", e);
            }
        });
    }

    @Override
    public void setup(AsyncContext asyncCtx, Set<CollabResponseLease> leases, Set<CollabResponseUpdate> updates,
            Set<CollabResponseUser> users) {

        AsyncUidContext ctx = null;
        synchronized (this.contexts) { // necessary?
            for (AsyncUidContext c : allContexts) {
                if (asyncCtx.equals(c.getCtx())) {
                    ctx = c;
                    break;
                }
            }
        }
        if (ctx == null) {
            LOG.warn("Context not found, cannot send setup message");
            return;
        }
        Message msg = new Message();
        msg.setSetup(true);
        msg.setLeases(leases);
        msg.setUpdates(updates);
        msg.setUserEnter(users);
        sendMessage(ctx, msg);
    }

    /**
     * Converts a message object to JSON text.
     * 
     * @param msg message to convert
     * @return JSON text
     */
    private static String msgToText(Message msg) {

        String text = "data: ".concat(GSON.toJson(msg));
        if (StringUtils.contains(text, '\n')) {
            LOG.warn("Payload contains a line break, which can impact the SSE payload: {}", text);
        }
        return text;
    }
}
