package com.example.flowable.bpmn;

import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@Log4j2
@RestController
class SingleTaskProcessRestController {

	private final SingleTaskService taskService;

	public SingleTaskProcessRestController(
		SingleTaskService sts) {
		this.taskService = sts;
	}

	@PostMapping("/insurance")
	String launchInsuranceFlow(@RequestBody Map<String, Object> params) {
		String customerId = (String) params.get("customerId");
		String email = (String) params.get("email");
		return this.taskService.launch(customerId, email);
	}

	@GetMapping("/insurance/{customerId}/tasks")
	Collection<SingleTaskService.Task> getOutstandingTasksForProcessInstance(@PathVariable String customerId) {
		return this.taskService.getTasksFor(customerId);
	}

	@PostMapping("/insurance/{customerId}/tasks/{taskName}")
	void complete(@RequestBody Map<String, Object> params,
															@PathVariable String customerId,
															@PathVariable String taskName) {

		this.taskService.complete(customerId, taskName);

	}

}
