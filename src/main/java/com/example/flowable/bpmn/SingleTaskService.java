package com.example.flowable.bpmn;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@Log4j2
@Service
@Transactional
class SingleTaskService {

	SingleTaskService(RuntimeService runtimeService, TaskService taskService) {
		this.runtimeService = runtimeService;
		this.taskService = taskService;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Task {
		private String id, name, email;
	}

	private final RuntimeService runtimeService;
	private final TaskService taskService;

	public static final String CUSTOMER_ID_PV = "customerId";
	public static final String EMAIL_PV = "email";

	public String launch(String customerId, String email) {
		var vars = Map.of(
			CUSTOMER_ID_PV, (Object) customerId,
			EMAIL_PV, email
		);
		var oneTaskProcess = this.runtimeService.startProcessInstanceByKey("oneTaskProcess", vars);
		return oneTaskProcess.getProcessInstanceId();
	}

	public Collection<Task> getTasksFor(String customerId) {

		return this.taskService
			.createTaskQuery()
			.processVariableValueEquals(CUSTOMER_ID_PV, customerId)
			.includeProcessVariables()
			.list()
			.stream()
			.map(t -> new Task(t.getId(), t.getName(),
				(String) t.getProcessVariables().get(EMAIL_PV)))
			.collect(Collectors.toSet());
	}

	public void complete(String customerId, String taskName) {

		List<String> collect = this.taskService
			.createTaskQuery()
			.taskName(taskName)
			.processVariableValueEquals(CUSTOMER_ID_PV, customerId)
			.includeProcessVariables()
			.active()
			.list()
			.stream()
			.map(TaskInfo::getId)
			.collect(Collectors.toList());

		log.info("obtained " + collect.size() + " results.");

		collect.forEach(this.taskService::complete);



	}


}
