package com.edugrade.service;

import com.edugrade.model.Student;
import com.edugrade.model.Subject;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;

// FILE-BASED STORAGE: Saves all student data to a JSON file
// Data persists across server restarts and deployments
public class StorageService {

    // Save file location — works both locally and on Railway/Render
    private static final String FILE_NAME = "edugrade_data.json";

    private static String getFilePath() {
        // On deployment servers, use /tmp which is always writable
        String dataDir = System.getenv("DATA_DIR");
        if (dataDir != null && !dataDir.isEmpty()) {
            return dataDir + File.separator + FILE_NAME;
        }
        // Locally: save in current working directory
        return FILE_NAME;
    }

    // ── SAVE all students to file ─────────────────────
    public static void save(ArrayList<Student> students) {
        try {
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < students.size(); i++) {
                json.append(toJson(students.get(i)));
                if (i < students.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]");
            Files.writeString(Paths.get(getFilePath()), json.toString());
        } catch (IOException e) {
            System.err.println("WARNING: Could not save data: " + e.getMessage());
        }
    }

    // ── LOAD all students from file ───────────────────
    public static ArrayList<Student> load() {
        ArrayList<Student> list = new ArrayList<>();
        try {
            Path path = Paths.get(getFilePath());
            if (!Files.exists(path)) return list;

            String json = Files.readString(path).trim();
            if (json.equals("[]") || json.isEmpty()) return list;

            // Remove outer [ ]
            json = json.substring(1, json.length() - 1).trim();

            // Split by student objects
            // Each student is a {...} block
            int depth = 0;
            int start = -1;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        String block = json.substring(start, i + 1);
                        Student s = fromJson(block);
                        if (s != null) list.add(s);
                        start = -1;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("WARNING: Could not load data: " + e.getMessage());
        }
        return list;
    }

    // ── Convert Student to JSON string ────────────────
    private static String toJson(Student s) {
        StringBuilder sb = new StringBuilder();
        sb.append("  {\n");
        sb.append("    \"rollNo\": \"").append(escape(s.getRollNo())).append("\",\n");
        sb.append("    \"name\": \"").append(escape(s.getName())).append("\",\n");
        sb.append("    \"branch\": \"").append(escape(s.getBranch())).append("\",\n");
        sb.append("    \"section\": \"").append(escape(s.getSection())).append("\",\n");
        sb.append("    \"batch\": ").append(s.getBatch()).append(",\n");
        sb.append("    \"semester\": \"").append(escape(s.getSemester())).append("\",\n");
        sb.append("    \"subjects\": [\n");
        for (int i = 0; i < s.getSubjects().size(); i++) {
            Subject sub = s.getSubjects().get(i);
            sb.append("      {");
            sb.append("\"code\": \"").append(escape(sub.getCode())).append("\", ");
            sb.append("\"name\": \"").append(escape(sub.getName())).append("\", ");
            sb.append("\"marks\": ").append(sub.getMarks()).append(", ");
            sb.append("\"totalMarks\": ").append(sub.getTotalMarks());
            sb.append("}");
            if (i < s.getSubjects().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ]\n");
        sb.append("  }");
        return sb.toString();
    }

    // ── Parse Student from JSON string ────────────────
    private static Student fromJson(String json) {
        try {
            String rollNo   = extractString(json, "rollNo");
            String name     = extractString(json, "name");
            String branch   = extractString(json, "branch");
            String section  = extractString(json, "section");
            int    batch    = extractInt(json, "batch");
            String semester = extractString(json, "semester");

            Student student = new Student(rollNo, name, branch, section, batch, semester);

            // Parse subjects array
            int subStart = json.indexOf("\"subjects\"");
            if (subStart != -1) {
                int arrStart = json.indexOf("[", subStart);
                int arrEnd   = json.lastIndexOf("]");
                if (arrStart != -1 && arrEnd != -1) {
                    String subArr = json.substring(arrStart + 1, arrEnd);
                    int d = 0, s2 = -1;
                    for (int i = 0; i < subArr.length(); i++) {
                        char c = subArr.charAt(i);
                        if (c == '{') { if (d == 0) s2 = i; d++; }
                        else if (c == '}') {
                            d--;
                            if (d == 0 && s2 != -1) {
                                String sb = subArr.substring(s2, i + 1);
                                String code  = extractString(sb, "code");
                                String sname = extractString(sb, "name");
                                int marks    = extractInt(sb, "marks");
                                int total    = extractInt(sb, "totalMarks");
                                student.addSubject(new Subject(code, sname, marks, total));
                                s2 = -1;
                            }
                        }
                    }
                }
            }
            return student;
        } catch (Exception e) {
            System.err.println("WARNING: Could not parse student: " + e.getMessage());
            return null;
        }
    }

    // ── JSON helpers ──────────────────────────────────
    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        int colon = json.indexOf(":", idx);
        int q1    = json.indexOf("\"", colon);
        int q2    = json.indexOf("\"", q1 + 1);
        // Handle escaped quotes
        while (q2 > 0 && json.charAt(q2 - 1) == '\\') q2 = json.indexOf("\"", q2 + 1);
        return json.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static int extractInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return 0;
        int colon = json.indexOf(":", idx);
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try { return Integer.parseInt(json.substring(start, end).trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
