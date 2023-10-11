package com.example.flowable;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ImportRuntimeHints({FlowableHints.class, FlowableApplication.AppSpecificRuntimeHints.class})
@SpringBootApplication/*(exclude = {
        org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration.class,
        org.flowable.spring.boot.EndpointAutoConfiguration.class,
        org.flowable.spring.boot.RestApiAutoConfiguration.class,
        org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration.class,
        org.flowable.spring.boot.app.AppEngineAutoConfiguration.class,
        org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration.class,
        org.flowable.spring.boot.ProcessEngineAutoConfiguration.class,
        org.flowable.spring.boot.FlowableJpaAutoConfiguration.class,
        org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration.class,
        org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration.class,
        org.flowable.spring.boot.idm.IdmEngineAutoConfiguration.class,
        org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration.class,
        org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration.class,
        org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration.class,
        org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration.class,
        org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration.class,
        org.flowable.spring.boot.ldap.FlowableLdapAutoConfiguration.class,
        org.flowable.spring.boot.FlowableSecurityAutoConfiguration.class
})*/
public class FlowableApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowableApplication.class, args);
    }

    static class AppSpecificRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerResource(new ClassPathResource("processes/single-task-process.bpmn20.xml"));
        }
    }

    @Bean
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

    private final PathMatchingResourcePatternResolver resourcePatternResolver =
            new PathMatchingResourcePatternResolver();

    private Set<Resource> persistenceResources() throws Exception {
        var resources = new HashSet<Resource>();
        resources.addAll(from(this.resourcePatternResolver.getResources("/org/flowable/common/db/**/*.sql")));
        resources.addAll(from(this.resourcePatternResolver.getResources("/org/flowable/common/db/**/*.properties")));
        resources.addAll(from(this.resourcePatternResolver.getResources("/org/flowable/common/db/**/*.xml")));
        return resources;
    }

    private <T> Set<T> from(T[] t) {
        return new HashSet<>(Arrays.asList(t));
    }


    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        try {
            var resources = new HashSet<Resource>();
            resources.addAll(persistenceResources());
            resources.addAll("""
                    META-INF/services/org.flowable.common.engine.impl.EngineConfigurator
                    flowable-default.properties
                    flowable-default.xml
                    flowable-default.yaml
                    flowable-default.yml
                    org/flowable/eventregistry/db/mapping/mappings.xml
                    org/flowable/spring/boot/flowable-banner.txt
                    org/flowable/db/mapping/mappings.xml
                    org/flowable/idm/db/mapping/mappings.xml
                       """
                    .stripIndent()
                    .stripLeading()
                    .trim()
                    .lines()
                    .map(l -> l.strip().trim())
                    .filter(l -> !l.isEmpty())
                    .map(ClassPathResource::new)
                    .toList());

            var mappings = Set.of(
                    new ClassPathResource("/org/flowable/idm/db/mapping/mappings.xml"),
                    new ClassPathResource("/org/flowable/db/mapping/mappings.xml"),
                    new ClassPathResource("/org/flowable/eventregistry/db/mapping/mappings.xml")
            );
            for (var r : mappings) {
                if (r.exists()) {
                    resources.addAll(mappers(r));
                }
            }


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

    private Set<Resource> mappers(Resource mapping) throws Exception {
        var resources = new HashSet<Resource>();
        try (var in = new InputStreamReader(mapping.getInputStream())) {
            var xml = FileCopyUtils.copyToString(in);
            resources.addAll(mapperResources(xml));
        }
        resources.add(mapping);
        return resources;

    }


    private Set<Resource> mapperResources(String xml) {
        try {
            var set = new HashSet<Resource>();
            var dbf = DocumentBuilderFactory.newInstance();
            var db = dbf.newDocumentBuilder();
            var is = new InputSource(new StringReader(xml));
            var doc = db.parse(is);
            var mappersElement = (Element) doc.getElementsByTagName("mappers").item(0);
            var mapperList = mappersElement.getElementsByTagName("mapper");
            for (var i = 0; i < mapperList.getLength(); i++) {
                var mapperElement = (Element) mapperList.item(i);
                var resourceValue = mapperElement.getAttribute("resource");
                set.add(new ClassPathResource(resourceValue));
            }
            return set;
        }//
        catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }
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