# Introduction

Library and command line for scanning a source path and sub directories and adding or 
setting the `description` property of the Swagger OpenAPI 
`@io.swagger.v3.oas.annotations.media.Schema annotation` from the Javadoc.

# Features

The library in its current state was created for setting the data model documentation of entities or DTOs.

This is the first version and has the following limitations:

* No methods are scanned
* No tags are used

# Usage

## Java

```
Enricher enricher = new Enricher(buildPath(User.class.getPackage().getName()),
            Collections.singleton("**User.java"), Collections.singleton("**.bak"));
    enricher.enrich();.model
```

## Maven

```
    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>1.6.0</version>
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
                <version>0.0.1-SNAPSHOT</version>
            </dependency>
        </dependencies>
    </plugin>
```

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
Or alternatively do it with Maven:

```
cd target/checkout
mvn nexus-staging:release
```
