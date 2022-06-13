package com.spr;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XMLtoClasses extends ClassPathXmlApplicationContext {
    public static Map<String, String> idToClassMap;


    public ArrayList<String> getAllClassesfromXml(String[] configLocations)  {

        idToClassMap=new HashMap<>();
        ArrayList<String> beans=new ArrayList<>();
        this.setConfigLocations(configLocations);

        this.prepareRefresh();

        ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();

        prepareBeanFactory(beanFactory);

//        System.out.println("bean factory names size " + this.getBeanDefinitionNames().length);
        String[] beanNames=this.getBeanDefinitionNames();
        for(String itr:beanNames)
        {
            BeanDefinition bd=beanFactory.getBeanDefinition(itr);
//            System.out.println(itr+" -> "+bd.getBeanClassName());
            beans.add(bd.getBeanClassName());
            idToClassMap.put(itr,bd.getBeanClassName());
        }
        destroyBeans();

        resetCommonCaches();

        return beans;
    }
}
