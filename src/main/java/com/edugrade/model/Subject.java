package com.edugrade.model;

import com.edugrade.interfaces.Gradable;

public class Subject implements Gradable {
    private String code;
    private String name;
    private int    marks;
    private int    totalMarks;

    public Subject(String code, String name, int marks, int totalMarks) {
        this.code       = code;
        this.name       = name;
        this.marks      = marks;
        this.totalMarks = totalMarks;
    }

    public String getCode()       { return code; }
    public String getName()       { return name; }
    public int    getMarks()      { return marks; }
    public int    getTotalMarks() { return totalMarks; }
    public void   setMarks(int m) { this.marks = m; }

    @Override
    public double getPercentage() {
        return ((double) marks / totalMarks) * 100;
    }

    @Override
    public String getGrade() {
        double p = getPercentage();
        if (p >= 90) return "O";
        if (p >= 80) return "A+";
        if (p >= 70) return "A";
        if (p >= 60) return "B+";
        if (p >= 50) return "B";
        if (p >= 40) return "C";
        return "F";
    }

    @Override
    public int getGradePoints() {
        double p = getPercentage();
        if (p >= 90) return 10;
        if (p >= 80) return 9;
        if (p >= 70) return 8;
        if (p >= 60) return 7;
        if (p >= 50) return 6;
        if (p >= 40) return 5;
        return 0;
    }

    @Override
    public boolean isPassed() { return !getGrade().equals("F"); }
}
