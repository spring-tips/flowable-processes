package com.example.flowable.bpmn;

import lombok.extern.log4j.Log4j2;
import org.flowable.engine.ProcessEngine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@RunWith(SpringRunner.class)
@Log4j2
@SpringBootTest
public class SingleTaskServiceTest {

	@Autowired
	private SingleTaskService singleTaskService;

	@Autowired
	private ProcessEngine processEngine;

	@Autowired
	private EmailService emailService;

	@Test
	public void demo() {
		log.info("PID : " + this.singleTaskService.launch("1", "1@email.com"));
		log.info("PID : " + this.singleTaskService.launch("1", "1@email.com"));

		var tasks = this.singleTaskService.getTasksFor("1");
		Assert.assertEquals(tasks.size(), 2);

		this.singleTaskService.complete("1", tasks.iterator().next().getName());

		// verify that the processes are done
		var processInstances = this.processEngine
			.getHistoryService()
			.createHistoricProcessInstanceQuery()
			.includeProcessVariables()
			.variableValueEquals(SingleTaskService.CUSTOMER_ID_PV, "1")
			.list();
		Assert.assertEquals(processInstances.size(), 2);
		Assert.assertEquals(this.emailService.emails.size(), 2);

	}

}