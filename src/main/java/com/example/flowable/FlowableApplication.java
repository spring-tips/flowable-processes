package com.example.flowable;

import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.delegate.DelegatePlanItemInstance;
import org.flowable.cmmn.api.delegate.PlanItemJavaDelegate;
import org.flowable.cmmn.engine.CmmnEngine;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.engine.ProcessEngine;
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
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class FlowableApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowableApplication.class, args);
    }

    @Bean
    ApplicationRunner demo(ProcessEngine processEngine, CmmnEngine cmmnEngine, DmnEngine dmnEngine, EmailService emailService) {
        return args -> {
            startProcessInstance(processEngine, emailService);
            startCaseInstance(cmmnEngine, emailService);
            executeRule(dmnEngine);
        };
    }

    private void startProcessInstance(ProcessEngine processEngine, EmailService emailService) {
        var customerId = "1";
        var email = "email@email.com";

        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();

        var vars = Map.of("customerId", (Object) customerId, "email", (Object) email);
        var processInstanceId = runtimeService.startProcessInstanceByKey("signup-process", vars).getId();

        System.out.println("process instance ID: " + processInstanceId);
        Assert.notNull(processInstanceId, "the process instance ID should not be null");
        var tasks = taskService
                .createTaskQuery()
                .taskName("confirm-email-task")
                .includeProcessVariables()
                .processVariableValueEquals("customerId", customerId)
                .list();
        Assert.state(!tasks.isEmpty(), "there should be one outstanding task");
        tasks.forEach(task -> {
            taskService.claim(task.getId(), "jlong");
            taskService.complete(task.getId());
        });
        Assert.isTrue(emailService.getSendCount(email).get() == 1, "there should be 1 email sent");
    }

    private void startCaseInstance(CmmnEngine cmmnEngine, EmailService emailService) {
        var customerId = "2";
        var email = "email@email.com";

        CmmnRuntimeService cmmnRuntimeService = cmmnEngine.getCmmnRuntimeService();
        CmmnTaskService cmmnTaskService = cmmnEngine.getCmmnTaskService();

        var vars = Map.of("customerId", (Object) customerId, "email", (Object) email);
        var caseInstanceId = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("signupCase")
                .variables(vars)
                .start()
                .getId();

        System.out.println("case instance ID: " + caseInstanceId);
        Assert.notNull(caseInstanceId, "the case instance ID should not be null");
        var tasks = cmmnTaskService
                .createTaskQuery()
                .taskName("Confirm email task")
                .includeProcessVariables()
                .caseVariableValueEquals("customerId", customerId)
                .list();
        Assert.state(!tasks.isEmpty(), "there should be one outstanding task");
        tasks.forEach(task -> {
            cmmnTaskService.claim(task.getId(), "jbarrez");
            cmmnTaskService.complete(task.getId());
        });
        Assert.isTrue(emailService.getSendCount(email).get() == 2, "there should be 2 emails sent");
    }

    private void executeRule(DmnEngine dmnEngine) {
        Map<String, Object> vars = Map.of("customerTotalOrderPrice", 99999);

        Map<String, Object> result = dmnEngine.getDmnDecisionService().createExecuteDecisionBuilder()
                .decisionKey("myDecisionTable")
                .variables(vars)
                .executeWithSingleResult();

        Assert.isTrue(result.size() == 1, "Expected one result");
        Object tier = result.get("tier");
        Assert.isTrue(tier.equals("SILVER"), "Expected SILVER as output, but was " + tier);
        System.out.println("Executed DMN rule correctly");
    }

}

@Service
class EmailService implements JavaDelegate, PlanItemJavaDelegate {

    private final ConcurrentHashMap<String, AtomicInteger> sends = new ConcurrentHashMap<>();

    AtomicInteger getSendCount(String key) {
        return this.sends.get(key);
    }

    @Override
    public void execute(DelegateExecution execution) {
        internalExecute(execution);
    }

    @Override
    public void execute(DelegatePlanItemInstance planItemInstance) {
        internalExecute(planItemInstance);
    }

    private void internalExecute(VariableContainer variableContainer) {
        var customerId = (String) variableContainer.getVariable("customerId");
        var email = (String) variableContainer.getVariable("email");
        System.out.println("sending welcome email for " + customerId + " to " + email);
        sends.computeIfAbsent(email, e -> new AtomicInteger());
        sends.get(email).incrementAndGet();
    }


}