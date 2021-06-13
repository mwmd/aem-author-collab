# Live Authoring Collaboration for AEM Sites

This project is an extension for the Adobe Experience Manager Sites platform. It enhances the AEM Author environment to support live collaboration of content editors, providing both security during concurrent editing as well as additional features for real-time interaction. 

## Features

- **Always current page content**
  - Immediate display of content updates
  - Supports Add / Update / Remove / Move 
  - Supports Style System updates
  - Supports Responsive Layout updates
- **Component leasing**
  - Automated lease and release of components during editing
  - Lease owner indicator
  - Protection from concurrency overwrites
  - Support for full-page dialogs (i.e. mobile)
- **Listing of current page editors**
  - Always current in-page listing of active editors
  - Sites console preview of active editors
- **Live editor feedback**
  - Immediate annotation and sketch updates 
  - On-page notification for new annotations

Although these features are available for use in the current release, it should be noted that this is an early version and should be considered a technology demo rather than production ready.

## Supported platforms

* AEM as a Cloud Service
* AEM 6.5 SP8+ (On Premise / Managed Service / Cloud Manager)
* Supported by newer releases of Chrome, Edge, Firefox, Safari

## How to install

### Automated installation with your Maven build

The package is available as a Maven artifact and can simply get added to your project build. This is the simplest way and the only way supported with AEM as a Cloud Service.

This works with current Maven archetypes for AEM (packaging in `all` module) and older Maven archetypes for AEM (packaging in `ui.apps` module). The below instructions assume the current Maven archetype.

1. First add the Maven repository into the ***project*** `pom.xml`:

```xml
<repositories>
    [...]
    <repository>
        <id>wmd-repo</id>
        <url>http://repo.wmd-software.com/repository/maven-releases/</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>	
```

2. Then add the package dependency and version in the same file. If you later want to upgrade the integrated extension, all you need to update is this `version` property:

```xml
<dependencyManagement>
    <dependencies>
        [...]
        <dependency>
            <groupId>com.mwmd</groupId>
            <artifactId>aem-author-collab.all</artifactId>
            <version>0.0.24</version>
            <type>zip</type>
        </dependency>
    </dependencies>
</dependencyManagement>
```

3. Now reference this dependency in the ***project*.all** pom.xml:

```xml
<dependency>
    <groupId>com.mwmd</groupId>
    <artifactId>aem-author-collab.all</artifactId>
    <type>zip</type>
</dependency>
```

4. And finally embed the package in your main application package in the same file:

```xml
<plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    <extensions>true</extensions>
    [...]
    <configuration>
        [...]
        <embeddeds>
            [...]
            <embedded>
                <groupId>com.mwmd</groupId>
                <artifactId>aem-author-collab.all</artifactId>
                <type>zip</type>
                <target>/apps/yourproject-vendor-packages/container/install.author</target>
            </embedded>
        </embeddeds>
    </configuration>
</plugin>
```

That's it, you can now build your application as usual. The package gets embedded into yours and will be installed automatically with yours.

### Standalone build and installation

If you prefer to check out the source from GitHub and build it yourself for manual installation of the package, first check out the code and build it on the root folder `collab` with

```shell
mvn clean install
```

After the successful build, you can find the package here:

    ./aem-author-collab/all/target/aem-author-collab.all-<VERSION>.zip

You can then install this zip file manually in your AEM Author instance in Package Manager.

## Configuration

All following configuration is optional, the package can get installed and most features used without any configuration. These OSGi configuration can be made manually in the Web Console or deployed as OSGi configuration nodes in the repository.

### CSRF Filter exclusion for Beacon URL

For immediate detection of editors leaving the page, a Beacon request is used. Because this Beacon request is HTTP POST but doesn't carry a CSRF Token, AEM would block it. Without the below configuration, it will take longer (~1 min) to detect that the editor has left the page. This is an out of the box service and the value should be added to the existing configuration.

Configuration PID: `com.adobe.granite.csrf.impl.CSRFFilter` 

| Property | Type           | Value |
| --- | --- | --- |
| `filter.excluded.paths` | string (multi) | `/bin/collab/beacon` |

### Extension Features

With the below settings the behavior of the extension can get adjusted.

Configuration PID: `	com.mwmd.core.services.impl.CollabSettingsImpl` 

| Setting               | Property           | Type    | Default value |
| --------------------- | ------------------ | ------- | ------------- |
| Show profile pictures | `profile.pictures` | boolean | `true`        |

### Included configurations

The following configurations are automatically setup when installing the extension. They are listed here for information only.

| Configuration PID                                            | Purpose                                                      |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `org.apache.sling.jcr.repoinit.RepositoryInitializer~collab` | Creates a service user `collab-service` with read permissions for`/content` and `/home/users` |
| `org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~collab` | Makes the service user `collab-service` available to the extension |

## References

- [AEM Project Archetype](https://github.com/adobe/aem-project-archetype)
