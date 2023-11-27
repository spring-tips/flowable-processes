package com.example.flowable.aot;

import org.apache.ibatis.javassist.util.proxy.ProxyFactory;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.type.TypeHandler;
import org.flowable.common.engine.api.query.Query;
import org.flowable.common.engine.impl.db.ListQueryParameterObject;
import org.flowable.common.engine.impl.de.odysseus.el.ExpressionFactoryImpl;
import org.flowable.common.engine.impl.persistence.cache.EntityCacheImpl;
import org.flowable.common.engine.impl.persistence.entity.ByteArrayRef;
import org.flowable.common.engine.impl.persistence.entity.Entity;
import org.flowable.common.engine.impl.persistence.entity.EntityManager;
import org.flowable.common.engine.impl.persistence.entity.TablePageQueryImpl;
import org.flowable.eventregistry.impl.db.SetChannelDefinitionTypeAndImplementationCustomChange;
import org.flowable.variable.api.types.VariableType;
import org.flowable.variable.service.impl.InternalVariableInstanceQueryImpl;
import org.flowable.variable.service.impl.QueryVariableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Josh Long
 * @author Joram Barrez
 */
class FlowableBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    private final PathMatchingResourcePatternResolver resolver;

    private final Logger log = LoggerFactory.getLogger(getClass());

    FlowableBeanFactoryInitializationAotProcessor(PathMatchingResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    private static <T> Set<T> from(T[] t) {
        return new HashSet<>(Arrays.asList(t));
    }

    private static Resource newResourceFor(Resource in) {
        try {
            var marker = "jar!";
            var p = in.getURL().toExternalForm();
            var rest = p.substring(p.lastIndexOf(marker) + marker.length());
            return new ClassPathResource(rest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<Resource> persistenceResources() throws Exception {
        var patterns = Stream
                .of(
                        "processes/**/*",
                        "org/flowable/**/*.sql",
                        "org/flowable/**/*.xml",
                        "org/flowable/**/*.txt",
                        "org/flowable/**/*.xsd",
                        "org/flowable/**/*.properties"
                )
                .map(path -> ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + path)
                .flatMap(p -> {
                    try {
                        return Stream.of(this.resolver.getResources(p));
                    }//
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(FlowableBeanFactoryInitializationAotProcessor::newResourceFor)
                .toList();

        var resources = new HashSet<Resource>();
        resources.addAll(patterns);

        for (var e : "xml,yaml,yml".split(","))
            resources.add(new ClassPathResource("flowable-default." + e));

        resources.addAll(from(this.resolver.getResources("META-INF/services/org.flowable.common.engine.impl.EngineConfigurator")));
        resources.addAll(from(this.resolver.getResources("org/flowable/common/engine/impl/de/odysseus/el/misc/LocalStrings")));
        return resources.stream()
                .filter(Resource::exists)
                .collect(Collectors.toSet());
    }

    private static Set<String> getSubTypesOf(Class<?> clzzName, String... packages) {
        var set = new HashSet<String>();

        for (var p : packages) {
            var classPathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);

            classPathScanningCandidateComponentProvider.addIncludeFilter(new AssignableTypeFilter(clzzName));

            var results = classPathScanningCandidateComponentProvider.findCandidateComponents(p);
            for (var r : results)
                set.add(r.getBeanClassName());
        }

        return set;

    }

    private void doRegisterHints(ConfigurableListableBeanFactory beanFactory, RuntimeHints hints, ClassLoader classLoader) {
        try {

            var memberCategories = MemberCategory.values();

            var clazzes = Set.of(ProxyFactory.class, XMLLanguageDriver.class,
                    org.apache.ibatis.logging.slf4j.Slf4jImpl.class, EntityCacheImpl.class,
                    RawLanguageDriver.class, org.apache.ibatis.session.Configuration.class, HashSet.class);

            for (var c : clazzes)
                hints.reflection().registerType(c, memberCategories);


            var types = new Class[]{
                    TypeHandler.class,
                    EntityManager.class,
                    Entity.class,
                    Query.class,
                    VariableType.class,
                    ListQueryParameterObject.class,
                    TablePageQueryImpl.class,
                    SetChannelDefinitionTypeAndImplementationCustomChange.class,
                    ByteArrayRef.class,
                    InternalVariableInstanceQueryImpl.class,
                    QueryVariableValue.class,
                    ExpressionFactoryImpl.class
            };

            var packagesSet = new HashSet<String>();
            packagesSet.add("org.apache.ibatis");
            packagesSet.add("org.flowable");
            packagesSet.addAll(AutoConfigurationPackages.get(beanFactory));
            var packages = packagesSet.toArray(new String[0]);

            for (var t : types) {
                hints.reflection().registerType(t, memberCategories);
                var subTypes = getSubTypesOf(t, packages);
                for (var s : subTypes) {
                    if (StringUtils.hasText(s)) {
                        hints.reflection().registerType(TypeReference.of(s), memberCategories);
                        log.info("registering hint for [" + s + "]");
                    }
                }
            }

            var resources = new HashSet<Resource>();
            resources.addAll(persistenceResources());
            resources.addAll("""
                    flowable-default.properties
                    flowable-default.xml
                    flowable-default.yaml
                    flowable-default.yml
                       """
                    .stripIndent()
                    .stripLeading()
                    .trim()
                    .lines()
                    .map(l -> l.strip().trim())
                    .filter(l -> !l.isEmpty())
                    .map(ClassPathResource::new)
                    .toList());


            for (var resource : resources) {
                if (resource.exists()) {
                    hints.resources().registerResource(resource);
                    System.out.println("registering hint for " + resource);
                }
            }

        }//
        catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        return (generationContext, beanFactoryInitializationCode) -> doRegisterHints(beanFactory, generationContext.getRuntimeHints(), beanFactory.getBeanClassLoader());
    }

}
