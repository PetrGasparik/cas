package org.apereo.cas.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.common.base.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.Unchecked;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeElementsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.boot.bind.RelaxedNames;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * This is {@link ConfigurationMetadataGenerator}.
 * This class is invoked by the build during the finalization of the compile phase.
 * Its job is to scan the generated configuration metadata and produce metadata
 * for settings that the build process is unable to parse. Specifically,
 * this includes fields that are of collection type (indexed) where the inner type is an
 * externalized class.
 * <p>
 * Example:
 * <code>
 * private List&lt;SomeClassProperties&gt; list = new ArrayList&lt;&gt;()
 * </code>
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
public class ConfigurationMetadataGenerator {
    private static final Pattern NESTED_TYPE_PATTERN = Pattern.compile("java\\.util\\.\\w+<(org\\.apereo\\.cas\\..+)>");

    private final String buildDir;
    private final String sourcePath;
    private final Map<String, Class> cachedPropertiesClasses = new HashMap<>();
    
    public ConfigurationMetadataGenerator(final String buildDir, final String sourcePath) {
        this.buildDir = buildDir;
        this.sourcePath = sourcePath;
    }

    /**
     * Main.
     *
     * @param args the args
     * @throws Exception the exception
     */
    public static void main(final String[] args) throws Exception {
        new ConfigurationMetadataGenerator(args[0], args[1]).execute();
    }

    /**
     * Execute.
     *
     * @throws Exception the exception
     */
    public void execute() throws Exception {
        final File jsonFile = new File(buildDir, "classes/java/main/META-INF/spring-configuration-metadata.json");
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final TypeReference<Map<String, Set<ConfigurationMetadataProperty>>> values = new TypeReference<Map<String, Set<ConfigurationMetadataProperty>>>() {
        };
        final Map<String, Set<ConfigurationMetadataProperty>> jsonMap = mapper.readValue(jsonFile, values);
        final Set<ConfigurationMetadataProperty> properties = jsonMap.get("properties");

        final Set<ConfigurationMetadataProperty> collectedProps = new HashSet<>();
        properties.stream()
                .filter(p -> NESTED_TYPE_PATTERN.matcher(p.getType()).matches())
                .forEach(Unchecked.consumer(p -> {
                    final Matcher matcher = NESTED_TYPE_PATTERN.matcher(p.getType());
                    // matcher always creates a new matcher, so you'd need to call matches() again.
                    final boolean indexBrackets = matcher.matches();
                    final String typePath = buildTypeSourcePath(matcher.group(1));
                    parseCompilationUnit(collectedProps, p, typePath, indexBrackets);
                }));
        properties.addAll(collectedProps);
        jsonMap.put("properties", properties);
        //mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, jsonMap);
    }

    private String buildTypeSourcePath(final String type) {
        final String newName = type.replace(".", File.separator);
        return sourcePath + "/src/main/java/" + newName + ".java";
    }

    private void parseCompilationUnit(final Set<ConfigurationMetadataProperty> collectedProps,
                                      final ConfigurationMetadataProperty p, 
                                      final String typePath,
                                      final boolean indexNameWithBrackets) {

        try (InputStream is = new FileInputStream(typePath)) {
            final CompilationUnit cu = JavaParser.parse(is);
            new FieldVisitor(collectedProps, indexNameWithBrackets).visit(cu, p);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class FieldVisitor extends VoidVisitorAdapter<ConfigurationMetadataProperty> {
        private final Set<ConfigurationMetadataProperty> properties;
        private final boolean indexNameWithBrackets;
        
        FieldVisitor(final Set<ConfigurationMetadataProperty> properties, final boolean indexNameWithBrackets) {
            this.properties = properties;
            this.indexNameWithBrackets = indexNameWithBrackets;
        }

        @Override
        public void visit(final FieldDeclaration n, final ConfigurationMetadataProperty arg) {
            if (n.getJavadoc().isPresent() && !n.getVariables().isEmpty()) {
                final ConfigurationMetadataProperty prop = createConfigurationProperty(n, arg);
                processNestedClassOrInterfaceTypeIfNeeded(n, prop);
            }

        }

        private ConfigurationMetadataProperty createConfigurationProperty(final FieldDeclaration n, 
                                                                          final ConfigurationMetadataProperty arg) {
            final VariableDeclarator variable = n.getVariables().get(0);
            final String name = StreamSupport.stream(RelaxedNames.forCamelCase(variable.getNameAsString()).spliterator(), false)
                    .map(Object::toString)
                    .findFirst()
                    .orElse(variable.getNameAsString());

            final String indexedName = arg.getName()
                    .concat(indexNameWithBrackets ? "[]" : StringUtils.EMPTY)
                    .concat(".")
                    .concat(name);
            final ConfigurationMetadataProperty prop = new ConfigurationMetadataProperty();
            final String description = n.getJavadoc().get().getDescription().toText();
            prop.setDescription(description);
            prop.setShortDescription(StringUtils.substringBefore(description, "."));
            prop.setName(indexedName);
            prop.setId(indexedName);
            prop.setType(n.getElementType().asString());

            if (variable.getInitializer().isPresent()) {
                final Expression exp = variable.getInitializer().get();
                if (exp instanceof LiteralStringValueExpr) {
                    prop.setDefaultValue(((LiteralStringValueExpr) exp).getValue());
                } else if (exp instanceof BooleanLiteralExpr) {
                    prop.setDefaultValue(((BooleanLiteralExpr) exp).getValue());
                }
            }
            properties.add(prop);
            return prop;
        }

        private void processNestedClassOrInterfaceTypeIfNeeded(final FieldDeclaration n, final ConfigurationMetadataProperty prop) {
            if (n.getElementType() instanceof ClassOrInterfaceType) {
                final ClassOrInterfaceType type = (ClassOrInterfaceType) n.getElementType();
                if (type.getNameAsString().endsWith("Properties")) {
                    final Class clz = locatePropertiesClassForType(type);
                    final String typePath = buildTypeSourcePath(clz.getName());
                    parseCompilationUnit(properties, prop, typePath, false);
                }
            }
        }
    }

    private Class locatePropertiesClassForType(final ClassOrInterfaceType type) {
        if (cachedPropertiesClasses.containsKey(type.getNameAsString())) {
            return cachedPropertiesClasses.get(type.getNameAsString());
            
        }
        final Predicate<String> filterInputs = s -> s.contains(type.getNameAsString());
        final Predicate<String> filterResults = s -> s.endsWith(type.getNameAsString());
        final String packageName = ConfigurationMetadataGenerator.class.getPackage().getName();
        final Reflections reflections =
                new Reflections(new ConfigurationBuilder()
                        .filterInputsBy(filterInputs)
                        .setUrls(ClasspathHelper.forPackage(packageName))
                        .setScanners(new TypeElementsScanner()
                                        .includeFields(false)
                                        .includeMethods(false)
                                        .includeAnnotations(false)
                                        .filterResultsBy(filterResults),
                                new SubTypesScanner(false)));
        final Class clz = reflections.getSubTypesOf(Serializable.class).stream()
                .filter(c -> c.getSimpleName().equalsIgnoreCase(type.getNameAsString()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cant locate class for " + type.getNameAsString()));
        cachedPropertiesClasses.put(type.getNameAsString(), clz);
        return clz;
    }
}