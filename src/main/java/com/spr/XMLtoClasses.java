package com.spr;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import java.util.*;

public class XMLtoClasses extends ClassPathXmlApplicationContext {

    private final String basePackage;
    private final Map<String, Class<?>> beanIdToClassMap;
    private final List<Class<?>> primaryBeans;

    public XMLtoClasses(String basePackage, String... configLocations) throws BeansException {
        super(configLocations, false, null);

        if (StringUtils.isEmpty(basePackage)) {
            throw new IllegalArgumentException("Base package must be provided");
        }
        this.basePackage = basePackage;

        Map<String, Class<?>> beanIdMap = new HashMap<>();
        List<Class<?>> primaryClasses = new ArrayList<>();

        ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();
        String[] beanIds = this.getBeanDefinitionNames();
        for (String beanId : beanIds) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanId);
            Class<?> clazz = beanDefinition.getClass();
            if (!clazz.getName().startsWith(basePackage)) {
                continue;
            }
            beanIdMap.put(beanId, clazz);
            if (beanDefinition.isPrimary()) {
                primaryClasses.add(clazz);
            }
        }

        this.beanIdToClassMap = Collections.unmodifiableMap(beanIdMap);
        this.primaryBeans = Collections.unmodifiableList(primaryClasses);
    }

    public String getClassName(String beanId) {
        Class<?> clazz =  beanIdToClassMap.get(beanId);
        if (clazz == null) {
            return null;
        }
        return clazz.getName();
    }

    public String getPrimaryBean(Class<?> clazz) {
        for (Class<?> primaryBean : primaryBeans) {
           if (clazz.isAssignableFrom(primaryBean)) {
               return primaryBean.getName();
           }
        }
        return null;
    }

    // TODO : change name
    public List<Class<?>> getPackageBeans() {
        return new ArrayList<>(beanIdToClassMap.values());
    }
}
