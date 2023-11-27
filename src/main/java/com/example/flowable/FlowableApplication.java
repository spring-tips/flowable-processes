package com.example.flowable;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ImportRuntimeHints({ FlowableApplication.AppSpecificRuntimeHints.class})
@SpringBootApplication
public class FlowableApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowableApplication.class, args);
    }

    static class AppSpecificRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // if you're using a flowable:expression, you need to reference beans in a reflection-friendly way yourself.
            hints.reflection().registerType(EmailService.class, MemberCategory.values());
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

@Service
class EmailService implements JavaDelegate {

    private final ConcurrentHashMap<String, AtomicInteger> sends = new ConcurrentHashMap<>();

    AtomicInteger getSendCount(String key) {
        return this.sends.get(key);
    }

    @Override
    public void execute(DelegateExecution execution) {
        String customerId = (String) execution.getVariable("customerId");
        String email = (String) execution.getVariable("email");
        System.out.println("sending welcome email for " + customerId + " to " + email);
        sends.computeIfAbsent(email, e -> new AtomicInteger());
        sends.get(email).incrementAndGet();
    }

}