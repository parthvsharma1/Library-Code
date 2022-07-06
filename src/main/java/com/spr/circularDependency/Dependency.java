package com.spr.circularDependency;

public class Dependency {

    private final String fromBean;
    private final String toBean;
    private final String dateTimestamp;
    private final String commitHash;

    public Dependency(String fromBean, String toBean, String dateTimestamp, String commitHash) {
        this.fromBean = fromBean;
        this.toBean = toBean;
        this.dateTimestamp = dateTimestamp;
        this.commitHash = commitHash;
    }

    public String getFromBean() {
        return fromBean;
    }

    public String getToBean() {
        return toBean;
    }

    public String getDateTimestamp() {
        return dateTimestamp;
    }

    public String getCommitHash() {
        return commitHash;
    }

    @Override
    public String toString() {
        return fromBean + " -> " + toBean + " at " + dateTimestamp + " from " + commitHash;
    }
}
