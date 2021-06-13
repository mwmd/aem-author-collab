package com.mwmd.core.filters;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.servlets.annotations.SlingServletFilter;
import org.apache.sling.servlets.annotations.SlingServletFilterScope;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.services.CollabService;
import com.mwmd.core.servlets.ResponseBodyWrapper;

/**
 * This filter extends the page preview information with additional data.
 * Because of limited extension points in the AEM preview pane generation, it
 * leaves the out-of-box functionality as-is but injects data into the generated
 * HTML markup.
 */
@Component
@SlingServletFilter(scope = SlingServletFilterScope.REQUEST, resourceTypes = "cq/gui/components/coral/admin/page/columnpreview", methods = HttpConstants.METHOD_GET)
public class PagePreviewFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(PagePreviewFilter.class);

    private static final String PREVIEW_LABEL_OPEN = "<coral-columnview-preview-label>";

    private static final String PREVIEW_LABEL_TEXT = "Live editors";

    private static final String PREVIEW_LABEL_CLOSE = "</coral-columnview-preview-label>";

    private static final String PREVIEW_VALUE_OPEN = "<coral-columnview-preview-value>";

    private static final String PREVIEW_VALUE_CLOSE = "</coral-columnview-preview-value>";

    @Reference
    private CollabService collab;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // nothing
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
        SlingHttpServletResponse slingResponse = (SlingHttpServletResponse) response;

        LOG.trace("Invoked for {}", slingRequest.getRequestURI());

        ResponseBodyWrapper wrapper = new ResponseBodyWrapper(slingResponse);

        chain.doFilter(request, wrapper);

        String text = wrapper.getText();
        int lastValueIndex = StringUtils.lastIndexOf(text, PREVIEW_VALUE_CLOSE);
        String suffix = slingRequest.getRequestPathInfo().getSuffix();
        if (lastValueIndex > 0 && StringUtils.isNotBlank(suffix)) {

            StringBuilder output = new StringBuilder(StringUtils.substringBeforeLast(text, PREVIEW_VALUE_CLOSE));
            output.append(PREVIEW_VALUE_CLOSE);
            output.append(PREVIEW_LABEL_OPEN).append(PREVIEW_LABEL_TEXT).append(PREVIEW_LABEL_CLOSE);
            output.append(PREVIEW_VALUE_OPEN);

            Set<String> userNames = new HashSet<>();
            if (collab.hasPage(suffix)) {
                Set<String> userIds = collab.getUsers(suffix);
                for (String userId : userIds) {
                    String userName = collab.getUserName(userId);
                    userNames.add(StringUtils.defaultIfBlank(userName, userId));
                }
            }
            if (!userNames.isEmpty()) {
                output.append(StringUtils.join(userNames, ", "));
            } else {
                output.append("-");
            }

            output.append(PREVIEW_VALUE_CLOSE);
            output.append(StringUtils.substringAfterLast(text, PREVIEW_VALUE_CLOSE));
            response.getWriter().print(output);
        } else {
            response.getWriter().print(text);
        }
        response.flushBuffer();
    }

    @Override
    public void destroy() {
        // nothing
    }

}
