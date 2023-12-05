package com.example.flowable.aot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Register hints based upon the structure of a particular user's Spring Boot application
 * packages and {@link org.springframework.beans.factory.BeanFactory}
 *
 * @author Josh Long
 */
class MybatisApplicationSpecificBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

	private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	MybatisApplicationSpecificBeanFactoryInitializationAotProcessor() {
	}

	private Collection<Resource> attemptToRegisterXmlResourcesForBasePackage(
			ConfigurableListableBeanFactory beanFactory) throws Exception {
		var set = new HashSet<Resource>();
		for (var packageName : AutoConfigurationPackages.get(beanFactory)) {
			Assert.hasText(packageName, "the package name must not be empty!");
			var path = AotUtils.packageToPath(packageName);
			for (var resolvedXmlResource : this.resourcePatternResolver
				.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + path + "/**/*.xml")) {
				var fqn = resolvedXmlResource.getURI().toString();
				if (resolvedXmlResource.exists()) {
					var np = fqn.substring(fqn.indexOf(path));
					var npr = new ClassPathResource(np);
					set.add(npr);
				}
			}
		}
		return set;
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {

		if (!ClassUtils.isPresent("org.mybatis.spring.mapper.MapperFactoryBean", beanFactory.getBeanClassLoader()))
			return null;

		try {
			var classesToRegister = new HashSet<Class<?>>();
			var proxiesToRegister = new HashSet<Class<?>>();
			var resourcesToRegister = new HashSet<Resource>();
			resourcesToRegister.addAll(attemptToRegisterXmlResourcesForBasePackage(beanFactory));
			var beanNames = beanFactory.getBeanNamesForType(MapperFactoryBean.class);
			for (var beanName : beanNames) {
				var beanDefinition = beanFactory.getBeanDefinition(beanName.substring(1));
				var mapperInterface = beanDefinition.getPropertyValues().getPropertyValue("mapperInterface");
				if (mapperInterface != null && mapperInterface.getValue() != null) {
					var mapperInterfaceType = (Class<?>) mapperInterface.getValue();
					if (mapperInterfaceType != null) {
						proxiesToRegister.add(mapperInterfaceType);
						resourcesToRegister
							.add(new ClassPathResource(mapperInterfaceType.getName().replace('.', '/').concat(".xml")));
						registerReflectionTypeIfNecessary(mapperInterfaceType, classesToRegister);
						registerMapperRelationships(mapperInterfaceType, classesToRegister);
					}
				}
			}
			return (generationContext, beanFactoryInitializationCode) -> {
				var mcs = MemberCategory.values();
				var runtimeHints = generationContext.getRuntimeHints();
				AotUtils.debug("proxies", proxiesToRegister);
				AotUtils.debug("classes for reflection", classesToRegister);
				AotUtils.debug("resources", resourcesToRegister);
				for (var c : proxiesToRegister) {
					runtimeHints.proxies().registerJdkProxy(c);
					runtimeHints.reflection().registerType(c, mcs);
				}
				for (var c : classesToRegister) {
					runtimeHints.reflection().registerType(c, mcs);
					if (AotUtils.isSerializable(c))
						runtimeHints.serialization().registerType(TypeReference.of(c.getName()));
				}
				for (var r : resourcesToRegister) {
					if (r.exists()) {
						runtimeHints.resources().registerResource(r);
					}
				}
			};
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SafeVarargs
	private <T extends Annotation> void registerSqlProviderTypes(Method method, Set<Class<?>> registry,
			Class<T> annotationType, Function<T, Class<?>>... providerTypeResolvers) {
		for (T annotation : method.getAnnotationsByType(annotationType)) {
			for (Function<T, Class<?>> providerTypeResolver : providerTypeResolvers) {
				registerReflectionTypeIfNecessary(providerTypeResolver.apply(annotation), registry);
			}
		}
	}

	private void registerReflectionTypeIfNecessary(Class<?> type, Set<Class<?>> registry) {
		if (!type.isPrimitive() && !type.getName().startsWith("java")) {
			registry.add(type);
		}
	}

	private void registerMapperRelationships(Class<?> mapperInterfaceType, Set<Class<?>> registry) {
		var methods = ReflectionUtils.getAllDeclaredMethods(mapperInterfaceType);
		for (var method : methods) {
			if (method.getDeclaringClass() != Object.class) {

				ReflectionUtils.makeAccessible(method);

				registerSqlProviderTypes(method, registry, SelectProvider.class, SelectProvider::value,
						SelectProvider::type);
				registerSqlProviderTypes(method, registry, InsertProvider.class, InsertProvider::value,
						InsertProvider::type);
				registerSqlProviderTypes(method, registry, UpdateProvider.class, UpdateProvider::value,
						UpdateProvider::type);
				registerSqlProviderTypes(method, registry, DeleteProvider.class, DeleteProvider::value,
						DeleteProvider::type);

				var returnType = MybatisMapperTypeUtils.resolveReturnClass(mapperInterfaceType, method);
				registerReflectionTypeIfNecessary(returnType, registry);

				MybatisMapperTypeUtils.resolveParameterClasses(mapperInterfaceType, method)
					.forEach(x -> registerReflectionTypeIfNecessary(x, registry));
			}
		}
	}

}
