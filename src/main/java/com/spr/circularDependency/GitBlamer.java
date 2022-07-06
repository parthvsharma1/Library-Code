package com.spr.circularDependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Pattern;

public class GitBlamer {

    private final ArrayList<Dependency> classDependencies = new ArrayList<>();

    private String cycle = "";
    private List<Dependency> dependenciesInCycle;
    private String baseElement = "";

    private final String packageName;
    private final String[] configLocations;

    public GitBlamer(String packageName, String[] configLocations) {
        this.packageName = packageName;
        this.configLocations = configLocations;
        dependenciesInCycle=new ArrayList<>();
    }

    public void findCircularDependency() throws Exception {

        SpringBeanDetailsProvider beanDetailsProvider = new SpringBeanDetailsProvider(packageName, configLocations);
        beanDetailsProvider.init();
        List<Class<?>> beans = beanDetailsProvider.getAllBeans();

        resolveDependencies(beans, beanDetailsProvider);

        // make graph
        Map<String, Set<List<String>>> graph = new HashMap<>();
        for (Dependency classDependency : classDependencies) {
            //adding edge
            Set<List<String>> currentNeighbours = graph.get(classDependency.getFromBean());
            if (currentNeighbours == null) {
                currentNeighbours = new HashSet<>();
            }

            //list has the class name+commit hash+time of adding
            List<String> neighbour = new ArrayList<>(3);
            neighbour.add(classDependency.getToBean());
            neighbour.add(classDependency.getCommitHash());
            neighbour.add(classDependency.getDateTimestamp());

            currentNeighbours.add(neighbour);

            graph.put(classDependency.getFromBean(), currentNeighbours);
        }


            if (findCycle(graph)) {
                System.out.println("cycle is : \n" + cycle + "\n");

                for(Dependency dependency:dependenciesInCycle){
                    System.out.println(dependency.toString());
                }

                // sort dependencies with latest first
                dependenciesInCycle.sort((o1, o2) -> o2.getDateTimestamp().compareTo(o1.getDateTimestamp()));
                System.out.println("faulty dependency is : "+dependenciesInCycle.get(0));
            }


        else{
            System.out.println("No cycle found !!!!!!!");
        }
        return;

    }

    private void addDependencyField(String currClassName, String gitBlameMessage, String fieldName) {
        String[] splited = gitBlameMessage.split("\\s+");
        String commitId = splited[0];
        String commitTime = splited[2] + splited[3];
        Dependency dependency = new Dependency(currClassName,fieldName,commitTime,commitId);
        classDependencies.add(dependency);
    }

    private void addDependencyConstructor(String currClassName, String line, Parameter[] parameters,
                                          SpringBeanDetailsProvider beanDetailsProvider) {
        String[] splited = line.split("\\s+");
        String commitId = splited[0];
        String commitTime = splited[2] + splited[3];

        for (Parameter parameter : parameters) {
            //check for qualifier in each parameter
            if (parameter.isAnnotationPresent(Qualifier.class)) {
                String qualifierName = parameter.getAnnotation(Qualifier.class).value();
                String qualifierClass = beanDetailsProvider.getClassName(qualifierName);
                if (qualifierClass != null) {
                    Dependency dependency = new Dependency(currClassName, qualifierClass, commitTime, commitId);
                    classDependencies.add(dependency);
                } else {
                    throw new RuntimeException("no class with given qualifier");
                }
            } else {
                String primaryBean = beanDetailsProvider.getPrimaryBean(parameter.getClass());
                if (primaryBean != null) {
                    Dependency dependency = new Dependency(currClassName, primaryBean, commitTime, commitId);
                    classDependencies.add(dependency);
                } else {
                    String parameterName = parameter.getType().getName();
                    Dependency dependency = new Dependency(currClassName, parameterName, commitTime, commitId);
                    classDependencies.add(dependency);
                }
            }
        }
    }

