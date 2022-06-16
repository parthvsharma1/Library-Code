package com.spr;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XMLtoClasses extends ClassPathXmlApplicationContext {
    public static Map<String, String> idToClassMap;
    public static ArrayList<Class<?>> primaryClasses;

    public ArrayList<String> getAllClassesfromXml(String[] configLocations) throws ClassNotFoundException {

        idToClassMap=new HashMap<>();
        primaryClasses=new ArrayList<>();

        ArrayList<String> beans=new ArrayList<>();
        this.setConfigLocations(configLocations);
        ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();


        String[] beanNames=this.getBeanDefinitionNames();

        for(String itr:beanNames)
        {
            BeanDefinition bd=beanFactory.getBeanDefinition(itr);
            String className=bd.getBeanClassName();
            beans.add(className);
            idToClassMap.put(itr,bd.getBeanClassName());

            Class<?> thisClass = Class.forName(className);
            if(bd.isPrimary())
                primaryClasses.add(thisClass);
        }
//
//        destroyBeans();
//
//        resetCommonCaches();

        return beans;
    }
}
