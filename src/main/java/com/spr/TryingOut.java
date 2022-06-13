package com.spr;

import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import static com.spr.XMLtoClasses.idToClassMap;


public class TryingOut {
    public static Map<String,Set<String>> edges; // main me mat daalna
    static String[] configs =new String[] {"ApplicationContext.xml"};// hai main me

    public static void addEdgeByThis(String methodName,String className,String newnbr) throws Exception {
        TryingOut obj=new TryingOut();
        Class<?>[] paramTypes = {String.class, String.class};
        Class<?> classObj = obj.getClass();

        Method wayToAddEdge = classObj.getDeclaredMethod(methodName, paramTypes);
        wayToAddEdge.invoke(obj, className,newnbr);

    }
    public static void main(String[] args) throws Exception {

//        addEdgeByThis("haha","hoh","nm");
//        addEdgeByThis("haha","op","kpj");
        edges = new HashMap<>();//nr

        ArrayList<String> allClasses=new ArrayList<>();
        ArrayList<String> beans;

        XMLtoClasses xmLtoClasses=new XMLtoClasses();

        beans=xmLtoClasses.getAllClassesfromXml(configs);
        System.out.println("classes are");

        for(String s:beans)
            System.out.println(s);
//        System.out.println("usefull classes are ");
        beans=getusefulClasses(beans);


//        System.out.println(Arrays.deepToString(beansClass.entrySet().toArray()));

        makeDirectedGraph(beans);
        System.out.println("------------------------------- bakchodi khatam ----------------------------------------");
        printerGraph(edges);

        return;

    }
    public static Field[] getAllModelFields(Class aClass) {
        List<Field> fields = new ArrayList<>();
        do {
            Collections.addAll(fields, aClass.getDeclaredFields());
            aClass = aClass.getSuperclass();
        } while (aClass != null);

        Field[] field=new Field[fields.size()];
        for(int i=0;i<fields.size();i++)
            field[i]=fields.get(i);
        return field;
    }

