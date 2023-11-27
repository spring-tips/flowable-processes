package com.example.flowable.aot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * @author Josh Long
 */
@Configuration
class MybatisAotAutoConfiguration {

	@Bean
	static FlowableMappersBeanFactoryInitializationAotProcessor flowableMappersBeanFactoryInitializationAotProcessor (PathMatchingResourcePatternResolver re ){
		return new FlowableMappersBeanFactoryInitializationAotProcessor(re);
	}

	@Bean
	static PathMatchingResourcePatternResolver patternResolver() {
		return new PathMatchingResourcePatternResolver();
	}

	@Bean
	static GlobalBeanFactoryInitializationAotProcessor myBatisGlobalHintsBeanFactoryInitializationAotProcessor(
			PathMatchingResourcePatternResolver patternResolver) {
		return new GlobalBeanFactoryInitializationAotProcessor(patternResolver);
	}

	@Bean
	static ApplicationSpecificBeanFactoryInitializationAotProcessor myBatisBeanFactoryInitializationAotProcessor(
			PathMatchingResourcePatternResolver patternResolver) {
		return new ApplicationSpecificBeanFactoryInitializationAotProcessor(patternResolver);
	}

	@Bean
	static MappersBeanFactoryInitializationAotProcessor mappersBeanFactoryInitializationAotProcessor(
			PathMatchingResourcePatternResolver patternResolver) {
		return new MappersBeanFactoryInitializationAotProcessor(patternResolver);
	}

}
