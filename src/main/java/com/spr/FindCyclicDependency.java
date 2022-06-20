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

import static com.spr.XMLtoClasses.*;

public class FindCyclicDependency {

    static String[] configs =new String[] {"ApplicationContext.xml"};
    public static Map<String,Set<String>> edges;
    public static Map<String,Set<String>> edges2;
    private static String loop="";
    private static String baseElement="";

    public static void main(String[] args) throws Exception {

//        if(1>0)
//        throw new Exception();

//        Commit currCommit= RunShellCommandFromJava.getCurrCommit();

//        try {

//            ArrayList<Commit> commitHistory = RunShellCommandFromJava.getGitCommits();
//            String firstFaultyCommit = "";
//            String firstGoodCommit = "";
//
//            for (Commit commit : commitHistory) {
//                String command = "git checkout -f " + commit.getId();
//                Process proc = Runtime.getRuntime().exec(command);
//                proc.waitFor();

                System.out.println("------------------------------------------------------------------");



                XMLtoClasses xmLtoClasses = new XMLtoClasses();
                ArrayList<String> allClasses = xmLtoClasses.getAllClassesfromXml(configs);  // get classes from XMLS

//                if(1>0)
//                    return;

                ArrayList<String> beans = new ArrayList<>();
                getusefulClasses(allClasses, beans);// with annotaion of : @component


                edges = new HashMap<>();

                makeDirectedGraph(beans, "addEdge");


                printGraph(edges);
                boolean iscycle = findCycle(edges);

                if(iscycle)
                    System.out.println("************************* THE GRAPH HAS A CYCLE : TRUE ****************************");
                else {
                    System.out.println("************************* THE GRAPH HAS A CYCLE : FALSE ***************************");
                    throw new Exception();
                }



//            System.out.println("first good commit : " + firstGoodCommit + "\n" + "first faulty commit " + firstFaultyCommit);
//            getFaultyEdges(firstGoodCommit, firstFaultyCommit);

//            return;
//        }
//

        return;

    }

    private static void makeDirectedGraph(ArrayList<String> beans,String addMethod) throws ClassNotFoundException {

        for(String className:beans){
//            System.out.println("\n"+className+" has dependencies on :");

            Class<?> thisClass = Class.forName(className);// should not have a prefix "class"

            try {

                Field[] fields=thisClass.getDeclaredFields();       // declared se private bhi aa jaaenge but inherited nahi aenge
                Constructor[] constructors= thisClass.getConstructors();


                // find auto wires in fields (works with reqd=false as well)
                for(int i=0;i<fields.length;i++){
                    if(fields[i].isAnnotationPresent(Autowired.class)!=false)
                    {

                            if(fields[i].isAnnotationPresent((Qualifier.class))!=false)
                            {
                                String qualifierName=fields[i].getAnnotation(Qualifier.class).value();
//                                System.out.println("qualifier value is : "+ qualifierName);

                                // find class of this qualifier
                                String qualifierClass= idToClassMap.get(qualifierName);
                                if(qualifierClass!=null)
                                    addEdgeByThis(addMethod,className,qualifierClass);

                            }

                            // find primary implementation if any
                        boolean foundPrimary=false;
                        for(Class<?> primary:primaryClasses){
                            if(fields[i].getType().isAssignableFrom(primary)){
                                addEdgeByThis(addMethod,className,primary.getName());
                                foundPrimary=true;
                                break;
                            }
                        }

                        // not an interface
                        if(!foundPrimary)
                        {
                            if(fields[i].getType().isInterface())
                                throw new RuntimeException("no implementation of interface ");
                            String newnbr = fields[i].getType().getName();
//                        String newnbr2=fields[i].getName();
                           addEdgeByThis(addMethod,className,newnbr);
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

                                if (parameters[j].isAnnotationPresent(Qualifier.class) != false)
                                {
                                    String qualifierName = parameters[j].getAnnotation(Qualifier.class).value();

//                                    System.out.println("qualifier value is : " + qualifierName);

                                    // isss qualifier ki class pata karo
                                    String qualifierClass = idToClassMap.get(qualifierName);
                                    if(qualifierClass!=null)
                                        addEdgeByThis(addMethod,className,qualifierClass);


                                }

                                boolean foundPrimary=false;
                                for(Class<?> primary:primaryClasses){
                                    if(parameters[j].getType().isAssignableFrom(primary)){
                                        addEdgeByThis(addMethod,className,primary.getName());
                                        foundPrimary=true;
                                        break;
                                    }
                                }


                            if(!foundPrimary)
                            {
                                if(parameters[j].getType().isInterface())
                                    throw new RuntimeException("No implementation for this interface");

                                String newnbr = parameters[i].getType().getName();
                                //                                System.out.println(newnbr);
                                addEdgeByThis(addMethod,className,newnbr);

                            }

                        }

                    }


                }
            }

            catch (Throwable e) {
                System.err.println(e);
            }


        }

    }



