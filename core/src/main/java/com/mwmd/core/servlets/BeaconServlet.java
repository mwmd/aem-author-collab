package com.mwmd.core.servlets;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mwmd.core.beans.Beacon;
import com.mwmd.core.listeners.EventProducer;
import com.mwmd.core.services.CollabService;

/**
 * Servlet receiving a Beacon HTTP POST request when users leave the page.
 * Because Beacon payload cannot contain a CSRF token, excluding of the servlet
 * path <code>/bin/aem-author-collab/beacon</code> in the AEM CSRF Filter is mandatory for
 * this functionality to work.
 */
@Component(service = { Servlet.class }, property = { "sling.servlet.paths=/bin/aem-author-collab/beacon",
        "sling.servlet.methods=post" })
@ServiceDescription("Author Collab Beacon Servlet")
public class BeaconServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(BeaconServlet.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Reference
    private transient CollabService collab;

    @Reference
    private transient EventAdmin eventAdmin;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control", "no-store");

        try {
            Beacon beacon = GSON.fromJson(request.getReader(), Beacon.class);
            if (beacon == null) {
                LOG.warn("Received empty payload");
                return;
            }
            LOG.debug("Received beacon for user {} on page {}", beacon.getUid(), beacon.getPagePath());
            if (StringUtils.isNoneBlank(beacon.getPagePath(), beacon.getUid())) {
                new EventProducer(eventAdmin).buildExit(beacon.getPagePath(), beacon.getUid());
            }
        } catch (JsonParseException e) {
            LOG.error("Error parsing JSON payload", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
