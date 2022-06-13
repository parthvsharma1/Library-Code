package com.spr;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class tryin {
    public static void main(String[]args) {

//        Map<String,String> m=new HashMap<>();
//        m.put("hi","hjo");
//        String d=m.get("p");
//        System.out.println(d); //null milega

//        Class mc=MyInterface.class;
//        System.out.println(mc);
//        Reflections reflections = new Reflections();
//        Set<Class<?>> implementations = reflections.getSubTypesOf(mc);
//
//        for (Class<?> implementation : implementations)
//        {
//            if(implementation.isAnnotationPresent(Primary.class)!=false)
//            {
//                System.out.println(implementation.getName()+" is primary");
//            }
//            System.out.println(implementation.getName());
//        }

    }
    public static List<Field> getAllModelFields(Class aClass) {
        List<Field> fields = new ArrayList<>();
        do {
            Collections.addAll(fields, aClass.getDeclaredFields());
            aClass = aClass.getSuperclass();
        } while (aClass != null);
        return fields;
    }
}
