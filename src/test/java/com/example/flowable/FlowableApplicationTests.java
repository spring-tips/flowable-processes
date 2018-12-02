package com.example.flowable;

import lombok.extern.log4j.Log4j2;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
@Log4j2
public class FlowableApplicationTests {


	private static String CUSTOMER_ID_PV = "customerId";
	private static String EMAIL_PV = "email";

	@Autowired
	private HistoryService historyService;

	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private TaskService taskService;

	@Test
	public void contextLoads() throws Exception {

		String customerId = "1";
		String email = "email@email.com";
		String processInstanceId = this.beginCustomerEnrollmentProcess(customerId, email);
		log.info("process instance ID: " + processInstanceId);
		Assert.assertNotNull("the process instance ID should not be null", processInstanceId);

		// get outstanding tasks

		List<Task> tasks = this.taskService
			.createTaskQuery()
			.taskName("confirm-email-task")
			.includeProcessVariables()
			.processVariableValueEquals(CUSTOMER_ID_PV, customerId)
			.list();

		Assert.assertTrue("there should be one outstanding", tasks.size() >= 1);

		// complete outstanding tasks

		tasks.forEach(task -> {
				this.taskService.claim(task.getId(), "jlong");
				this.taskService.complete(task.getId());
			}
		);

		// confirm that the email has been sent
		Assert.assertEquals(this.emailService.sends.get(email).get(), 1);
	}


	String beginCustomerEnrollmentProcess(String customerId, String email) {

		Map<String, Object> vars = new HashMap<>();
		vars.put(CUSTOMER_ID_PV, customerId);
		vars.put(EMAIL_PV, email);

		ProcessInstance processInstance = this.runtimeService
			.startProcessInstanceByKey("signup-process", vars);

		return processInstance.getId();
	}

	void confirmEmail(String customerId) {
	}

}
