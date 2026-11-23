package org.sid.Auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.TestExecutionListeners(
        listeners = org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        mergeMode = org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
