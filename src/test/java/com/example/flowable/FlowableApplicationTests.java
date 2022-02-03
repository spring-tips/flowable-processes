package com.example.flowable;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;


@Slf4j
@SpringBootTest
public class FlowableApplicationTests {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TaskService taskService;

    @Test
    public void contextLoads() throws Exception {
        var customerId = "1";
        var email = "email@email.com";
        var processInstanceId = this.beginCustomerEnrollmentProcess(customerId, email);
        log.info("process instance ID: " + processInstanceId);
        Assertions.assertNotNull(processInstanceId, "the process instance ID should not be null");
        var tasks = this.taskService
                .createTaskQuery()
                .taskName("confirm-email-task")
                .includeProcessVariables()
                .processVariableValueEquals("customerId", customerId)
                .list();
        Assertions.assertTrue(tasks.size() >= 1, "there should be one outstanding");
        tasks.forEach(task -> {
            this.taskService.claim(task.getId(), "jlong");
            this.taskService.complete(task.getId());
        });
        Assertions.assertEquals(this.emailService.getSendCount(email).get(), 1);
    }

    private String beginCustomerEnrollmentProcess(String customerId, String email) {
        var vars = Map.of("customerId", (Object) customerId, "email", (Object) email);
        var processInstance = this.runtimeService.startProcessInstanceByKey("signup-process", vars);
        return processInstance.getId();
    }
}
