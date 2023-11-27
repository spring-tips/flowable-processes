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
	static PathMatchingResourcePatternResolver patternResolver() {
		return new PathMatchingResourcePatternResolver();
	}

	@Bean
	static MybatisGlobalBeanFactoryInitializationAotProcessor myBatisGlobalHintsBeanFactoryInitializationAotProcessor(
			PathMatchingResourcePatternResolver patternResolver) {
		return new MybatisGlobalBeanFactoryInitializationAotProcessor(patternResolver);
	}

	@Bean
	static MybatisApplicationSpecificBeanFactoryInitializationAotProcessor myBatisBeanFactoryInitializationAotProcessor(
			PathMatchingResourcePatternResolver patternResolver) {
		return new MybatisApplicationSpecificBeanFactoryInitializationAotProcessor(patternResolver);
	}

	@Bean
	static MybatisMappersBeanFactoryInitializationAotProcessor mappersBeanFactoryInitializationAotProcessor(
			PathMatchingResourcePatternResolver patternResolver) {
		return new MybatisMappersBeanFactoryInitializationAotProcessor(patternResolver);
	}

}
