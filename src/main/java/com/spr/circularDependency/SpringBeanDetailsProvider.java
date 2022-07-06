package com.spr.circularDependency;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import java.util.*;

public class SpringBeanDetailsProvider extends ClassPathXmlApplicationContext {

    private final String basePackage;
    private boolean initialized;
    private Map<String, Class<?>> beanIdToClassMap;
    private List<Class<?>> primaryBeans;

    public SpringBeanDetailsProvider(String basePackage, String[] configLocations) throws BeansException {
        super(configLocations, false, null);
        this.basePackage = basePackage;
    }

    public void init() throws Exception {
        if (initialized) {
            return;
        }
        if (StringUtils.isEmpty(basePackage)) {
            throw new IllegalArgumentException("Base package must be provided");
        }

        Map<String, Class<?>> beanIdMap = new HashMap<>();
        List<Class<?>> primaryClasses = new ArrayList<>();

        ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();

        String[] beanIds = getBeanDefinitionNames();

        for (String beanId : beanIds) {
            AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) beanFactory.getBeanDefinition(beanId);
            Class<?> clazz = beanDefinition.resolveBeanClass(ClassLoader.getSystemClassLoader());
            if (clazz == null) {
                throw new RuntimeException("Class not found for beanId " + beanId);
            }
            String className = clazz.getName();
            if (!className.startsWith(basePackage)) {
                continue;
            }

            beanIdMap.put(beanId, clazz);
            if (beanDefinition.isPrimary()) {
                primaryClasses.add(clazz);
            }
        }

        this.beanIdToClassMap = Collections.unmodifiableMap(beanIdMap);
        this.primaryBeans = Collections.unmodifiableList(primaryClasses);
        initialized = true;
    }

    public String getClassName(String beanId) {
        checkIfInitialized();
        Class<?> clazz =  beanIdToClassMap.get(beanId);
        if (clazz == null) {
            return null;
        }
        return clazz.getName();
    }

    public String getPrimaryBean(Class<?> clazz) {
        checkIfInitialized();
        for (Class<?> primaryBean : primaryBeans) {
           if (clazz.isAssignableFrom(primaryBean)) {
               return primaryBean.getName();
           }
        }
        return null;
    }

    public List<Class<?>> getAllBeans() {
        checkIfInitialized();
        return new ArrayList<>(beanIdToClassMap.values());
    }

    private void checkIfInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Must call init() before using on this method");
        }
    }
}
