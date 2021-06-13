package com.mwmd.core.util;

import com.day.cq.commons.jcr.JcrConstants;

/**
 * Constants to use across the code base.
 */
public final class Constants {

    public static final String CONTENT_DAM = "/content/dam/";

    public static final String SLASH = "/";

    public static final String JCR_CONTENT_SUFFIX = SLASH.concat(JcrConstants.JCR_CONTENT);

    public static final String JCR_CONTENT_INFIX = SLASH.concat(JcrConstants.JCR_CONTENT).concat(SLASH);

    public static final String NN_CQ_ANNOTATIONS = "cq:annotations";
	
	public static final String SERVICE_USER = "aemAuthorCollabService";

    private Constants() {
        // nothing
    }

}
