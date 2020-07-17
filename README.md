# Introduction

Library and command line for scanning a source path and sub directories and adding or 
setting the `description` property of the Swagger OpenAPI 
`@io.swagger.v3.oas.annotations.media.Schema` annotation from the Javadoc.

# Features

The library in its current state was created for setting the data model documentation of entities or DTOs.

Supported:

 * Javadoc parsing for summary and description for field and getters. Field have priority over getters.
 * Min, Max, Size, Column, NotEmpty, NotNull annotations
 * HATEAOS URI descriptions following the HAL specification targeting Spring REST data (`hateaosHAL` flag).
 

Limitations:

* No internal enums are found.

# Usage

## Options

* `sourcePath`: source path
* `includes`: Restriction to include only the given file pattern. Multiples are separated by a comma.
* `excludes`: Restriction to exclude the given file pattern. Multiples are separated by a comma.
* `hateaosHAL`: In this case associations are rendered as links like common for HATEAOS.

__NOTE:__ The `excludes` and `includes` options is using a glob expression. Take note that to use a wildcard over path 
separators two asterisks have to be used. 

## Java

```
Enricher enricher = new Enricher(buildPath(User.class.getPackage().getName()),
            Collections.singleton("**User.java"), Collections.singleton("**.bak"), false);
enricher.enrich();
```

## Maven

```xml
    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.0.0</version>
        <executions>
          <execution>
            <goals>
              <goal>java</goal>
            </goals>
            <phase>generate-sources</phase>
          </execution>
        </executions>
        <configuration>
          <mainClass>de.ohmesoftware.javadoctoopenapischema.Enricher</mainClass>
          <includePluginDependencies>true</includePluginDependencies>
          <arguments>
            <argument>-sourcePath</argument>
            <argument>src/test/java/my/domain/project/model</argument>
            <argument>-excludes</argument>
            <argument>**.bak</argument>
            <argument>-includes</argument>
            <argument>**User.java</argument>
          </arguments>
        </configuration>
        <dependencies>
            <dependency>
                <groupId>de.ohmesoftware</groupId>
                <artifactId>javadoctoopenapischema</artifactId>
                <version>0.0.1</version>
            </dependency>
        </dependencies>
    </plugin>
```

__NOTE:__ It might be necessary because of a Log4j2 or exec-maven-plugin issue to add 
`<cleanupDaemonThreads>false</cleanupDaemonThreads>` to the `<configuration>` section.
        
__NOTE:__ In case the central repository is not used, he `exec-maven-plugin` cannot be found. Include in this case:
 
~~~xml
    <repositories>
        ...
        <repository>
            <id>central</id>
            <url>https://repo1.maven.org/maven2/</url>
        </repository>
    </repositories>
~~~

# Deployment + Release

See https://central.sonatype.org/pages/apache-maven.html


# For Snapshots

    mvn clean deploy

## For Releases

```
mvn release:clean release:prepare
mvn release:perform
```

Release the deployment using Nexus See https://central.sonatype.org/pages/releasing-the-deployment.html
Or do it with Maven:

```
cd target/checkout
mvn nexus-staging:release
```
