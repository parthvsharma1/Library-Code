package com.spr;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import java.util.*;

public class ExtractClassfromXml extends ClassPathXmlApplicationContext {

    private final Map<String, Class<?>> beanIdToClassMap;
    private final List<Class<?>> primaryBeans;

    public ExtractClassfromXml(String basePackage, String[] configLocations) throws BeansException, ClassNotFoundException {
        super(configLocations, false, null);

        if (StringUtils.isEmpty(basePackage)) {
            throw new IllegalArgumentException("Base package must be provided");
        }

        Map<String, Class<?>> beanIdMap = new HashMap<>();
        List<Class<?>> primaryClasses = new ArrayList<>();

        ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();

        String[] beanIds = getBeanDefinitionNames();

        for (String beanId : beanIds) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanId);
            String className=beanDefinition.getBeanClassName();

            if (!className.startsWith(basePackage)) {
                continue;
            }

            Class<?> clazz = Class.forName(className,false,ClassLoader.getSystemClassLoader());

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
