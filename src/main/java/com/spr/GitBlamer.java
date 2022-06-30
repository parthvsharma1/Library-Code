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

    private final ExtractClassfromXml xmLtoClasses;
    private final List<Class<?>> beans;
    public GitBlamer(String packageName, String[] configLocations) throws ClassNotFoundException {
        xmLtoClasses = new ExtractClassfromXml(packageName, configLocations);
        beans = xmLtoClasses.getPackageBeans();
    }

    public void findCircularDependency() throws Exception {

        resolveDependencies(beans);

        // TODO : sort with latest first
        classDependencies.sort((o1, o2) -> o2.getDateTimestamp().compareTo(o1.getDateTimestamp()));

        for (Dependency classDependency : classDependencies) {
            System.out.println(classDependency);
        }


        // TODO : MAKE GRAPH
        boolean isCycle=false;
        Map<String, Set<String>> graph=new HashMap<>() ;
        for(int i=0;i< classDependencies.size();i++)
        {

            //adding edge
            Set<String> currnbrs=graph.get(classDependencies.get(i).getFromBean());
            if(currnbrs!=null) {
                currnbrs.add(classDependencies.get(i).getToBean());
                graph.put(classDependencies.get(i).getFromBean(), currnbrs);

            }
            else {
                Set<String> currnbr = new HashSet<>();
                currnbr.add(classDependencies.get(i).getToBean());
                graph.put(classDependencies.get(i).getFromBean(),currnbr);

            }

            // TODO : checking if this edge lead to cycle formation
            if(findCycle(graph)) {
                isCycle=true;
                System.out.println("cycle is : \n"+loop+"\n");
                break;
            }
        }

        if(!isCycle) {
            System.out.println("No cycle found !!!!!!!");
            return;
        }

        // TODO : remove edges and check if it removes cycle
        for(int i=0;i< classDependencies.size();i++){
            Set<String> currnbrs=graph.get(classDependencies.get(i).getFromBean());
            currnbrs.remove(classDependencies.get(i).getToBean());
            graph.put(classDependencies.get(i).getFromBean(), currnbrs);

            if(!findCycle(graph)){
                System.out.println("found cycle due to commit "+classDependencies.get(i).getCommitHash());
                System.out.println("the faulty dependency is "
                        +classDependencies.get(i).getFromBean()+" to "+classDependencies.get(i).getToBean());
                return;

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

    private void addDependencyConstructor(String currClassName, String line,Parameter[] parameters) {
        String[] splited = line.split("\\s+");
        String commitId = splited[0];
        String commitTime = splited[2] + splited[3];

        for(Parameter p:parameters){

            //check for qualifier in each parameter
            if(p.isAnnotationPresent(Qualifier.class)){

                String qualifierName=p.getAnnotation(Qualifier.class).value();
                String qualifierClass = xmLtoClasses.getClassName(qualifierName);

                if(qualifierClass!=null){
                    Dependency d=new Dependency(currClassName,qualifierClass,commitTime,commitId);
                    classDependencies.add(d);
                }
                else
                    throw new RuntimeException("no class with given qualifier");

            }

            else {

                boolean foundPrimary=false;

                   String primaryImplement = xmLtoClasses.getPrimaryBean(p.getClass());
                    if (primaryImplement!=null) {
                        Dependency d = new Dependency(currClassName,primaryImplement, commitTime, commitId);
                        classDependencies.add(d);
                        foundPrimary = true;

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
            String className=bean.getName();

            Field[] fields = bean.getDeclaredFields();
            Constructor<?>[] constructors= bean.getConstructors();

            List<Field> autowireField = new ArrayList<>();
            for (Field field : fields){
                if (field.isAnnotationPresent(Autowired.class))
                    autowireField.add(field);
            }

            List<Constructor> autowireConstructor=new ArrayList<>();
            for(Constructor c:constructors){
                if(c.isAnnotationPresent(Autowired.class)!=false)
                    autowireConstructor.add(c);
            }


            String classLocation= bean.getProtectionDomain().getCodeSource().getLocation().getPath();
            classLocation=classLocation.replaceAll("build/classes/java/main","src/main/java");

            String pathFromRoot=className.replace('.','/');
            String completeClassPath=classLocation+pathFromRoot;
            completeClassPath+=".java";
//
//            System.out.println(className+":");
//            System.out.println(classLocation);
//            System.out.println(completeClassPath);
//            if(1>0) continue;

            String command = "git blame " + completeClassPath;
            Process proc = Runtime.getRuntime().exec(command,null,new File(classLocation));
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

//            String prev = "-1";// when we find any class definition, we have to check if there is @component annotation before it
            boolean isAutowired = false;


            int fieldCounter=0;
            int constructorCounter=0;
            String readLine;

            while ((readLine = reader.readLine()) != null) {

                if (isAutowired) {

                    // TODO : ignore other annotations if any
                    while (readLine.contains("@"))
                        readLine= reader.readLine();

                    // TODO : auto-wired constructor
                    // TODO :add prefix " " to avoid substring instrance of class name in another class
                    if (readLine.contains(" " +simpleName + "(") || readLine.contains(" " + simpleName + " (")) {
                        Parameter[] parameters=autowireConstructor.get(constructorCounter).getParameters();
                        addDependencyConstructor(className, readLine,parameters);
                        constructorCounter++;
                    }

                    // TODO : autowired field
                    else
                    {

                        Field currField=autowireField.get(fieldCounter);

                        if(currField.isAnnotationPresent((Qualifier.class))){
                            String qualifierName=currField.getAnnotation(Qualifier.class).value();

                            String qualifierClass = xmLtoClasses.getClassName(qualifierName);

                            if(qualifierClass!=null)
                                addDependencyField(className,readLine,qualifierClass);
                            else
                                throw new RuntimeException("no class with given qualifier");

                        }

                        else {
                            boolean foundPrimary=false;
                            String primaryImplement = xmLtoClasses.getPrimaryBean(currField.getClass());

                            if(primaryImplement!=null) {
                                addDependencyField(className, readLine, primaryImplement);
                                foundPrimary = true;
                            }

                            if(!foundPrimary) {
                                String fieldName = autowireField.get(fieldCounter).getType().getName();
                                addDependencyField(className, readLine, fieldName);
                            }

                        }

                        fieldCounter++;
                    }
                }

//                if (readLine.contains("class " +simpleName )) {
////                System.out.println(line);
//                    if (!(prev.contains("@Component") || prev.contains("@Service") || prev.contains("@Repository")))
//                        break;
//
//                }

                isAutowired = readLine.contains("@Autowired");

//                prev = readLine;
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
