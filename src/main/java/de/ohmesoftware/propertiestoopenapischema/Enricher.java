package de.ohmesoftware.propertiestoopenapischema;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.sun.javadoc.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Enriches the passed source path and sub directories and adds @Schema annotation if missing to field annotations.
 *
 * @author Karsten Ohme
 */
public class Enricher {

    /**
     * The property file to use.
     */
    private String propertyFile;

    /**
     * The source path to enrich.
     */
    private String sourcePath;

    /**
     * The includes.
     */
    private Set<String> includes;

    /**
     * The excludes.
     */
    private Set<String> excludes;

    /**
     * Constructor.
     * @param propertyFile The property file to use.
     * @param sourcePath The source path to enrich.
     * @param includes The includes.
     * @param excludes The excludes.
     */
    public Enricher(String propertyFile, String sourcePath, Set<String> includes, Set<String> excludes) {
        this.propertyFile = propertyFile;
        this.sourcePath = sourcePath;
        this.includes = includes;
        this.excludes = excludes;
    }

    public void enrich() {
        Properties properties = new Properties();
        try (FileReader fileReader = new FileReader(propertyFile)) {
            properties.load(fileReader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Input property file is not correct.", e);
        }


        try {
            Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    if (includes != null && !includes.isEmpty()) {
                        boolean handle = false;
                        for (String include : includes) {
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(include);
                            if (pathMatcher.matches(path) && path.toFile().isFile()) {
                                // handle
                                handle = true;
                                break;
                            }
                        }
                        if (!handle) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    if (excludes != null && !excludes.isEmpty()) {
                        for (String exclude : excludes) {
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(exclude);
                            if (pathMatcher.matches(path)) {
                                // skip sub dirs if a directory
                                if (path.toFile().isDirectory()) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                // ignore if excludes
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    }
                    // handle
    handleSchema(properties, path);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Could not walk ", e);
        }

        CompilationUnit compilationUnit = JavaParser.parse("class A { }");
        Optional<ClassOrInterfaceDeclaration> classA = compilationUnit.getClassByName("A");
    }

    private void handleSchema(Properties properties, Path path) {

    }

}
