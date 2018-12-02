package com.example.flowable.bpmn;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Log4j2
class EmailService {

	// for testing
	List<String> emails = new CopyOnWriteArrayList<>();

	public void sendWelcomeEmail( String customerId, String email) {
		log.info("sending an email to " + email);
		this.emails.add( email);
	}
}
