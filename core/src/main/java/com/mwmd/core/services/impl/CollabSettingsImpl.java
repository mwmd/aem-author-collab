package com.mwmd.core.services.impl;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;

import com.mwmd.core.services.CollabSettings;

@Component
@Designate(ocd = CollabSettingsProperties.class)
public class CollabSettingsImpl implements CollabSettings {

    private CollabSettingsProperties properties;

    @Activate
    protected void activate(CollabSettingsProperties properties) {

        this.properties = properties;
    }

    @Override
    public boolean isProfilePictures() {

        return properties.profile_pictures();
    }

}
