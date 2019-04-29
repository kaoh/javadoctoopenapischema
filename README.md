# Introduction

This library uses a properties file and adds @Schema annotation to fields and classes with the description attribute 
if this annotation is not yet present.

# Features

The library in its current state was created for setting the data model documentation of entities or DTOs.

This is the first version and has the following limitations:

* No methods are scanned
* No tags are used

# Usage

## Cmd Line:

```
        javadoc -doclet de.ohmesoftware.propertiestoopenapischema.Converter -docletpath target/classes -sourcepath src/main/java de.example.my.model
```

## Maven

```
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <id>add-openapischema-description</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                        <phase>test</phase>
                        <configuration>
                            <doclet>de.ohmesoftware.propertiestoopenapischema.Converter</doclet>
                            <additionalOptions>-prefix rest.description -output src/main/resources/mydocs.properties</additionalOptions>
                            <debug>true</debug>
                            <docletPath>${project.build.outputDirectory}</docletPath>
                            <sourcepath>${project.basedir}/src/main/java/foo/bar/model</sourcepath>
                        </configuration>
                    </execution>
                </executions>
                <dependencies>
                    <dependency>
                        <groupId>de.ohmesoftware</groupId>
                        <artifactId>javadoctoproperties</artifactId>
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
