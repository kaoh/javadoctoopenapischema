package de.ohmesoftware.javadoctoopenapischema;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.javadoc.Javadoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enriches the passed source path and sub directories and adds or sets the "description" property of the @Schema annotation
 * from the Javadoc.
 *
 * @author <a href="mailto:k_o_@users.sourceforge.net">Karsten Ohme
 * (k_o_@users.sourceforge.net)</a>
 */
public class Enricher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Enricher.class);

    private static final String SCHEMA_ANNOTATION_SIMPLE_NAME = "Schema";
    private static final String SCHEMA_ANNOTATION_CLASS = "io.swagger.v3.oas.annotations.media.Schema";
    private static final String EMPTY_STRING = "";
    private static final String SPACE_STRING = " ";
    private static final String INCLUDE_EXCLUDE_SEPARATOR = ",";
    private static final String QUOTATION_MARK_STRING = "\"";
    private static final String SCHEMA_DESCRIPTION = "description";

    private static final String GLOB = "glob:";

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
     * @param sourcePath The source path to enrich.
     * @param includes   The includes.
     * @param excludes   The excludes.
     */
    public Enricher(String sourcePath, Set<String> includes, Set<String> excludes) {
        this.sourcePath = sourcePath;
        this.includes = includes;
        this.excludes = excludes;
    }

    private static final String EXCLUDES_OPT = "-excludes";
    private static final String INCLUDES_OPT = "-includes";
    private static final String SOURCE_OPT = "-sourcePath";

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("No command line options passed.");
            System.exit(-1);
        }
        String sourcePath = parseOption(args, SOURCE_OPT, true, null);
        String includes = parseOption(args, INCLUDES_OPT, false, null);
        String excludes = parseOption(args, EXCLUDES_OPT, false, null);
        Enricher enricher = new Enricher(sourcePath,
                includes == null ? null : Arrays.stream(includes.split(INCLUDE_EXCLUDE_SEPARATOR)).map(String::trim).collect(Collectors.toSet()),
                excludes == null ? null : Arrays.stream(excludes.split(INCLUDE_EXCLUDE_SEPARATOR)).map(String::trim).collect(Collectors.toSet())
        );
        enricher.enrich();
    }

    private static String parseOption(String[] args, String option, boolean required,
                                      String _default) {
        Optional<String> optionArg = Arrays.stream(args).filter(s -> s.equals(option)).findFirst();
        if (!optionArg.isPresent() && required) {
            System.err.println(String.format("Required option '%s' is missing.", option));
            System.exit(-2);
        }
        // get next element after option
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(option)) {
                if (args.length > i + 1) {
                    return args[i + 1];
                }
                System.err.println(String.format("Required option argument for '%s' is missing.", option));
                System.exit(-2);
            }
        }
        if (required) {
            System.err.println(String.format("Required option '%s' is missing.", option));
            System.exit(-2);
        }
        return _default;
    }


    public void enrich() {
        LOGGER.info(String.format("Enriching source path '%s'", sourcePath));
        try {
            Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    LOGGER.debug(String.format("Checking file '%s' for inclusion / exclusion", path.getFileName().toString()));
                    if (includes != null && !includes.isEmpty()) {
                        boolean handle = false;
                        for (String include : includes) {
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(GLOB+include);
                            if (pathMatcher.matches(path) && path.toFile().isFile()) {
                                LOGGER.debug(String.format("Included file: '%s'", path.getFileName().toString()));
                                // handle
                                handle = true;
                                break;
                            }
                        }
                        if (!handle) {
                            LOGGER.debug(String.format("Not included file: '%s'", path.getFileName().toString()));
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    if (excludes != null && !excludes.isEmpty()) {
                        for (String exclude : excludes) {
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(GLOB+exclude);
                            if (pathMatcher.matches(path)) {
                                // skip sub dirs if a directory
                                if (path.toFile().isDirectory()) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                LOGGER.debug(String.format("Excluded file: '%s'", path.getFileName().toString()));
                                // ignore if excludes
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    }
                    // handle
                    handleSchema(path);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    LOGGER.warn(String.format("Could not check file '%s'", file.getFileName().toString()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.error("Could not walk through source files.", e);
            throw new RuntimeException("Could not walk through source files.", e);
        }
    }

    private String getJavadoc(BodyDeclaration bodyDeclaration) {
        Javadoc javadoc = bodyDeclaration.getComment().filter(Comment::isJavadocComment).map(c -> c.asJavadocComment().parse()).orElse(null);
        if (javadoc != null) {
            return javadoc.getDescription().getElements().stream().map(d -> d.toText().trim()).collect(Collectors.joining(SPACE_STRING));
        }
        return null;
    }

    private void handleSchema(Path path) throws IOException {
        LOGGER.info(String.format("Handling file: '%s'", path.getFileName().toString()));
        CompilationUnit compilationUnit;
        try {
            compilationUnit = JavaParser.parse(path.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find file.", e);
        }
        List<ClassOrInterfaceDeclaration> classOrInterfaceDeclarations = new ArrayList<>(compilationUnit.
                findAll(ClassOrInterfaceDeclaration.class));

        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : classOrInterfaceDeclarations) {
            addSchemaAnnotation(classOrInterfaceDeclaration);
            classOrInterfaceDeclaration.getFields().forEach(
                    this::addSchemaAnnotation
            );
            try (FileWriter fileWriter = new FileWriter(path.toFile())) {
                fileWriter.write(compilationUnit.toString());
            }
        }
    }

    private void addSchemaAnnotation(BodyDeclaration bodyDeclaration) {
        String description = getJavadoc(bodyDeclaration);
        if (description != null) {
            AnnotationExpr annotationExpr = (AnnotationExpr) bodyDeclaration.getAnnotationByName(SCHEMA_ANNOTATION_SIMPLE_NAME).orElse(
                    bodyDeclaration.addAndGetAnnotation(SCHEMA_ANNOTATION_CLASS));
            ((NormalAnnotationExpr) annotationExpr).addPair(SCHEMA_DESCRIPTION,
                    escapeString(description));
        }
    }

    private String escapeString(String string) {
        return QUOTATION_MARK_STRING + string + QUOTATION_MARK_STRING;
    }

}
