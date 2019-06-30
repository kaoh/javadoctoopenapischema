package de.ohmesoftware.javadoctoopenapischema;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.javadoc.Javadoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

    private static final String SUMMARY = "No summary";
    private static final String DESCRIPTION = "No description";

    private static final Logger LOGGER = LoggerFactory.getLogger(Enricher.class);

    private static final String JAVA_EXT = ".java";
    private static final String DOT = ".";
    private static final String SLASH = "/";

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
    private static final String MIN_ANNOTATION = "javax.validation.constraints.Min";
    private static final String MAX_ANNOTATION = "javax.validation.constraints.Max";
    private static final String VALUE_PROP = "value";
    private static final String SIZE_MIN_PROP = "min";
    private static final String SIZE_MAX_PROP = "max";

    private static final String EXCLUDES_OPT = "-excludes";
    private static final String INCLUDES_OPT = "-includes";
    private static final String SOURCE_OPT = "-sourcePath";
    private static final String HATEAOS_OPT = "-hateaos";

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
     * <code>true</code> if HATEAOS is used.
     */
    private boolean hateaos;

    /**
     * Constructor.
     *
     * @param sourcePath The source path to enrich.
     * @param includes   The includes.
     * @param excludes   The excludes.
     * @param hateaos    <code>true</code> if HATEAOS is used. In this case associations are rendered as links.
     */
    public Enricher(String sourcePath, Set<String> includes, Set<String> excludes, boolean hateaos) {
        this.sourcePath = sourcePath;
        this.includes = includes;
        this.excludes = excludes;
        this.hateaos = hateaos;
    }


    private static CompilationUnit parseFile(File file) {
        try {
            return JavaParser.parse(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(String.format("Could not find file: %s", file), e);
        }
    }

    private static String getBaseSourcePath(CompilationUnit compilationUnit, String sourcePath) {
        String _package = compilationUnit.getPackageDeclaration().map(p -> p.getName().asString()).orElse(EMPTY_STRING);
        String packagePath = _package.replace(".", SLASH);
        int overlap = 0;
        for (int i = packagePath.length(); i >= 0; i--) {
            if (sourcePath.endsWith(packagePath.substring(0, i))) {
                overlap = i;
                break;
            }
        }
        return sourcePath.substring(0, sourcePath.length() - overlap);
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("No command line options passed.");
            System.exit(-1);
        }
        String sourcePath = parseOption(args, SOURCE_OPT, true, null);
        String includes = parseOption(args, INCLUDES_OPT, false, null);
        String excludes = parseOption(args, EXCLUDES_OPT, false, null);
        boolean hateaos = parseFlag(args, HATEAOS_OPT);
        Enricher enricher = new Enricher(sourcePath,
                includes == null ? null : Arrays.stream(includes.split(INCLUDE_EXCLUDE_SEPARATOR)).map(String::trim).collect(Collectors.toSet()),
                excludes == null ? null : Arrays.stream(excludes.split(INCLUDE_EXCLUDE_SEPARATOR)).map(String::trim).collect(Collectors.toSet()),
                hateaos
        );
        enricher.enrich();
    }

    private static boolean parseFlag(String[] args, String option) {
        Optional<String> optionArg = Arrays.stream(args).filter(s -> s.equals(option)).findFirst();
        if (!optionArg.isPresent()) {
            return false;
        }
        return true;
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
        String basePath = getBaseSourcePath(compilationUnit, path.toString());

        List<ClassOrInterfaceDeclaration> classOrInterfaceDeclarations = new ArrayList<>(compilationUnit.
                findAll(ClassOrInterfaceDeclaration.class));

        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : classOrInterfaceDeclarations) {
            addSchemaAnnotation(basePath, compilationUnit, classOrInterfaceDeclaration);
            classOrInterfaceDeclaration.getFields().forEach(
                    f -> addSchemaAnnotation(basePath, compilationUnit, f)
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

    private String getFullClassName(CompilationUnit compilationUnit,
                                    String className) {
        if (className.contains(DOT)) {
            return className;
        }
        switch (className) {
            case "String":
            case "Long":
            case "Integer":
            case "Double":
            case "Float":
            case "Date":
            case "Boolean":
                return String.class.getPackage().getName() + DOT + className;
            case "Optional":
                return Optional.class.getPackage().getName() + DOT + className;
        }
        return compilationUnit.getImports().stream().filter(i -> !i.isAsterisk() && i.getName().getIdentifier().equals(className)).
                map(i -> i.getName().asString()).findFirst().orElse(
                compilationUnit.getPackageDeclaration().map(p -> p.getName().asString() + DOT + className).
                        orElseThrow(
                                () -> new RuntimeException(
                                        String.format("Could not resolve import for type: %s", className))
                        ));
    }

    private String getFullClassName(CompilationUnit compilationUnit, ClassOrInterfaceType extent) {
        return getFullClassName(compilationUnit, extent.getNameAsString());
    }

    private File getSourceFile(String basePath, CompilationUnit compilationUnit, ClassOrInterfaceType extent) {
        String className = getFullClassName(compilationUnit, extent);
        // get File
        String sourcePath = basePath + className.replace('.', '/') + JAVA_EXT;
        return new File(sourcePath);
    }

    protected TypeDeclaration parseClassOrInterfaceType(String basePath, CompilationUnit compilationUnit, ClassOrInterfaceType classOrInterfaceType) {

        CompilationUnit newCompilationUnit = parseFile(getSourceFile(basePath, compilationUnit, classOrInterfaceType));
        TypeDeclaration newClassOrInterfaceDeclaration = newCompilationUnit.findFirst(TypeDeclaration.class).
                orElseThrow(() -> new RuntimeException(
                        String.format("Could not parse type: %s", classOrInterfaceType.asString())));
        return newClassOrInterfaceDeclaration;
    }

    private boolean isEnumProperty(String basePath, CompilationUnit compilationUnit, Type propertyClassOrInterfaceType) {
        if (!propertyClassOrInterfaceType.isClassOrInterfaceType()) {
            return false;
        }
        TypeDeclaration extendTypeDeclaration = parseClassOrInterfaceType(basePath, compilationUnit,
                propertyClassOrInterfaceType.asClassOrInterfaceType());
        return extendTypeDeclaration.isEnumDeclaration();
    }

    private String getSimpleNameFromClass(String fqClassName) {
        String[] packages = fqClassName.split("\\.");
        return packages[packages.length - 1];
    }

    private boolean isSimpleType(String basePath, CompilationUnit compilationUnit, Type type) {
        if (type.isPrimitiveType() || isEnumProperty(basePath, compilationUnit, type)) {
            return true;
        }
        if (type.isClassOrInterfaceType()) {
            switch (getSimpleNameFromClass(type.asClassOrInterfaceType().getName().asString())) {
                case "String":
                case "Double":
                case "Integer":
                case "Blob":
                case "Date":
                case "Float":
                case "Byte":
                case "Short":
                case "Calendar":
                    return true;
            }
        }
        return false;
    }

    private boolean isByteArray(FieldDeclaration fieldDeclaration) {
        return (fieldDeclaration.getCommonType().isArrayType()
                && fieldDeclaration.getElementType() != null
                && fieldDeclaration.getElementType().isPrimitiveType());
    }

    private void addSchemaAnnotation(String basePath, CompilationUnit compilationUnit,
                                     BodyDeclaration<?> bodyDeclaration) {
        String javadoc = getJavadoc(bodyDeclaration);
        String summary = SUMMARY;
        String description = DESCRIPTION;
        if (javadoc != null) {
            summary = getJavadocSummary(javadoc);
            description = getJavadocDescription(javadoc);
        }
        if (bodyDeclaration.isFieldDeclaration()) {
            FieldDeclaration fieldDeclaration = bodyDeclaration.asFieldDeclaration();
            Type commonType = fieldDeclaration.getCommonType();
            String fieldname = fieldDeclaration.toString();

            if (!isSimpleType(basePath, compilationUnit, commonType) && !isByteArray(fieldDeclaration)) {
                summary = String.format("URI for %s", SUMMARY);
                description = String.format("For the resource creation with POST this is an URI/are URIs to the associated resource(s). For GET operation on the item or collection resource " +
                                "this attribute is not included in the `\"_links\": { \"%s\": { \"href\": \"<Resource URI to %s>\"} } ` ",
                        fieldname, fieldname);
            }
        }

        NormalAnnotationExpr schemaAnnotationExpr = bodyDeclaration.getAnnotationByName(SCHEMA_ANNOTATION_SIMPLE_NAME).map(Expression::asNormalAnnotationExpr)
                .orElse(null);
        if (schemaAnnotationExpr == null) {
            schemaAnnotationExpr = bodyDeclaration.addAndGetAnnotation(SCHEMA_ANNOTATION_CLASS).asNormalAnnotationExpr();
        }
        setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_DESCRIPTION, description);
        setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_TITLE, summary);

        if (bodyDeclaration.isFieldDeclaration()) {
            Type commonType = bodyDeclaration.asFieldDeclaration().getCommonType();
            boolean required = false;
            int maxSize = -1;
            int minSize = -1;
            int max = -1;
            int min = -1;
            AnnotationExpr annotationExpr = getAnnotation(bodyDeclaration, NOT_EMPTY_ANNOTATION);
            if (annotationExpr != null) {
                required = true;
                minSize = 1;
            }
            annotationExpr = getAnnotation(bodyDeclaration, NOT_NULL_ANNOTATION);
            if (annotationExpr != null) {
                required = true;
            }

            annotationExpr = getAnnotation(bodyDeclaration, MIN_ANNOTATION);
            if (annotationExpr != null) {
                Expression value = getAnnotationValue(annotationExpr, VALUE_PROP);
                if (value != null) {
                    min = value.asIntegerLiteralExpr().asInt();
                }
            }
            annotationExpr = getAnnotation(bodyDeclaration, MAX_ANNOTATION);
            if (annotationExpr != null) {
                Expression value = getAnnotationValue(annotationExpr, VALUE_PROP);
                if (value != null) {
                    max = value.asIntegerLiteralExpr().asInt();
                }
            }

            annotationExpr = getAnnotation(bodyDeclaration, COLUMN_ANNOTATION);
            if (annotationExpr != null) {
                Expression nullable = getAnnotationValue(annotationExpr, COLUMN_NULLABLE);
                if (nullable != null) {
                    required = nullable.asBooleanLiteralExpr().getValue();
                }
                Expression length = getAnnotationValue(annotationExpr, COLUMN_LENGTH_PROP);
                if (length != null) {
                    maxSize = length.asIntegerLiteralExpr().asInt();
                }
            }
            annotationExpr = getAnnotation(bodyDeclaration, SIZE_ANNOTATION);
            if (annotationExpr != null) {
                Expression value = getAnnotationValue(annotationExpr, SIZE_MIN_PROP);
                if (value != null) {
                    minSize = value.asIntegerLiteralExpr().asInt();
                }

                value = getAnnotationValue(annotationExpr, SIZE_MAX_PROP);
                if (value != null) {
                    maxSize = value.asIntegerLiteralExpr().asInt();
                }
            }

            if (required) {
                setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_REQUIRED, required);
            }
            // length for String, arrays, blobs
            if (commonType.asString().endsWith(String.class.getSimpleName())
                    || commonType.asString().endsWith(Blob.class.getSimpleName())
                    || commonType.isArrayType()) {
                if (minSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MIN_LENGTH, minSize);
                }
                if (maxSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MAX_LENGTH, maxSize);
                }
            } else {
                if (minSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MIN, "" + minSize + "");
                }
                if (maxSize > -1) {
                    setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MAX, "" + maxSize + "");
                }
            }
            if (max > -1) {
                setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MAX, "" + max + "");
            }
            if (min > -1) {
                setSchemaMemberValue(schemaAnnotationExpr, SCHEMA_MIN, "" + min + "");
            }
        }
    }


    private Expression getAnnotationValue(AnnotationExpr annotationExpr, String property) {
        if (annotationExpr.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotationExpr = annotationExpr.asNormalAnnotationExpr();
            for (MemberValuePair valuePair : normalAnnotationExpr.getPairs()) {
                if (valuePair.getName().asString().equals(property)) {
                    return valuePair.getValue();
                }
            }
        } else if (annotationExpr.isSingleMemberAnnotationExpr()) {
            return annotationExpr.asSingleMemberAnnotationExpr().getMemberValue();
        }
        return null;
    }

    private AnnotationExpr getAnnotation(BodyDeclaration<?> bodyDeclaration, String annotationClass) {
        return bodyDeclaration.getAnnotationByName(annotationClass)
                .orElse(bodyDeclaration.getAnnotationByName(
                        annotationClass.substring(annotationClass.lastIndexOf('.') + 1)).orElse(null));
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
