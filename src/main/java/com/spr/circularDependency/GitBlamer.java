package com.spr.circularDependency;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.*;
//import org.eclipse.jgit.api.BlameCommand;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.blame.BlameGenerator;
//import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Pattern;

public class GitBlamer {

    public static class CircularDependencyException extends RuntimeException {

        public CircularDependencyException() {
        }

        public CircularDependencyException(String message) {
            super(message);
        }
    }
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

        try {
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

                dependenciesInCycle.sort((o1, o2) -> o2.getDateTimestamp().compareTo(o1.getDateTimestamp()));
                System.out.println("faulty dependency is : " + dependenciesInCycle.get(0));
                throw new CircularDependencyException(dependenciesInCycle.get(0).toString());
            } else {
                System.out.println("No cycle found !!!!!!!");
            }
            return;
        }catch (CircularDependencyException circularDependencyException) {
           System.out.println(circularDependencyException);
           return;
        } catch (Exception eX) {

        }

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
        String rootLocation = beans.get(0).getProtectionDomain().getCodeSource().getLocation().getPath();
        rootLocation = rootLocation.replaceAll("/build/.*", "");
        String subDirectoryLocation = rootLocation;
        rootLocation = rootLocation.replaceAll("/[^/]*$", ""); // set the root location to parent folder
                                                                                // p.s. all beans are in same base folder

        Map<String,List<String>> beanPath = new HashMap<>();
        Process proc1 = Runtime.getRuntime().exec("find ./ -name *.java -type f",
                null, new File(rootLocation));

        try(BufferedReader reader1 = new BufferedReader(new InputStreamReader(proc1.getInputStream()))) {
            String pathFromRoot;
            while ((pathFromRoot = reader1.readLine()) != null) {
                pathFromRoot = pathFromRoot.replaceAll(".//", "/");
                String beanName = extractBeanName(pathFromRoot);

                if (beanPath.containsKey(beanName)) {
                    List<String> locationsForBean = beanPath.get(beanName);
                    locationsForBean.add(rootLocation + pathFromRoot);
                    beanPath.put(beanName, locationsForBean);
                } else {
                    List<String> locationsForBean = new ArrayList<>();
                    locationsForBean.add(rootLocation + pathFromRoot);
                    beanPath.put(beanName, locationsForBean);
                }
            }
        }catch (FileNotFoundException ex) {
            System.out.println(ex);
        }

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

            // TODO : use jgit for performing blame operation on a given class
//
//            Repository repository = new FileRepositoryBuilder().setGitDir(new File("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses/.git")).readEnvironment() // scan environment GIT_* variables
//                        .findGitDir() // scan up the file system tree
//                        .build();
//            BlameGenerator blameGenerator = new BlameGenerator(repository,"/src/main/java/com/spr/shapes/Square.java");
////
//            BlameResult blameResult = BlameResult.create(blameGenerator);




            List<Constructor<?>> autowireConstructor = new ArrayList<>();
            for (Constructor<?> constructor : constructors) {
                if (constructor.isAnnotationPresent(Autowired.class)) {
                    autowireConstructor.add(constructor);
                }
            }

           List<String> allPaths = beanPath.get(simpleName);
            if(allPaths.size()>1)
                throw new Exception("2 beans with same Name");

            String absolutePath = allPaths.get(0);

                String command = "git blame " + absolutePath;
                Process proc = Runtime.getRuntime().exec(command, null, new File(subDirectoryLocation));
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

    private boolean findCycle(Map<String, Set<List<String>>> edges) {
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

    private boolean isCyclicUtil(String className, Set<String> visited, Set<String> recStack,
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
            String childName = child.get(0);
            if (isCyclicUtil(childName, visited, recStack, edges)) {
                if (!baseElement.equals("")) {
                    cycle += childName;
                    cycle += " <- ";
                    dependenciesInCycle.add(new Dependency(className,childName,child.get(1),child.get(2)));
                } else if (childName.equals(baseElement)) {
                    baseElement = "";
                }
                return true;
            }
        }
        recStack.remove(className);
        return false;
    }

    private String extractBeanName(String pathFromRoot){
        String output = pathFromRoot.replaceAll("/.*/","").replaceAll(".java","");

        return output;

    }
}


//
//            try {
////                File repoDir = new File("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses/.git");
////
////                // now open the resulting repository with a FileRepositoryBuilder
////                FileRepositoryBuilder builder = new FileRepositoryBuilder();
////                Repository repository = builder.setGitDir(repoDir)
////                        .readEnvironment() // scan environment GIT_* variables
////                        .findGitDir() // scan up the file system tree
////                        .build();
////                boolean isRepo = repository.getObjectDatabase().exists();
////                    System.out.println("Having repository: " + repository.getDirectory());
////
////                    // the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
////                    Ref head = repository.exactRef("refs/heads/master");
////                    System.out.println("Ref of refs/heads/master: " + head);
////
////                BlameGenerator blameGenerator = new BlameGenerator(repository,"/src/main/java/com/spr/shapes/Square.java");
////
////                BlameResult blameResult = BlameResult.create(blameGenerator);
//
//
////                Git git = new Git(new FileRepository("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses.git"));
////                BlameCommand blameCommand = git.blame();
////                blameCommand.setStartCommit(git.getRepository().resolve("HEAD"));
////                blameCommand.setFilePath("src/main/java/com/spr/shapes/Square.java");
////                BlameResult blameResult = blameCommand.call();
//
//
//
//                Repository repository = new FileRepositoryBuilder().setGitDir
//                        (new File("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses")).build();
//
//                Repository repository1 = new FileRepositoryBuilder().setGitDir(new File("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses")).readEnvironment() // scan environment GIT_* variables
//                        .setGitDir(new File("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses")) // --git-dir if supplied, no-op if null
//                        .findGitDir() // scan up the file system tree
//                        .build();
////                FileRepository repository = new FileRepository(rootLocation);
////                FileRepository repository = new FileRepository("/Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses/src/main/java/com/spr/shapes");
////                pathFromRoot=absolutePath.replaceAll(rootLocation,"");
//                BlameGenerator blameGenerator = new BlameGenerator(repository,"/src/main/java/com/spr/shapes/Square.java");
//
//                BlameResult blameResult = BlameResult.create(blameGenerator);
////                              /Users/parth.sharma/Documents/Finding-Circular-DepsPPT/TestClasses/src/main/java/com/spr/shapes/Square.java
//
//            if(blameResult!=null) {
//                int size = blameResult.getResultContents().size();
//
//                for (int k = 0; k < size; k++) {
//                    System.out.println(blameResult.getSourceAuthor(k));
//                    System.out.println(blameResult.getSourceCommit(k));
//
//                }
//            }
//
//
//            }catch (Exception ex ){
//                System.out.println("err");
//            }
//
//                Git git=null;
