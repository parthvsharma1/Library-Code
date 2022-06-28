package com.spr;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.*;

public class GitBlamer {

    public static ArrayList<Dependency> classDependencies=new ArrayList<>();
    private static String loop="";
    private static String baseElement="";

    public void findCircularDependency(String packageName, String... configLocations) throws Exception {
        XMLtoClasses xmLtoClasses = new XMLtoClasses(packageName, configLocations);
        List<Class<?>> beans = xmLtoClasses.getPackageBeans();
        resolveDependencies(beans);

        classDependencies.sort((o1, o2) -> o2.getDateTimestamp().compareTo(o1.getDateTimestamp()));

        for (Dependency classDependency : classDependencies) {
            System.out.println(classDependency);
        }



        //make graph
        boolean isCycle=false;
        Map<String, Set<String>> graph=new HashMap<>() ;
        for(int i=0;i< classDependencies.size();i++)
        {

            //adding edge
            Set<String> currnbrs=graph.get(classDependencies.get(i).getFromBean());
            if(currnbrs!=null)
            {
                currnbrs.add(classDependencies.get(i).getToBean());
                graph.put(classDependencies.get(i).getFromBean(), currnbrs);

            }
            else {
                Set<String> currnbr = new HashSet<>();
                currnbr.add(classDependencies.get(i).getToBean());
                graph.put(classDependencies.get(i).getFromBean(),currnbr);

            }

            //checking if this edge lead to cycle formation
            if(findCycle(graph))
            {
                isCycle=true;
                System.out.println("cycle is : \n"+loop+"\n");
                break;
//                System.out.println("found cycle due to commit "+classDependencies.get(i).commitId);
//                System.out.println("the faulty dependency is "
//                        +classDependencies.get(i).from+" to "+classDependencies.get(i).to);
//
//                return;
            }
        }
        if(!isCycle)
        {
            System.out.println("No cycle found !!!!!!!");
            return;
        }

        //remove edges
        for(int i=0;i< classDependencies.size();i++){
            Set<String> currnbrs=graph.get(classDependencies.get(i).getFromBean());
            currnbrs.remove(classDependencies.get(i).getToBean());
            graph.put(classDependencies.get(i).getFromBean(), currnbrs);

            if(!findCycle(graph)){
                System.out.println("found cycle due to commit "+classDependencies.get(i).getCommitHash());
                System.out.println("the faulty dependency is "
                        +classDependencies.get(i).getFromBean()+" to "+classDependencies.get(i).getToBean());
                return;
//
            }
        }


    }

    private static void addDependencyField(String currClassName, String line,String fieldName) {
//        System.out.println(currClassName+"->"+fieldName);
        String[] splited = line.split("\\s+");
        String commitId=splited[0];
        String commitTime=splited[2]+splited[3];


        Dependency d=new Dependency(currClassName,fieldName,commitTime,commitId);
        classDependencies.add(d);

    }

    private static void addDependencyConstructor(String currClassName, String line,Parameter[] parameters) {
        String[] splited = line.split("\\s+");
        String commitId = splited[0];
        String commitTime = splited[2] + splited[3];

        for(Parameter p:parameters){

            //check for qualifier in each parameter
            if(p.isAnnotationPresent(Qualifier.class)){

                String qualifierName=p.getAnnotation(Qualifier.class).value();
                String qualifierClass= idToClassMap.get(qualifierName);

                if(qualifierClass!=null){
                    Dependency d=new Dependency(currClassName,qualifierClass,commitTime,commitId);
                    classDependencies.add(d);
                }
                else
                    throw new RuntimeException("no class with given qualifier");

            }

            else {

                boolean foundPrimary=false;
                for(Class<?> primary:primaryClasses) {
                    if (p.getType().isAssignableFrom(primary)) {
                        Dependency d = new Dependency(currClassName,primary.getName(), commitTime, commitId);
                        classDependencies.add(d);
                        foundPrimary = true;
                        break;
                    }
                }
                if(!foundPrimary) {
                    String parameterName = p.getType().getName();
                    Dependency d = new Dependency(currClassName, parameterName, commitTime, commitId);
                    classDependencies.add(d);
                }
//                System.out.println(currClassName + "->" + parameterName);
            }
        }
    }



