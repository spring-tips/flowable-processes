package com.example.flowable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

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
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.eventregistry.impl.db.SetChannelDefinitionTypeAndImplementationCustomChange;
import org.flowable.variable.api.types.VariableType;
import org.flowable.variable.service.impl.InternalVariableInstanceQueryImpl;
import org.flowable.variable.service.impl.QueryVariableValue;
import org.reflections.Reflections;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@ImportRuntimeHints({FlowableHints.class, FlowableApplication.AppSpecificRuntimeHints.class})
@SpringBootApplication
public class FlowableApplication {

    public static void main(String[] args) {
        System.setProperty("javax.xml.accessExternalDTD", "all");
        SpringApplication.run(FlowableApplication.class, args);
    }



    static class AppSpecificRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerResource(new ClassPathResource("my-processes/single-task-process.bpmn20.xml"));
            hints.reflection().registerType(EmailService.class,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);
        }
    }

    @Bean
    @Order(1)
    ApplicationRunner deployProcesses(ProcessEngine processEngine) {
        return args -> {
            System.out.println("Deploying process definition");
            processEngine.getRepositoryService().createDeployment()
                    .addClasspathResource("my-processes/single-task-process.bpmn20.xml").deploy();
        };
    }


    @Bean
    @Order(2)
    ApplicationRunner demo(RuntimeService runtimeService, TaskService taskService, EmailService emailService) {
        return args -> {
            var customerId = "1";
            var email = "email@email.com";
            var processInstanceId = this.beginCustomerEnrollmentProcess(runtimeService, customerId, email);
            System.out.println("process instance ID: " + processInstanceId);
            Assert.notNull(processInstanceId, "the process instance ID should not be null");
            var tasks = taskService
                    .createTaskQuery()
                    .taskName("confirm-email-task")
                    .includeProcessVariables()
                    .processVariableValueEquals("customerId", customerId)
                    .list();
            Assert.state(!tasks.isEmpty(), "there should be one outstanding");
            tasks.forEach(task -> {
                taskService.claim(task.getId(), "jlong");
                taskService.complete(task.getId());
            });
            Assert.isTrue(emailService.getSendCount(email).get() == 1, "there should be 1 email sent");
        };
    }

    private String beginCustomerEnrollmentProcess(RuntimeService runtimeService, String customerId, String email) {
        var vars = Map.of("customerId", (Object) customerId, "email", (Object) email);
        var processInstance = runtimeService
                .startProcessInstanceByKey("signup-process", vars);
        return processInstance.getId();
    }
}

class FlowableHints implements RuntimeHintsRegistrar {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

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
//                        "org/flowable/**/mappings.xml",
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
                .map(FlowableHints::newResourceFor)
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


    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        try {

            var memberCategories = new MemberCategory[]{
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS
            };

            var clazzes = Set.of(ProxyFactory.class, XMLLanguageDriver.class,
                    org.apache.ibatis.logging.slf4j.Slf4jImpl.class, EntityCacheImpl.class,
                    RawLanguageDriver.class, org.apache.ibatis.session.Configuration.class, HashSet.class);

            for (var c : clazzes)
                hints.reflection().registerType(c, memberCategories);

            var reflections = new Reflections("org.flowable");
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
            for (var t : types) {
                hints.reflection().registerType(t, memberCategories);
                var subTypes = (Set<Class<?>>) reflections.getSubTypesOf(t);
                for (var s : subTypes) {
                    hints.reflection().registerType(s, memberCategories);
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

//            var mappings = Set.of(
//                    new ClassPathResource("/org/flowable/idm/db/mapping/mappings.xml"),
//                    new ClassPathResource("/org/flowable/db/mapping/mappings.xml"),
//                    new ClassPathResource("/org/flowable/eventregistry/db/mapping/mappings.xml")
//            );
//            for (var r : mappings) {
//                if (r.exists()) {
//                    resources.addAll(mappers(r));
//                }
//            }

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

//    private Set<Resource> mappers(Resource mapping) throws Exception {
//        var resources = new HashSet<Resource>();
//        try (var in = new InputStreamReader(mapping.getInputStream())) {
//            var xml = FileCopyUtils.copyToString(in);
//            resources.addAll(mapperResources(xml));
//        }
//        resources.add(mapping);
//        return resources;
//
//    }

//    private Set<Resource> mapperResources(String xml) {
//        try {
//            var set = new HashSet<Resource>();
//            var dbf = DocumentBuilderFactory.newInstance();
//            var db = dbf.newDocumentBuilder();
//            var is = new InputSource(new StringReader(xml));
//            var doc = db.parse(is);
//            var mappersElement = (Element) doc.getElementsByTagName("mappers").item(0);
//            var mapperList = mappersElement.getElementsByTagName("mapper");
//            for (var i = 0; i < mapperList.getLength(); i++) {
//                var mapperElement = (Element) mapperList.item(i);
//                var resourceValue = mapperElement.getAttribute("resource");
//                set.add(new ClassPathResource(resourceValue));
//            }
//            return set;
//        }//
//        catch (Throwable throwable) {
//            throw new RuntimeException(throwable);
//        }
//
//    }
}

@Service
class EmailService {

    private final ConcurrentHashMap<String, AtomicInteger> sends = new ConcurrentHashMap<>();

    AtomicInteger getSendCount(String key) {
        return this.sends.get(key);
    }

    public void sendWelcomeEmail(String customerId, String email) {
        System.out.println("sending welcome email for " + customerId + " to " + email);
        sends.computeIfAbsent(email, e -> new AtomicInteger());
        sends.get(email).incrementAndGet();
    }
}