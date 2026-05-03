package com.edugrade.service;

import com.edugrade.model.Student;
import com.edugrade.model.Subject;
import com.edugrade.utils.ValidationException;

import java.util.ArrayList;

public class GradeService {

    public static final String[] BRANCHES = {
        "CSE", "CSE (AI & ML)", "CSE (Data Science)", "CSE (Cyber Security)",
        "IT", "ECE", "EEE", "ME", "CE", "BioTech", "MCA", "BCA", "Other"
    };
    public static final String[] SEMESTERS = {
        "I","II","III","IV","V","VI","VII","VIII"
    };

    // ── Find by roll ──────────────────────────────────
    public Student findByRoll(ArrayList<Student> list, String rollNo) {
        for (Student s : list)
            if (s.getRollNo().equalsIgnoreCase(rollNo)) return s;
        return null;
    }

    // ── Find exact roll + semester ────────────────────
    public Student findByRollAndSem(ArrayList<Student> list, String rollNo, String sem) {
        for (Student s : list)
            if (s.getRollNo().equalsIgnoreCase(rollNo) && s.getSemester().equals(sem)) return s;
        return null;
    }

    // ── ADD ───────────────────────────────────────────
    public void addStudent(ArrayList<Student> list, Student student)
            throws ValidationException {

        String roll = student.getRollNo();
        String sem  = student.getSemester();

        // Same roll + same semester = duplicate
        if (findByRollAndSem(list, roll, sem) != null)
            throw new ValidationException(
                "Report for Roll No " + roll + " in Semester " + sem + " already exists!");

        // Validate locked fields against existing roll
        Student ex = findByRoll(list, roll);
        if (ex != null) {
            if (!ex.getName().equalsIgnoreCase(student.getName()))
                throw new ValidationException("Roll No " + roll + " is registered under '" + ex.getName() + "'.");
            if (!ex.getBranch().equals(student.getBranch()))
                throw new ValidationException("Roll No " + roll + " belongs to branch '" + ex.getBranch() + "'.");
            if (!ex.getSection().equalsIgnoreCase(student.getSection()))
                throw new ValidationException("Roll No " + roll + " belongs to Section '" + ex.getSection() + "'.");
            if (ex.getBatch() != student.getBatch())
                throw new ValidationException("Roll No " + roll + " belongs to " + ex.getBatch() + " Batch.");
        }

        // Cross-semester subject duplicate check
        for (Student s : list) {
            if (!s.getRollNo().equalsIgnoreCase(roll)) continue;
            for (Subject newSub : student.getSubjects()) {
                for (Subject oldSub : s.getSubjects()) {
                    if (oldSub.getName().equalsIgnoreCase(newSub.getName()))
                        throw new ValidationException(
                            "Subject '" + newSub.getName() + "' already exists in Semester " + s.getSemester() + " for this student.");
                }
            }
        }

        list.add(student);
    }

    // ── DELETE ────────────────────────────────────────
    public boolean deleteStudent(ArrayList<Student> list, String rollNo, String sem) {
        return list.removeIf(s -> s.getRollNo().equalsIgnoreCase(rollNo) && s.getSemester().equals(sem));
    }

    // ── STATS ─────────────────────────────────────────
    public String buildStatsHTML(ArrayList<Student> list) {
        if (list.isEmpty()) return "<p style='color:rgba(255,255,255,.4);'>No reports yet.</p>";

        double total = 0;
        int allPass = 0, hasFail = 0;
        Student top = list.get(0);
        for (Student s : list) {
            total += s.getPercentage();
            if (s.hasFailed()) hasFail++; else allPass++;
            if (s.getPercentage() > top.getPercentage()) top = s;
        }

        return "<div class='stats-strip'>" +
            stat(String.valueOf(list.size()), "Total Reports", false) +
            stat(String.format("%.1f%%", total / list.size()), "Avg Percentage", true) +
            stat(String.valueOf(allPass), "All Pass", false) +
            stat(String.valueOf(hasFail), "Has Fail", false) +
            "</div>" +
            "<div style='margin-top:14px;background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);border-radius:10px;padding:16px 20px;color:rgba(255,255,255,.7);font-size:13px;'>" +
            "🏆 Top Scorer: <strong style='color:var(--gold-light)'>" + top.getName() +
            "</strong> — " + String.format("%.1f", top.getPercentage()) + "% (" + top.getGrade() + ")" +
            "</div>";
    }

    private String stat(String val, String label, boolean gold) {
        return "<div class='stb" + (gold ? " gld" : "") + "'>" +
               "<div class='sv'>" + val + "</div>" +
               "<div class='sl'>" + label + "</div></div>";
    }
}