    private static void  getFaultyEdges(String firstGoodCommit,String firstFaultyCommit ) throws Exception {
        // make old graph
        String command="git checkout "+firstGoodCommit;
//        runCmd(command);
        Process proc = Runtime.getRuntime().exec(command);
        proc.waitFor();



        // get classes from bean-id pairs and the packages to be scanned
        XMLtoClasses xmLtoClasses=new XMLtoClasses();
        ArrayList<String> allClasses=xmLtoClasses.getAllClassesfromXml(configs);


        ArrayList<String> beans = new ArrayList<>();
        getusefulClasses(allClasses, beans);// with @component annotation

        edges = new HashMap<>();
        makeDirectedGraph(beans,"addEdge");
        System.out.println("\n In good commit graph : ");
        printGraph(edges);
        System.out.println("graph as cycle -> "+findCycle(edges));




        // make new graph:
            // iteration 1: only add if already there
            // iteration 2 : add if no cycle gets formed
        edges2=new HashMap<>();
         command="git checkout "+firstFaultyCommit;
//         runCmd(command);
        Process proc2 = Runtime.getRuntime().exec(command);
        proc2.waitFor();



        // get classes from bean-id pairs and the packages to be scanned
        allClasses=xmLtoClasses.getAllClassesfromXml(configs);


         beans = new ArrayList<>();
        getusefulClasses(allClasses, beans);// with @component annotation


       /*  ab dhyaan se graph banao
            iteration 1 */
        makeDirectedGraph(beans,"addCommonEdge");


//        for(String className:beans){
////            System.out.println("\n"+className);
//
//            Class<?> thisClass = Class.forName(className);    // should not have a prefix "class"
//
//
//            try {
//
//                Field[] fields=thisClass.getDeclaredFields();       // declared se private bhi aa jaaenge but inherited nahi aenge
//                Constructor[] constructors= thisClass.getConstructors();
//
//
//                // find auto wires in fields (works with reqd=false as well)
//                for(int i=0;i<fields.length;i++){
//                    if(fields[i].isAnnotationPresent(Autowired.class)!=false)
//                    {
//                        String newnbr=fields[i].getType().toString();
//                        addCommonEdge(className,newnbr);
//                    }
//
//                }
//
//
////              finding autowires in constructors
//                for(int i=0;i< constructors.length;i++)
//                {
//                    if(constructors[i].isAnnotationPresent(Autowired.class)!=false)
//                    {
//                        List<Class> arguments= Arrays.stream(constructors[i].getParameterTypes()).collect(toList());
//                        for(int j=0;j<arguments.size();j++)
//                        {
//                            String newnbr=arguments.get(j).toString();
//                            addCommonEdge(className,newnbr);
//
//                        }
//
//                    }
//                }
//
//
//
//            } catch (Throwable e) {
//                System.err.println(e);
//            }
//
//
//        }
        System.out.println("in good commit grpahs are : ");
        printGraph(edges);
        printGraph(edges2);
        // idron tak sab vdia haiga


        /*iteration 2*/
        makeDirectedGraph(beans,"addNonLoopEdge");

//        for(String className:beans){
////            System.out.println("\n"+className);
//
//            Class<?> thisClass = Class.forName(className);    // should not have a prefix "class"
//
//
//            try {
//
//                Field[] fields=thisClass.getDeclaredFields();       // declared se private bhi aa jaaenge but inherited nahi aenge
//                Constructor[] constructors= thisClass.getConstructors();
//
//
//                // find auto wires in fields (works with reqd=false as well)
//                for(int i=0;i<fields.length;i++){
//                    if(fields[i].isAnnotationPresent(Autowired.class)!=false)
//                    {
//                        String newnbr=fields[i].getType().toString();
//                        addNonLoopEdge(className,newnbr);
//                    }
//
//                }
//
//
////              finding autowires in constructors
//                for(int i=0;i< constructors.length;i++)
//                {
//                    if(constructors[i].isAnnotationPresent(Autowired.class)!=false)
//                    {
//                        List<Class> arguments= Arrays.stream(constructors[i].getParameterTypes()).collect(toList());
//                        for(int j=0;j<arguments.size();j++)
//                        {
//                            String newnbr=arguments.get(j).toString();
//                            addNonLoopEdge(className,newnbr);
//
//                        }
//
//                    }
//                }
//
//
//
//            } catch (Throwable e) {
//                System.err.println(e);
//            }
//
//
//        }

        System.out.println("added edges which were not making loops :D");


//        runCmd("git checkout mybranch");
        Process proc3 = Runtime.getRuntime().exec("git checkout mybranch");
        proc3.waitFor();

        return;
    }