    private static void makeDirectedGraph(ArrayList<String> beans) throws ClassNotFoundException {

        for(String className:beans){
            System.out.println("\n"+className+" has dependencies on :");

            Class<?> thisClass = Class.forName(className);    // should not have a prefix "class"


            try {

//                Field[] fields=thisClass.getDeclaredFields();       // declared se private bhi aa jaaenge but inherited nahi aenge
                Field[] fields=getAllModelFields(thisClass);
                Constructor[] constructors= thisClass.getConstructors();


                // find auto wires in fields (works with reqd=false as well)
                for(int i=0;i<fields.length;i++){
                    if(fields[i].isAnnotationPresent(Autowired.class)!=false)
                    {
                        if(fields[i].getType().isInterface())
                        {
                            if(fields[i].isAnnotationPresent((Qualifier.class))!=false)
                            {
                                String qualifierName=fields[i].getAnnotation(Qualifier.class).value();

                                System.out.println("qualifier value is : "+ qualifierName);

                                // isss qualifier ki class pata karo
                                String qualifierClass= idToClassMap.get(qualifierName);
                                if(qualifierClass!=null)
                                    addEdgeByThis("addEdge",className,qualifierClass);

                            }
                            else
                            {
                                boolean foundPrimary=false;

                                //get all implementations
                                Class myClass=fields[i].getType();
                                Reflections reflections = new Reflections();
                                Set< Class<?> > implementations = reflections.getSubTypesOf(myClass);
                                if(implementations.size()==0){
                                    System.out.println("no implementation found of interface "+
                                                                        fields[i].getType().getName());
                                    return;
                                }

                                if(implementations.size()==1)
                                {
//                                    System.out.println("only implementation is ");
                                    for (Class<?> implementation : implementations){
                                        System.out.println(implementation.getName());

                                        addEdgeByThis("addEdge",className,implementation.getName());
//
                                        foundPrimary=true;
                                    }

                                }

                                else {

                                    for (Class<?> implementation : implementations) {
                                        if (implementation.isAnnotationPresent(Primary.class) != false) {
                                            foundPrimary = true;
                                            System.out.println(implementation.getName() + " is primary");
                                            addEdgeByThis("addEdge",className,implementation.getName());

                                            break;
                                        }
//                                    System.out.println(implementation.getName());
                                    }

                                }


                                if(foundPrimary==false)
                                {
                                        System.out.println("multiple implementations are present " +
                                                "which cannot be resolved");
                                        return;
                                }

                            }
                        }

                        // not an interface
                        else
                        {
                            String newnbr = fields[i].getType().getName();
//                        String newnbr2=fields[i].getName();
                            System.out.println(newnbr);
                            addEdgeByThis("addEdge",className,newnbr);
                        }
                    }

                }

//              finding autowires in constructors
                for(int i=0;i< constructors.length;i++)
                {
                    if(constructors[i].isAnnotationPresent(Autowired.class)!=false)
                    {
                        Parameter[] parameters=constructors[i].getParameters();
                        for(int j=0;j< parameters.length;j++)
                        {

                            if(parameters[j].getType().isInterface())
                            {

                                if (parameters[j].isAnnotationPresent(Qualifier.class) != false)
                                {
                                    String qualifierName = parameters[j].getAnnotation(Qualifier.class).value();

                                    System.out.println("qualifier value is : " + qualifierName);

                                    // isss qualifier ki class pata karo
                                    String qualifierClass = idToClassMap.get(qualifierName);
                                    if(qualifierClass!=null)
                                        addEdgeByThis("addEdge",className,qualifierClass);


                                }

                                else
                                {
                                    boolean foundPrimary=false;

                                    //get all implementations
                                    Class myClass=parameters[j].getType();
                                    Reflections reflections = new Reflections();
                                    Set< Class<?> > implementations = reflections.getSubTypesOf(myClass);

                                    if(implementations.size()==0)
                                    {
                                        System.out.println("no implementation found of interface "+
                                                parameters[j].getType().getName());
                                        return;
                                    }

                                    if(implementations.size()==1)
                                    {
//                                    System.out.println("only implementation is ");
                                        for (Class<?> implementation : implementations){
                                            System.out.println(implementation.getName());
                                            addEdgeByThis("addEdge",className,implementation.getName());
                                            foundPrimary=true;
                                        }

                                    }

                                    else {

                                        for (Class<?> implementation : implementations) {
                                            if (implementation.isAnnotationPresent(Primary.class) != false) {
                                                foundPrimary = true;
                                                System.out.println(implementation.getName() + " is primary");
                                                addEdgeByThis("addEdge",className,implementation.getName());

                                                break;
                                            }
//                                    System.out.println(implementation.getName());
                                        }

                                    }


                                    if(foundPrimary==false)
                                    {
                                        System.out.println("multiple implementations are present " +
                                                "which cannot be resolved");
                                        return;
                                    }

                                }

                            }


                            else
                            {
                                    String newnbr = parameters[i].getType().getName();
    //                                System.out.println(newnbr);
                                addEdgeByThis("addEdge",className,newnbr);

                            }

                        }
//                        List<Class> arguments= Arrays.stream(constructors[i].getParameterTypes()).collect(toList());
//                        for(int j=0;j<arguments.size();j++){
//                           System.out.println(arguments.get(i));
//                            if(arguments.get(i).isInterface()==true)
//                            {
//
//                                if(arguments.get(i).isAnnotationPresent((Qualifier.class))!=false)
//                                {
//                                    String qualifierName=arguments.get(i).getAnnotation(Qualifier.class).toString();
////                                    System.out.println("qualifier is : "+qualifierName);
//
////                                    System.out.println("qualifier value is : "+ qualifierName);
////
////                                    // isss qualifier ki class pata karo
////                                    String qualifierClass= beansClass.get(qualifierName);
////                                    addEdge(className,qualifierClass);
//
//                                }
//                            }

                        }


//                        for(int j=0;j<arguments.size();j++)
//                        {
//                            String newnbr=arguments.get(j).getTypeName();
//                            System.out.println(newnbr);
//                            addEdge(className,newnbr);
//
//                        }


                    }
                }




            catch (Throwable e) {
                System.err.println(e);
            }


        }

    }



    // replica of main
    public static void addEdge(String className,String newnbr)
    {
        String updateClassName=className;
        Set<String> currnbrs=edges.get(updateClassName);

        if(currnbrs!=null)
        {
            currnbrs.add(newnbr);
            edges.put(updateClassName, currnbrs);

        }
        else {
            Set<String> currnbr = new HashSet<>();
            currnbr.add(newnbr);
            edges.put(updateClassName,currnbr);

        }
    }

    //replica of main
    private static void printerGraph(Map<String,Set<String>> edgess)
    {
        System.out.println("graph looks like : ");
        System.out.println(Arrays.deepToString(edgess.entrySet().toArray()));
    }



    public static ArrayList<String> getusefulClasses(ArrayList<String> allClasses) throws ClassNotFoundException {

        ArrayList<String> beans=new ArrayList<>();
        for(String className:allClasses)
        {
            Class<?> thisClass = Class.forName(className);
            if( thisClass.isAnnotationPresent(Component.class)!=false) {
                beans.add(thisClass.getName());
            }
            /// automatically find sub compnents
            else if (thisClass.isAnnotationPresent(Service.class)!=false || thisClass.isAnnotationPresent(Repository.class)!=false || thisClass.isAnnotationPresent(Controller.class)!=false || thisClass.isAnnotationPresent(Configuration.class)!=false) {
                beans.add(thisClass.getName());
            }

        }

        return beans;
    }

}
