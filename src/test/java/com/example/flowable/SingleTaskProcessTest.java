package com.example.flowable;

import org.flowable.engine.ProcessEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootTest
@RunWith(SpringRunner.class)
public class SingleTaskProcessTest {

	@Autowired
	private ProcessEngine processEngine;

	@Test
	public void singleTaskProcess() throws Exception {

	}
}
