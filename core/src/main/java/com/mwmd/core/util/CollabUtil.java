package com.mwmd.core.util;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mwmd.core.beans.Update;
import com.mwmd.core.beans.User;
import com.mwmd.core.util.Constants;

/**
 * Utility methods reused across the extension code base.
 */
public final class CollabUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CollabUtil.class);

    /**
     * period when a user is considered stale and expired after not receiving a
     * heartbeat
     */
    private static final long USER_EXPIRATION_TIME = 70_000L;

    /**
     * period after which updates are removed from the page update history
     */
    private static final long UPDATE_EXPIRATION_TIME = 70_000L;

    private CollabUtil() {
        // nothing
    }

    /**
     * Get current timestamp in milliseconds.
     * 
     * @return current timestamp in milliseconds
     */
    public static long getTime() {

        return System.currentTimeMillis();
    }

    /**
     * Get current timestamp in milliseconds, reduced by a number of seconds
     * 
     * @param subtractSeconds seconds to remove from the current time
     * @return timestamp in milliseconds
     */
    public static long getTime(int subtractSeconds) {

        return System.currentTimeMillis() - subtractSeconds * 1000;
    }

    /**
     * Calculates if a user's last heartbeat was outside the expiration window.
     * 
     * @param user user to check for expiration
     * @return if the user is expired
     */
    public static boolean checkExpired(User user) {

        return user.getLastPing() + USER_EXPIRATION_TIME < getTime();
    }

    /**
     * Calculates if an update occurred outside the expiration window.
     * 
     * @param update update to check for expiration
     * @return if the update is expired
     */
    public static boolean checkExpired(Update update) {

        return update.getTime() + UPDATE_EXPIRATION_TIME < getTime();
    }

    /**
     * Creates a new {@link ResourceResolver} using the extension's service user.
     * 
     * @param factory service reference
     * @return opened service user {@link ResourceResolver}
     * @throws LoginException
     */
    public static ResourceResolver getServiceResolver(ResourceResolverFactory factory) throws LoginException {

        Map<String, Object> factoryParams = new HashMap<>();
        factoryParams.put(ResourceResolverFactory.SUBSERVICE, Constants.SERVICE_USER);
        return factory.getServiceResourceResolver(factoryParams);
    }

    /**
     * Reads a String property from an AEM user profile.
     * 
     * @param user         AEM user
     * @param propertyName profile property name
     * @return property value or null
     */
    public static String getProfileProperty(Authorizable user, String propertyName) {

        String value = null;
        if (user != null && StringUtils.isNotBlank(propertyName)) {
            try {
                Value[] property = user.getProperty("profile/".concat(propertyName));
                if (property != null && property.length > 0) {
                    value = property[0].getString();
                }
            } catch (RepositoryException e) {
                LOG.error("Error retrieving profile property", e);
            }
        }
        return value;
    }

}
