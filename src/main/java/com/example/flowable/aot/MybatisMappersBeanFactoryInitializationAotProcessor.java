package com.example.flowable.aot;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.FileCopyUtils;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Discovers any {@literal  mappings.xml} and reads them in to then register the
 * referenced {@literal .xml} files as resource hints.
 *
 * @author Josh Long
 */
class MybatisMappersBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

	private final PathMatchingResourcePatternResolver resolver;

	MybatisMappersBeanFactoryInitializationAotProcessor(PathMatchingResourcePatternResolver resolver) {
		this.resolver = resolver;
	}

	private Set<Resource> persistenceResources(String rootPackage) throws Exception {
		var folderFromPackage = AotUtils.packageToPath(rootPackage);
		var patterns = Stream//
			.of(folderFromPackage + "/**/mappings.xml")//
			.map(path -> ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + path)//
			.flatMap(p -> {
				try {
					return Stream.of(this.resolver.getResources(p));
				} //
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			})//
			.map(AotUtils::newResourceFor)
			.toList();

		var resources = new HashSet<Resource>();
		for (var p : patterns) {
			var mappers = mappers(p);
			resources.add(p);
			resources.addAll(mappers);
		}
		return resources.stream().filter(Resource::exists).collect(Collectors.toSet());
	}

	protected List<String> getPackagesToScan (BeanFactory b){
		return  AutoConfigurationPackages.get(b) ;
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		try {
			var packages =  getPackagesToScan(beanFactory);
			var resources = new HashSet<Resource>();
			for (var pkg : packages) {
				resources.addAll(persistenceResources(pkg));
			}
			return (generationContext, beanFactoryInitializationCode) -> {
				for (var r : resources)
					if (r.exists())
						generationContext.getRuntimeHints().resources().registerResource(r);
			};
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Set<Resource> mappers(Resource mapping) throws Exception {
		var resources = new HashSet<Resource>();
		try (var in = new InputStreamReader(mapping.getInputStream())) {
			var xml = FileCopyUtils.copyToString(in);
			resources.addAll(mapperResources(xml));
		}
		resources.add(mapping);
		return resources;

	}

	private Set<Resource> mapperResources(String xml) {
		try {
			var set = new HashSet<Resource>();
			var dbf = DocumentBuilderFactory.newInstance();
			var db = dbf.newDocumentBuilder();
			var is = new InputSource(new StringReader(xml));
			var doc = db.parse(is);
			var mappersElement = (Element) doc.getElementsByTagName("mappers").item(0);
			var mapperList = mappersElement.getElementsByTagName("mapper");
			for (var i = 0; i < mapperList.getLength(); i++) {
				var mapperElement = (Element) mapperList.item(i);
				var resourceValue = mapperElement.getAttribute("resource");
				set.add(new ClassPathResource(resourceValue));
			}
			return set;
		} //
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}

	}

}
