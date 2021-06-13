package com.mwmd.core.servlets.sse;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardContextSelect;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardServletAsyncSupported;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardServletPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.beans.CollabResponseLease;
import com.mwmd.core.beans.CollabResponseUpdate;
import com.mwmd.core.beans.CollabResponseUser;
import com.mwmd.core.beans.Update;
import com.mwmd.core.services.CollabService;
import com.mwmd.core.services.CollabSettings;
import com.mwmd.core.services.PushService;
import com.mwmd.core.util.CollabUtil;

/**
 * Servlet to provide Server-Sent Event functionality. Apache Sling doesn't
 * support asynchronous servlets with long running responses, therefore this
 * Servlet isn't a Sling Servlet but instead a generic OSGI Whiteboard
 * Servlet.<br>
 * Although SSE servlet responses by design run until the connection is closed,
 * there can be many reasons for connection interrupts that terminate the
 * response earlier. Browsers implement a SSE auto-restart behavior, which is
 * why this Servlet may receive multiple calls for the same page edit session
 * ID.
 */
@Component(service = Servlet.class, scope = ServiceScope.PROTOTYPE)
@HttpWhiteboardServletPattern("/bin/aem-author-collab/sse")
@HttpWhiteboardContextSelect("(osgi.http.whiteboard.context.name=org.apache.sling)")
@HttpWhiteboardServletAsyncSupported
@ServiceDescription("Author Collab SSE Servlet")
public class ServerSideEventServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ServerSideEventServlet.class);

    // 9 min max timeout
    private static final int TIMEOUT = 540_000;

    @Reference
    private transient PushService push;

    @Reference
    private transient CollabSettings settings;

    @Reference
    private transient CollabService collab;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pagePath = request.getParameter("page");
        String uid = request.getParameter("uid");
        LOG.trace("Invoked for page {} with uid {}", pagePath, uid);

        if (StringUtils.isAnyBlank(pagePath, uid)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!request.isAsyncSupported()) {
            LOG.error("Async not supported");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setHeader("Dispatcher", "no-cache");
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");

        try {
            collab.addUser(pagePath, request.getRemoteUser(), uid);

            AsyncContext ctx = request.startAsync();
            ctx.setTimeout(TIMEOUT);
            ctx.addListener(new SSEAsyncListener(push));
            push.register(pagePath, uid, ctx);

            // generate setup message for new SSE session
            // users
            Set<String> userIds = collab.getUsers(pagePath);
            Set<CollabResponseUser> users = new HashSet<>();
            for (String userId : userIds) {
                users.add(new CollabResponseUser(userId, collab.getUserName(userId)));
            }
            // leases
            Map<String, String> currentLeases = collab.getLeases(pagePath, uid);
            Set<CollabResponseLease> leases = new HashSet<>();
            for (Entry<String, String> l : currentLeases.entrySet()) {
                CollabResponseUser user = new CollabResponseUser(l.getValue(), collab.getUserName(l.getValue()));
                leases.add(new CollabResponseLease(l.getKey(), user));
            }
            // updates (last 20 seconds)
            List<Update> lastUpdates = collab.getUpdates(pagePath, CollabUtil.getTime(20));
            Set<CollabResponseUpdate> updates = new HashSet<>();
            for (Update lastUpdate : lastUpdates) {
                updates.add(new CollabResponseUpdate(lastUpdate.getPaths(), lastUpdate.getRefreshPaths(), null,
                        lastUpdate.getTime()));
            }
            push.setup(ctx, leases, updates, users);
        } catch (Exception e) {
            LOG.error("Error in async servlet", e);
        }
    }

}
