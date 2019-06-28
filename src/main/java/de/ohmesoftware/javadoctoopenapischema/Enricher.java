package de.ohmesoftware.javadoctoopenapischema;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.javadoc.Javadoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Blob;
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
    private static final String SCHEMA_TITLE = "title";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_MAX_LENGTH = "maxLength";
    private static final String SCHEMA_MIN_LENGTH = "minLength";
    private static final String SCHEMA_MAX = "maximum";
    private static final String SCHEMA_MIN = "minimum";

    private static final String GLOB = "glob:";

    private static final String PARAGRAPH_START = "<p>";
    private static final String PARAGRAPH_END = "</p>";

    private static final String NOT_EMPTY_ANNOTATION = "javax.validation.constraints.NotEmpty";
    private static final String COLUMN_ANNOTATION = "javax.persistence.Column";
    private static final String COLUMN_LENGTH_PROP = "length";
    private static final String COLUMN_NULLABLE = "nullable";
    private static final String SIZE_ANNOTATION = "javax.validation.constraints.Size";
    private static final String NOT_NULL_ANNOTATION = "javax.validation.constraints.NotNull";
    private static final String SIZE_MIN_PROP = "min";
    private static final String SIZE_MAX_PROP = "max";


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
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(GLOB + include);
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
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(GLOB + exclude);
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

    private String getJavadocSummary(String javadoc) {
        String[] commentParts = javadoc.split(PARAGRAPH_START);
        return commentParts[0].trim();
    }

    private String getJavadocDescription(String javadoc) {
        String[] commentParts = javadoc.split(PARAGRAPH_START);
        if (commentParts.length > 1) {
            String description = commentParts[1].trim();
            if (description.endsWith(PARAGRAPH_START)) {
                description = description.substring(0, description.length() - PARAGRAPH_START.length());
            }
            if (description.endsWith(PARAGRAPH_END)) {
                description = description.substring(0, description.length() - PARAGRAPH_END.length());
            }
            return description;
        }
        return null;
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

    private void setSchemaMemberValue(NormalAnnotationExpr annotationExpr, String schemaProperty, boolean value) {
        Optional<MemberValuePair> memberValuePairOptional = (annotationExpr.getPairs().stream().filter(
                a -> a.getName().getIdentifier().equals(schemaProperty)
        ).findFirst());
        if (!memberValuePairOptional.isPresent()) {
            annotationExpr.addPair(schemaProperty,
                    new BooleanLiteralExpr(value));
        } else {
            memberValuePairOptional.get().setValue(new BooleanLiteralExpr(value));
        }
    }

    private void setSchemaMemberValue(NormalAnnotationExpr annotationExpr, String schemaProperty, int value) {
        Optional<MemberValuePair> memberValuePairOptional = (annotationExpr.getPairs().stream().filter(
                a -> a.getName().getIdentifier().equals(schemaProperty)
        ).findFirst());
        if (!memberValuePairOptional.isPresent()) {
            annotationExpr.addPair(schemaProperty,
                    new IntegerLiteralExpr(value));
        } else {
            memberValuePairOptional.get().setValue(new IntegerLiteralExpr(value));
        }
    }

    private void setSchemaMemberValue(NormalAnnotationExpr annotationExpr, String schemaProperty, String value) {
        Optional<MemberValuePair> memberValuePairOptional = (annotationExpr.getPairs().stream().filter(
                a -> a.getName().getIdentifier().equals(schemaProperty)
        ).findFirst());
        if (!memberValuePairOptional.isPresent()) {
            if (value != null) {
                annotationExpr.addPair(schemaProperty,
                        new StringLiteralExpr(escapeString(value)));
            }
        } else {
            if (value != null) {
                memberValuePairOptional.get().setValue(new StringLiteralExpr(escapeString(value)));
            }
        }
    }

    private void addSchemaAnnotation(BodyDeclaration<?> bodyDeclaration) {
        String javadoc = getJavadoc(bodyDeclaration);
        if (javadoc == null) {
            return;
        }
        String summary = getJavadocSummary(javadoc);
        String description = getJavadocDescription(javadoc);

        NormalAnnotationExpr schemaAnnotationExpr = bodyDeclaration.getAnnotationByName(SCHEMA_ANNOTATION_SIMPLE_NAME).map(Expression::asNormalAnnotationExpr)
                .orElse(null);
        if (schemaAnnotationExpr == null) {
            schemaAnnotationExpr = bodyDeclaration.addAndGetAnnotation(SCHEMA_ANNOTATION_CLASS).asNormalAnnotationExpr();
        }
        setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_DESCRIPTION, description);
        setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_TITLE, summary);

        if (bodyDeclaration.isFieldDeclaration()) {
            Type elementType = bodyDeclaration.asFieldDeclaration().getElementType();
            boolean required = false;
            int maxSize = -1;
            int minSize = -1;
            AnnotationExpr annotationExpr = bodyDeclaration.getAnnotationByName(NOT_EMPTY_ANNOTATION).orElse(null);
            if (annotationExpr != null) {
                required = true;
                minSize = 1;
            }
            annotationExpr = bodyDeclaration.getAnnotationByName(NOT_NULL_ANNOTATION).orElse(null);
            if (annotationExpr != null) {
                required = true;
            }

            NormalAnnotationExpr normalAnnotationExpr = bodyDeclaration.getAnnotationByName(COLUMN_ANNOTATION).map(Expression::asNormalAnnotationExpr)
                    .orElse(null);
            if (normalAnnotationExpr != null) {
                for (MemberValuePair valuePair : normalAnnotationExpr.getPairs()) {
                    if (valuePair.getName().asString().equals(COLUMN_NULLABLE)) {
                        required = valuePair.getValue().asBooleanLiteralExpr().getValue();
                    }
                    if (valuePair.getName().asString().equals(COLUMN_LENGTH_PROP)) {
                        maxSize = valuePair.getValue().asIntegerLiteralExpr().asInt();
                    }
                }
            }
            normalAnnotationExpr = bodyDeclaration.getAnnotationByName(SIZE_ANNOTATION).map(Expression::asNormalAnnotationExpr)
                    .orElse(null);
            if (normalAnnotationExpr != null) {
                for (MemberValuePair valuePair : normalAnnotationExpr.getPairs()) {
                    if (valuePair.getName().asString().equals(SIZE_MIN_PROP)) {
                        minSize = valuePair.getValue().asIntegerLiteralExpr().asInt();
                    }
                    if (valuePair.getName().asString().equals(SIZE_MAX_PROP)) {
                        maxSize = valuePair.getValue().asIntegerLiteralExpr().asInt();
                    }
                }
            }

            if (required) {
                setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_REQUIRED, required);
            }
            // length for String
            if (elementType.asString().equals(String.class.getSimpleName())
                    || elementType.asString().equals(Blob.class.getSimpleName())) {
                if (minSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MIN_LENGTH, minSize);
                }
                if (maxSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MAX_LENGTH, maxSize);
                }
            } else {
                if (minSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MIN, minSize);
                }
                if (maxSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MAX, maxSize);
                }
            }
        }
    }

    protected String quoteString(String string) {
        return QUOTATION_MARK_STRING + string + QUOTATION_MARK_STRING;
    }

    protected String escapeString(String string) {
        return string.trim().replace("\n", SPACE_STRING).
                replace("\r", EMPTY_STRING).
                replace("<", "&lt;").
                replace(">", "&gt;").
                replace("\"", "\\\"").
                replaceAll("\\s+", SPACE_STRING);
    }

}