    private void resolveDependencies(List<Class<?>> beans, SpringBeanDetailsProvider beanDetailsProvider) throws Exception {
        for (Class<?> bean : beans) {
            String simpleName = bean.getSimpleName();
            String className = bean.getName();

            Field[] fields = bean.getDeclaredFields();
            Constructor<?>[] constructors = bean.getConstructors();

            List<Field> autowireField = new ArrayList<>();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    autowireField.add(field);
                }
            }

            List<Constructor<?>> autowireConstructor = new ArrayList<>();
            for (Constructor<?> constructor : constructors) {
                if (constructor.isAnnotationPresent(Autowired.class)) {
                    autowireConstructor.add(constructor);
                }
            }

            String classLocation= bean.getProtectionDomain().getCodeSource().getLocation().getPath();
            classLocation = classLocation.replaceAll("build/classes/java/main","src/main/java");

            String pathFromRoot = className.replace('.','/');
            String completeClassPath = classLocation + pathFromRoot;
            completeClassPath += ".java";

            String command = "git blame " + completeClassPath;
            Process proc = Runtime.getRuntime().exec(command, null, new File(classLocation));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                boolean isAutowired = false;

                int fieldCounter = 0;
                int constructorCounter = 0;
                String readLine;

                while ((readLine = reader.readLine()) != null) {

                    if (isAutowired) {

                        // ignore other annotations if any
                        while (readLine.contains("@")) {
                            readLine = reader.readLine();
                        }

                        // auto-wired constructor
                        // add prefix " " to avoid substring instrance of class name in another class
                        Pattern pattern = Pattern.compile("\\b" + simpleName + "\\b");
                        if (pattern.matcher(readLine).find()) {
                            Parameter[] parameters = autowireConstructor.get(constructorCounter).getParameters();
                            addDependencyConstructor(className, readLine, parameters, beanDetailsProvider);
                            constructorCounter++;
                        } else {
                            Field currField = autowireField.get(fieldCounter);
                            if (currField.isAnnotationPresent((Qualifier.class))) {
                                String qualifierName = currField.getAnnotation(Qualifier.class).value();
                                String qualifierClass = beanDetailsProvider.getClassName(qualifierName);
                                if (qualifierClass != null) {
                                    addDependencyField(className, readLine, qualifierClass);
                                } else {
                                    throw new RuntimeException("no class with given qualifier");
                                }
                            } else {
                                String primaryBean = beanDetailsProvider.getPrimaryBean(currField.getClass());
                                if (primaryBean != null) {
                                    addDependencyField(className, readLine, primaryBean);
                                } else {
                                    String fieldName = autowireField.get(fieldCounter).getType().getName();
                                    addDependencyField(className, readLine, fieldName);
                                }
                            }
                            fieldCounter++;
                        }
                    }
                    isAutowired = readLine.contains("@Autowired");
                }
            }
        }
    }

    public boolean findCycle(Map<String, Set<List<String>>> edges) {
        cycle = "";
        dependenciesInCycle=new ArrayList<>();

        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (String className : edges.keySet()) {
            if (isCyclicUtil(className, visited, recStack, edges)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCyclicUtil(String className, Set<String> visited, Set<String> recStack,
                                Map<String,Set<List<String>>> edges) {

        if (recStack.contains(className)) {
            cycle += "<- ";
            baseElement = className;
            return true;
        }
        if (visited.contains(className)) {
            return false;
        }
        recStack.add(className);
        visited.add(className);
        Set<List<String>> children = edges.get(className);
        if (children == null) {
            recStack.remove(className);
            return false;
        }
        for (List<String> child : children) {
            String childName= child.get(0);
            if (isCyclicUtil(childName, visited, recStack, edges)) {
                if (!baseElement.equals("")) {
                    cycle += childName;
                    cycle += " <- ";
                    dependenciesInCycle.add(new Dependency(className,childName,child.get(1),child.get(2)));
                } else if (child.equals(baseElement)) {
                    baseElement = "";
                }
                return true;
            }
        }
        recStack.remove(className);
        return false;
    }
}