    private void resolveDependencies(List<Class<?>> beans) throws Exception {
        for (Class<?> bean : beans) {
            String simpleName = bean.getSimpleName();

            Field[] fields = bean.getDeclaredFields();
            Constructor<?>[] constructors= bean.getConstructors();

            ArrayList<Field> autowireField = new ArrayList<>();
            for (Field field : fields)
                if (field.isAnnotationPresent(Autowired.class))
                    autowireField.add(field);
            }

            ArrayList<Constructor> autowireConstructor=new ArrayList<>();
            for(Constructor c:constructors){
                if(c.isAnnotationPresent(Autowired.class)!=false)
                    autowireConstructor.add(c);
            }


            String location= bean.getProtectionDomain().getCodeSource().getLocation().getPath();
            location=location.replaceAll("build/classes/java/main","src/main/java");

            String nameToPath=className.replace('.','/');
            String classPath=location+nameToPath;
            classPath+=".java";
//
//            System.out.println(className+":");
//            System.out.println(location);
//            System.out.println(classPath);
//            if(1>0) continue;

            String command = "git blame " + classPath;
            Process proc = Runtime.getRuntime().exec(command,null,new File(location));
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            String prev = "-1";// when we find any class definition, we have to check if there is @component annotation before it
            boolean isAutowired = false;


            int fieldCounter=0;
            int constructorCounter=0;
            String line;

//            if(1>0) return;
            while ((line = reader.readLine()) != null) {

                if (isAutowired) {

                    /* ignore other annotations*/
                    while (line.contains("@"))
                        line= reader.readLine();

                    //for constructor
                    if (line.contains(" " +name + "(") || line.contains(" " + name + " (")) // add prefix " " tp avoid substring instrance of class name in another class
                    {
//                        System.out.println("constructor autowire");
                        Parameter[] parameters=autowireConstructor.get(constructorCounter).getParameters();
                        addDependencyConstructor(className, line,parameters);
                        constructorCounter++;
                    }

                    //for field
                    else
                    {
//                        System.out.println("field autowire");

                        Field currField=autowireField.get(fieldCounter);

                        if(currField.isAnnotationPresent((Qualifier.class))){
//                            System.out.println("found a Qualifer ");
                            String qualifierName=currField.getAnnotation(Qualifier.class).value();

                            String qualifierClass= idToClassMap.get(qualifierName);
                            if(qualifierClass!=null)
                                addDependencyField(className,line,qualifierClass);
                            else
                                throw new RuntimeException("no class with given qualifier");

                        }

                        else {
                            boolean foundPrimary=false;
                            for(Class<?> primary:primaryClasses){
                                if(currField.getType().isAssignableFrom(primary)){
                                    addDependencyField(className,line,primary.getName());
                                    foundPrimary=true;
                                    break;
                                }
                            }
                            if(!foundPrimary) {
                                String fieldName = autowireField.get(fieldCounter).getType().getName();
                                addDependencyField(className, line, fieldName);
                            }

                        }

                        fieldCounter++;
                    }
                }

                if (line.contains("class " +name )) {
//                System.out.println(line);
                    if (!(prev.contains("@Component") || prev.contains("@Service") || prev.contains("@Repository")))
                        break;

                }

                isAutowired = line.contains("@Autowired");

                prev = line;
            }

        }
    }

    public static boolean findCycle(Map<String,Set<String>> ed) {
        loop="";
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String s : ed.keySet())
        {
            if (isCyclicUtil(s, visited, recStack,ed))
            {
//                System.out.println("cycle is "+loop);
                return true;
            }

        }
        return false;
    }

    public static boolean isCyclicUtil(String i, Set<String> visited, Set<String> recStack,Map<String,Set<String>> ed) {
        if (recStack.contains(i)) {
            loop+="<- ";
            baseElement = i;
            return true;
        }
        if (visited.contains(i))
            return false;

        recStack.add(i);
        visited.add(i);
        Set<String> children = ed.get(i);
        if (children == null)
        {
            recStack.remove(i);
            return false;
        }
        for (String child : children) {
            if (isCyclicUtil(child, visited, recStack,ed)) {
                if (!baseElement.equals(""))
                {
                    loop += child;
                    loop += " <- ";
                }
                else if (child.equals(baseElement))
                {
                    baseElement = "";
                }
                return true;
            }
        }

        recStack.remove(i);
        return false;
    }


}

/*

/Users/parth.sharma/Documents/CircularDependencylts/src/main/java/org/parth/B.java

/Users/parth.sharma/Documents/CircularDependencylts/src/main/java/org/parth/B.java
 */
