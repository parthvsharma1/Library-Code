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
        return from+" -> "+to+" at "+time+" from "+commitId;
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

            if(splited[i].equals("(")||splited[i].equals(currClassName+"("))
            {
                int j=i+1;
                while(j<n){
                    allClasses.add(splited[j]);

                    // move until finding ,
                    while (j<n && !splited[j].equals(","))
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
        if(splited[6].equals("public") || splited[6].equals("private")|| splited[6].equals("protected"))
            toClassName=splited[7];
        else
            toClassName=splited[6];

        Dependency d=new Dependency(currClassName,toClassName,commitTime,commitId);
        classDependencies.add(d);

    }

    public static boolean isJava(String fileName){
        return fileName.endsWith("java");
    }
    public static void getFnames(String sDir, ArrayList<File> filesList) {
        File[] faFiles = new File(sDir).listFiles();

        assert faFiles != null;
        for (File file : faFiles) {
            if (file.isDirectory()) {
                getFnames(file.getAbsolutePath(),filesList);
            }
            else if (isJava(file.getName())) {
//                System.out.println(file.getAbsolutePath());
                filesList.add(file);
            }
        }
    }
    public static void main(String[] args) throws IOException {


        classDependencies=new ArrayList<>();
        // sample path and name of a class for testing.........
        Scanner sc= new Scanner(System.in);    //System.in is a standard input stream
        System.out.print("Enter full directory path - ");
        String directoryPathName= sc.next();

        long now=System.currentTimeMillis();

        //List of all the text files
        ArrayList<File> filesList=new ArrayList<>();
        getFnames(directoryPathName,filesList);



        for(File file : filesList) {

            String path = file.getAbsolutePath();
            String currClassName = file.getName();
            currClassName = currClassName.substring(0, currClassName.length() - 5);

            String command = "git blame " + path;
            Process proc = Runtime.getRuntime().exec(command,null,new File(directoryPathName));
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            String line;
            String prev = "-1";// when we find any class definition, we have to check if there is @component annotation before it
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

                isAutowired = line.contains("@Autowired");


                prev = line;
            }
        }

        System.out.println("time after git blame :" +(System.currentTimeMillis()-now));

        // sort deps based on time
        Collections.sort(classDependencies, Comparator.comparing(o -> o.time));


//        System.out.println(classDependencies.size());
        for (Dependency classDependency : classDependencies) {
            System.out.println(classDependency);
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

