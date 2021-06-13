package com.mwmd.core.servlets;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Rectangle;
import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.ImageHelper;
import com.day.image.Font;
import com.day.image.Layer;
import com.day.image.font.AbstractFont;
import com.mwmd.core.services.CollabService;
import com.mwmd.core.services.CollabSettings;
import com.mwmd.core.util.CollabUtil;

/**
 * Servlet to return thumbnail pictures for users. The thumbnail is based on the
 * AEM user profile picture of the user, or if empty falls back to a generated
 * icon using the user's name. Using {@link CollabSettings} this fallback
 * behavior can be enforced for all pictures.<br>
 * The pictures will be retrieved with the following URL pattern:
 * <code>/bin/aem-author-collab/profile.<i>userid</i>.png</code><br>
 * For performance it's recommended to ensure that Dispatcher cache is enabled
 * for these URLs. Each image is returned with a 1-hour expiration header, which
 * can be used to manage expiration in Dispatcher.
 * 
 * 
 */
@Component(service = { Servlet.class }, property = { "sling.servlet.paths=/bin/aem-author-collab/profile",
        "sling.servlet.methods=get" })
@ServiceDescription("Author Collab Profile Picture Servlet")
public class ProfilePictureServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ProfilePictureServlet.class);

    private static final String EXT_PNG = "png";

    private static final int WIDTH = 50;

    private static final int HEIGHT = 50;

    private static final String PNG_MIME = "image/png";

    private static final String EXPIRES_HEADER = "Expires";

    private static final long EXPIRES_HOUR = 3_600_000;

    @Reference
    private transient ResourceResolverFactory resolverFactory;

    @Reference
    private transient CollabSettings settings;

    @Reference
    private transient CollabService collab;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        RequestPathInfo info = request.getRequestPathInfo();
        if (!(StringUtils.equals(EXT_PNG, info.getExtension()) && StringUtils.isNotBlank(info.getSelectorString()))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(PNG_MIME);
        response.setDateHeader(EXPIRES_HEADER, CollabUtil.getTime() + EXPIRES_HOUR);

        String userId = info.getSelectorString();
        LOG.debug("picture for userId: {}", userId);

        try (ResourceResolver resolver = CollabUtil.getServiceResolver(resolverFactory)) {
            JackrabbitSession session = (JackrabbitSession) resolver.adaptTo(Session.class);
            Authorizable authorizable = session.getUserManager().getAuthorizable(userId);
            if (authorizable != null) {
                if (settings.isProfilePictures()) {
                    String picturePath = authorizable.getPath().concat("/profile/photos/primary/image");
                    Resource pictureRes = resolver.getResource(picturePath);
                    if (pictureRes != null) {
                        renderCustomProfile(response, pictureRes);
                        return;
                    }
                }
                // user name
                String familyName = StringUtils.trim(CollabUtil.getProfileProperty(authorizable, "familyName"));
                if ("null".equals(familyName)) {
                    familyName = null;
                }
                String givenName = StringUtils.trim(CollabUtil.getProfileProperty(authorizable, "givenName"));
                if ("null".equals(givenName)) {
                    givenName = null;
                }
                String initials;
                if (StringUtils.isAllBlank(givenName, familyName)) {
                    initials = StringUtils.substring(userId, 0, 2);
                } else {
                    if (StringUtils.isBlank(givenName)) {
                        initials = StringUtils.substring(familyName, 0, 2);
                    } else if (StringUtils.isBlank(familyName)) {
                        initials = StringUtils.substring(givenName, 0, 2);
                    } else {
                        initials = StringUtils.substring(givenName, 0, 1)
                                .concat(StringUtils.substring(familyName, 0, 1));
                    }
                }
                renderGenericProfile(response, StringUtils.upperCase(initials));
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (LoginException e) {
            LOG.error("Error retrieving service resolver", e);
        } catch (RepositoryException e) {
            LOG.error("Error retrieving user picture", e);
        }
    }

    private static void renderGenericProfile(ServletResponse response, String initials) throws IOException {

        GradientPaint gradient = new GradientPaint(0, 50, new Color(108, 165, 94), 50, 0, new Color(27, 79, 62));
        Layer layer = new Layer(WIDTH, HEIGHT, gradient);
        if (StringUtils.isNotBlank(initials)) {
            int align = AbstractFont.ALIGN_CENTER | AbstractFont.TTANTIALIASED;
            Font font = new Font("Arial", 14);
            layer.setPaint(Color.BLACK);
            layer.drawText(0, 18, 50, 32, initials, font, align, 0, 0);
        }
        layer.write(PNG_MIME, 0.9, response.getOutputStream());
        response.flushBuffer();
    }

    private static void renderCustomProfile(ServletResponse response, Resource imageRes) throws IOException {

        try {
            Layer layer = ImageHelper.createLayer(imageRes.adaptTo(Node.class));

            // bring to 1:1 ratio
            int newLength = Math.min(layer.getWidth(), layer.getHeight());
            int x = (layer.getWidth() - newLength) / 2;
            int y = (layer.getHeight() - newLength) / 2;
            Rectangle newSize = new Rectangle(x, y, newLength, newLength);
            layer.crop(newSize);

            layer.resize(WIDTH, HEIGHT);
            layer.write(PNG_MIME, 0.9, response.getOutputStream());
            response.flushBuffer();
        } catch (RepositoryException e) {
            LOG.error("Error rendering custom image", e);
        }
    }

}