    public static void addEdgeByThis(String methodName,String className,String newnbr) throws Exception {
        FindCyclicDependency obj=new FindCyclicDependency();
        Class<?>[] paramTypes = {String.class, String.class};
        Class<?> classObj = obj.getClass();

        Method wayToAddEdge = classObj.getDeclaredMethod(methodName, paramTypes);
        wayToAddEdge.invoke(obj, className,newnbr);

    }

    private static void addNonLoopEdge(String className,String newnbr)
    {
        // check if already exists
        String updateClassName=className;
        Set<String> currnbrs=edges2.get(updateClassName);
        if(currnbrs!=null && currnbrs.contains(newnbr)==true)
            return;

        //add
        if(currnbrs==null)
            currnbrs=new HashSet<>();

        currnbrs.add(newnbr);
        edges2.put(updateClassName,currnbrs);

        //remove
        if(findCycle(edges2)==true)
        {
            System.out.println(className+" -> "+newnbr+"  .... faulty dependency ");
            currnbrs.remove(newnbr);
            edges2.put(updateClassName,currnbrs);
        }

        return;
    }
    private static void addCommonEdge(String className,String newnbr) // add edge only if it is present in the older graph
    {
        String updateClassName=className;
        Set<String> currnbrs=edges.get(updateClassName);

        Set<String> st=edges.get(updateClassName);
        boolean isPresentInOld=false;
        if(st!=null && st.contains(newnbr)==true)
            isPresentInOld=true;

        if(isPresentInOld)
        {
            if(currnbrs != null)
            {
                currnbrs.add(newnbr);
                edges2.put(updateClassName, currnbrs);

            }
            else
            {
                Set<String> currnbr = new HashSet<>();
                currnbr.add(newnbr);
                edges2.put(updateClassName, currnbr);

            }

        }


        return;
    }

    private static void addEdge(String className,String newnbr)
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

    public static boolean findCycle(Map<String,Set<String>> ed) {
        loop="";
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String s : ed.keySet())
        {
            if (isCyclicUtil(s, visited, recStack,ed))
            {
                System.out.println("CYCLE IS  "+loop);
                return true;
            }

        }
        return false;
    }



    private static void printGraph(Map<String,Set<String>> edgess)
    {
        System.out.println("graph looks like : ");
        System.out.println(Arrays.deepToString(edgess.entrySet().toArray()));
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

    public static void getusefulClasses(ArrayList<String> allClasses,ArrayList<String> beans) throws ClassNotFoundException {

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

        return;
    }

}

// qualifier
// beans/bean

// commit