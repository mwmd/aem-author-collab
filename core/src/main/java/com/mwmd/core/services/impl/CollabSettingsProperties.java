package com.mwmd.core.services.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import com.mwmd.core.services.CollabSettings;

/**
 * OSGi configuration for {@link CollabSettings}.
 */
@ObjectClassDefinition(name = "Collab Settings")
public @interface CollabSettingsProperties {

    @AttributeDefinition(name = "Show profile pictures", description = "Makes user profile pictures accessible to all logged in users")
    boolean profile_pictures() default true;

}
