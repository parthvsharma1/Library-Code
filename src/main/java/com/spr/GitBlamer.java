package com.spr;

import java.io.*;
import java.util.*;

class Dependency{
    public Dependency(String from,String to,String time,String commitId)
    {
        this.from=from;
        this.to=to;
        this.time=time;
        this.commitId=commitId;
    }
    public String from;
    public String to;
    public String time;
    public String commitId;

    @Override
    public String toString(){
        String a=from+" -> "+to+" at "+time+" from "+commitId;
        return a;
    }
}

public class GitBlamer {

    public static ArrayList<Dependency> classDependencies;
    private static String loop="";
    private static String baseElement="";

    private static void addDependencyConstructor(String currClassName, String line){
        String[] splited = line.split("\\s+");
        String commitId=splited[0];
        String commitTime=splited[2]+splited[3];

        int n= splited.length;
        ArrayList<String> allClasses=new ArrayList<>();

        for(int i=6;i<n;i++){ // before 6th idx we have commit info

            if(splited[i]=="("||splited[i].equals(currClassName+"("))
            {
                int j=i+1;
                while(j<n){
                    allClasses.add(splited[j]);

                    // move until finding ,
                    while (j<n && splited[j]!=",")
                        j++;

                    j++;
                }

                break;
            }
        }

        for(String toClassName:allClasses){
            Dependency d=new Dependency(currClassName,toClassName,commitTime,commitId);
            classDependencies.add(d);
        }

    }

    private static void addDependencyField(String currClassName, String line) {
        String[] splited = line.split("\\s+");
        String commitId=splited[0];
        String commitTime=splited[2]+splited[3];
        String toClassName;
        if(splited[6]=="public" || splited[6]=="private"|| splited[6]=="protected")
         toClassName=splited[7];
        else
            toClassName=splited[6];

        Dependency d=new Dependency(currClassName,toClassName,commitTime,commitId);
                classDependencies.add(d);

    }

    public static void main(String[] ar) throws IOException {
         classDependencies=new ArrayList<>();

         // sample path and name of a class for testing.........
        File directoryPath = new File("/Users/parth.sharma/Documents/CircularDependencylts/src/main/java/org/parth");
        FilenameFilter textFilefilter = new FilenameFilter(){
            public boolean accept(File dir, String name) {
                String lowercaseName = name.toLowerCase();
                if (lowercaseName.endsWith(".java")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        //List of all the text files
        File filesList[] = directoryPath.listFiles(textFilefilter);

        for(File file : filesList) {
            String path = file.getAbsolutePath();
            String currClassName = file.getName();
            currClassName = currClassName.substring(0, currClassName.length() - 5);

            long now = System.currentTimeMillis();
            String command = "git blame " + path;
            Process proc = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            System.out.println("Time for git blame : " + (System.currentTimeMillis() - now)+" ms");
            String line = "";
            String prev = "-1";// when we find any class, we have to check if there is @component annotation before it
            boolean isAutowired = false;

            while ((line = reader.readLine()) != null) {

                if (isAutowired) {
                    // check if constructor or parameter
                    if (line.contains(" " + currClassName + "(") || line.contains(" " + currClassName + " (")) // add prefix " " tp avoid substring instrance of class name in another class
                        addDependencyConstructor(currClassName, line);

                    else
                        addDependencyField(currClassName, line);
                }

                if (line.contains("class " + currClassName)) {
//                System.out.println(line);
                    if (!(prev.contains("@Component") || prev.contains("@Service") || prev.contains("Repository")))
                        break;

                }

                if (line.contains("@Autowired"))
                    isAutowired = true;
                else
                    isAutowired = false;


                prev = line;
            }
        }



        // sort deps based on time
//        classDependencies.add(new Dependency("B","A","2022-05-2902:13:19","chichi"));

        Collections.sort(classDependencies, new Comparator<Dependency>() {
            @Override
            public int compare(Dependency o1, Dependency o2) {
                return o1.time.compareTo(o2.time);
            }
        });


        System.out.println(classDependencies.size());
        for(int i=0;i<classDependencies.size();i++){
            System.out.println(classDependencies.get(i));
        }

        // make graph

         Map<String, Set<String>> graph=new HashMap<>() ;
        for(int i=0;i< classDependencies.size();i++)
        {

            //adding edge
            Set<String> currnbrs=graph.get(classDependencies.get(i).from);
            if(currnbrs!=null)
            {
                currnbrs.add(classDependencies.get(i).to);
                graph.put(classDependencies.get(i).from, currnbrs);

            }
            else {
                Set<String> currnbr = new HashSet<>();
                currnbr.add(classDependencies.get(i).to);
                graph.put(classDependencies.get(i).from,currnbr);

            }

            //checking if this edge lead to cycle formation
            if(findCycle(graph))
            {
                System.out.println("found cycle due to commit "+classDependencies.get(i).commitId);
                System.out.println("the faulty dependency is "
                                +classDependencies.get(i).from+" to "+classDependencies.get(i).to);

                return;
            }
        }
    System.out.println("No cycle found !!!!!!!");

    }

    public static boolean findCycle(Map<String,Set<String>> ed) {
        loop="";
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String s : ed.keySet())
        {
            if (isCyclicUtil(s, visited, recStack,ed))
            {
                System.out.println("cycle is "+loop);
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

