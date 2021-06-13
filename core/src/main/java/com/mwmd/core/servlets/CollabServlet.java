package com.mwmd.core.servlets;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.wcm.api.NameConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mwmd.core.beans.CollabRequest;
import com.mwmd.core.beans.CollabResponse;
import com.mwmd.core.listeners.EventProducer;
import com.mwmd.core.services.CollabService;
import com.mwmd.core.services.CollabSettings;

/**
 * Servlet receiving user interactions with the collaboration framework. Because
 * only one AEM instance will receive this call, this Servlet spawns distributed
 * {@link Event} instances for processing on each potential cluster node.
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = NameConstants.NT_PAGE, selectors = "author-collab", extensions = "json", methods = HttpConstants.METHOD_POST)
@ServiceDescription("Author Collab Status Servlet")
public class CollabServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(CollabServlet.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Reference
    private transient CollabService collab;

    @Reference
    private transient CollabSettings settings;

    @Reference
    private transient EventAdmin eventAdmin;

    @Override
    protected void doPost(final SlingHttpServletRequest req, final SlingHttpServletResponse resp)
            throws ServletException, IOException {

        resp.setHeader("Cache-Control", "no-store");

        try {
            CollabRequest request = GSON.fromJson(req.getReader(), CollabRequest.class);

            String page = req.getRequestPathInfo().getResourcePath();
            String uid = request.getUid();
            if (StringUtils.isAnyBlank(page, uid)) {
                LOG.warn("Called with missing payload: uid={} , page={}", uid, page);
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            CollabResponse response = new CollabResponse();

            String userId = req.getRemoteUser();

            if (request.isRelease()) {
                LOG.info("release producer {} {} {}", page, uid, userId);
                new EventProducer(eventAdmin).buildRelease(page, uid, userId);
            } else {
                // adjust namespace
                String leasePath = StringUtils.replace(request.getLeasePath(), "/_jcr_", "/jcr:");
                if (StringUtils.isBlank(leasePath) || collab.mayLease(page, uid, leasePath)) {
                    new EventProducer(eventAdmin).buildLease(page, uid, userId, leasePath);
                } else {
                    response.setRejected(true);
                }
            }
            resp.setContentType("application/json");
            GSON.toJson(response, resp.getWriter());
        } catch (JsonParseException e) {
            LOG.error("Error parsing JSON payload", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
