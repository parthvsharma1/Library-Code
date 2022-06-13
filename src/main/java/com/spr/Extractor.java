package com.spr;

public class Extractor {
    public static String extractid(String s)
    {
        int n=s.length();
        String id="";
        for(int i=7;i<n;i++)
        {
            id+=s.charAt(i);
        }

        return id;
    }

    public static String extractAuthor(String s)
    {
        int n=s.length();
        String name="";
        for(int i=8;i<n;i++)
        {
            name+=s.charAt(i);
        }

        return name;
    }

    public static String extractDate(String s)
    {
        int n=s.length();
        String date="";
        for(int i=8;i<n;i++)
        {
            date+=s.charAt(i);
        }

        return date;
    }

}
