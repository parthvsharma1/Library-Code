package com.spr;

public class Dependency {
    public Dependency(String from, String to, String time, String commitId) {
        this.from = from;
        this.to = to;
        this.time = time;
        this.commitId = commitId;
    }

    public String from;
    public String to;
    public String time;
    public String commitId;

    @Override
    public String toString() {
        return from + " -> " + to + " at " + time + " from " + commitId;
    }
}
