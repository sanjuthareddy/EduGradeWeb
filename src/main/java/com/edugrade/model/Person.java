package com.edugrade.model;

public abstract class Person {
    private String name;
    private String rollNo;

    public Person(String rollNo, String name) {
        this.rollNo = rollNo;
        this.name   = name;
    }

    public String getName()   { return name; }
    public String getRollNo() { return rollNo; }
    public void setName(String name)     { this.name = name; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public abstract String getInfo();
}
