package com.example.flowable.aot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
class FlowableAotAutoConfiguration {

    @Bean
    static FlowableMappersBeanFactoryInitializationAotProcessor flowableMappersBeanFactoryInitializationAotProcessor(PathMatchingResourcePatternResolver re ){
        return new FlowableMappersBeanFactoryInitializationAotProcessor(re);
    }

    @Bean
    static FlowableBeanFactoryInitializationAotProcessor hints(PathMatchingResourcePatternResolver patternResolver) {
        return new FlowableBeanFactoryInitializationAotProcessor(patternResolver);
    }
}
