package de.ohmesoftware.propertiestoopenapischema;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * Enriches the passed source path and sub directories and adds @Schema annotation if missing to field annotations.
 *
 * @author <a href="mailto:k_o_@users.sourceforge.net">Karsten Ohme
 * (k_o_@users.sourceforge.net)</a>
 */
public class Enricher {

    private static final String SCHEMA_ANNOTATION_SIMPLE_NAME = "Schema";
    private static final String SCHEMA_ANNOTATION_CLASS = "io.swagger.v3.oas.annotations.media.Schema";
    private static final String EMPTY_STRING = "";
    private static final String DOT_STRING = ".";
    private static final String QUOTATION_MARK_STRING = "\"";
    private static final String SCHEMA_DESCRIPTION = "description";

    /**
     * The property file to use.
     */
    private String propertyFile;

    /**
     * The prefix all properties are having.
     */
    private String propertyPrefix = EMPTY_STRING;

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
     *
     * @param propertyFile   The property file to use.
     * @param propertyPrefix The prefix all properties are having.
     * @param sourcePath     The source path to enrich.
     * @param includes       The includes.
     * @param excludes       The excludes.
     */
    public Enricher(String propertyFile, String propertyPrefix,
                    String sourcePath, Set<String> includes, Set<String> excludes) {
        this.propertyFile = propertyFile;
        this.propertyPrefix = propertyPrefix;
        this.sourcePath = sourcePath;
        this.includes = includes;
        this.excludes = excludes;
        if (propertyFile != null && !propertyPrefix.isEmpty() && !propertyPrefix.endsWith(DOT_STRING)) {
            this.propertyPrefix = propertyPrefix + DOT_STRING;
        }
        else {
            this.propertyFile = EMPTY_STRING;
        }
    }

    private static final String EXCLUDES_OPT = "-excludes";
    private static final String INCLUDES_OPT = "-includes";
    private static final String SOURCE_OPT = "-sourcePath";
    private static final String PROP_FILE_OPT = "-propFile";
    private static final String PROP_FILE_PREFIX_OPT = "-propFilePrefix";

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("No command line options passed.");
            System.exit(-1);
        }
        String sourcePath = parseOption(args, SOURCE_OPT, true);
        String propFile = parseOption(args, PROP_FILE_OPT, true);
        String propFilePrefix = parseOption(args, PROP_FILE_PREFIX_OPT, false);
        String includes = parseOption(args, INCLUDES_OPT, false);
        String excludes = parseOption(args, EXCLUDES_OPT, false);
        Enricher enricher = new Enricher(propFile, propFilePrefix, sourcePath,
                );


    }

    private static String parseOption(String[] args, String option, boolean required) {
        Optional<String> optionArg = Arrays.stream(args).filter(s -> s.equals(option)).findFirst();
        if (!optionArg.isPresent() && required) {
            System.err.println(String.format("Required option '%s' is missing.", option));
            System.exit(-2);
        }
        // get next element after option
        for (int i=0; i<args.length; i++) {
            if (args[i].equals(option)) {
                if (args.length > i+1) {
                    return args[i+1];
                }
                System.err.println(String.format("Required option argument for '%s' is missing.", option));
                System.exit(-2);
            }
        }
        if (required) {
            System.err.println(String.format("Required option '%s' is missing.", option));
            System.exit(-2);
        }
        return null;
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
    }

    private void handleSchema(Properties properties, Path path) {
        CompilationUnit compilationUnit;
        try {
            compilationUnit = JavaParser.parse(path.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find file.", e);
        }
        List<ClassOrInterfaceDeclaration> classOrInterfaceDeclarations = new ArrayList<>(compilationUnit.
                findAll(ClassOrInterfaceDeclaration.class));

        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : classOrInterfaceDeclarations) {
            if (!classOrInterfaceDeclaration.getAnnotationByName(SCHEMA_ANNOTATION_SIMPLE_NAME).isPresent()) {
                String description = findClassDescription(properties, classOrInterfaceDeclaration);
                if (description != null && !description.isEmpty()) {
                    addSchemaAnnotation(classOrInterfaceDeclaration, description);
                }
            }
            classOrInterfaceDeclaration.getFields().forEach(
                    f -> {
                        if (!f.isAnnotationPresent(SCHEMA_ANNOTATION_SIMPLE_NAME)) {
                            String description = findFieldDescription(properties, classOrInterfaceDeclaration, f);
                            if (description != null && !description.isEmpty()) {
                                addSchemaAnnotation(f, description);
                            }
                        }
                    }
            );
        }
    }

    private void addSchemaAnnotation(BodyDeclaration bodyDeclaration, String description) {
        bodyDeclaration.addAndGetAnnotation(SCHEMA_ANNOTATION_CLASS).addPair(SCHEMA_DESCRIPTION,
                escapeString(description));
    }

    private String escapeString(String string) {
        return QUOTATION_MARK_STRING + string + QUOTATION_MARK_STRING;
    }

    private String getFilenameFromPath(String path) {
        if (path == null) {
            return null;
        }
        final int index = path.lastIndexOf(File.pathSeparatorChar);
        return path.substring(index + 1);
    }

    private String getLowercaseClassName(ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
        String className = classOrInterfaceDeclaration.getNameAsString();
       return className.substring(0, 1).toLowerCase() + className.substring(1);
    }

    private String findFieldDescription(Properties properties, ClassOrInterfaceDeclaration classOrInterfaceDeclaration,
    FieldDeclaration fieldDeclaration) {
        String className = getLowercaseClassName(classOrInterfaceDeclaration);
        Optional<String> descriptionKey = properties.keySet().stream().map(Object::toString).filter(
                k -> k.substring(propertyPrefix.length()).equals(className + DOT_STRING + fieldDeclaration.getVariables().get(0).getNameAsString())
        ).findFirst();
        return descriptionKey.map(properties::getProperty).orElse(null);
    }

    private String findClassDescription(Properties properties, ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
        String className = getLowercaseClassName(classOrInterfaceDeclaration);
        Optional<String> descriptionKey = properties.keySet().stream().map(Object::toString).filter(
                k -> k.substring(propertyPrefix.length()).equals(className)
        ).findFirst();
        return descriptionKey.map(properties::getProperty).orElse(null);
    }


}
