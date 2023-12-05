package com.example.flowable.aot;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FlowableMybatisMappersBeanFactoryInitializationAotProcessor
        extends MybatisMappersBeanFactoryInitializationAotProcessor {

    FlowableMybatisMappersBeanFactoryInitializationAotProcessor() {

    }

    @Override
    protected List<String> getPackagesToScan(BeanFactory b) {
        var defaults = super.getPackagesToScan(b);
        var l = new ArrayList<String>();
        l.add("org.flowable");
        l.addAll(defaults);
        return l;
    }
}
