package com.edugrade.model;

import com.edugrade.interfaces.Gradable;
import com.edugrade.interfaces.Reportable;
import java.util.ArrayList;

@SuppressWarnings("serial")
public class Student extends Person implements Gradable, Reportable {

    private String            branch;
    private String            section;
    private int               batch;
    private String            semester;
    private ArrayList<Subject> subjects;

    public Student(String rollNo, String name, String branch,
                   String section, int batch, String semester) {
        super(rollNo, name);
        this.branch   = branch;
        this.section  = section;
        this.batch    = batch;
        this.semester = semester;
        this.subjects = new ArrayList<>();
    }

    public String             getBranch()   { return branch; }
    public String             getSection()  { return section; }
    public int                getBatch()    { return batch; }
    public String             getSemester() { return semester; }
    public ArrayList<Subject> getSubjects() { return subjects; }
    public void addSubject(Subject s)       { subjects.add(s); }

    public int getTotalMarks() {
        int t = 0; for (Subject s : subjects) t += s.getMarks(); return t;
    }
    public int getTotalMaxMarks() {
        int t = 0; for (Subject s : subjects) t += s.getTotalMarks(); return t;
    }
    public int getPassedCount() {
        int c = 0; for (Subject s : subjects) if (s.isPassed()) c++; return c;
    }
    public boolean hasFailed() {
        for (Subject s : subjects) if (!s.isPassed()) return true; return false;
    }

    // ── Gradable ─────────────────────────────────────
    @Override
    public double getPercentage() {
        if (getTotalMaxMarks() == 0) return 0;
        return ((double) getTotalMarks() / getTotalMaxMarks()) * 100;
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
        if (subjects.isEmpty()) return 0;
        int t = 0; for (Subject s : subjects) t += s.getGradePoints();
        return t / subjects.size();
    }
    @Override
    public boolean isPassed() { return !hasFailed(); }

    public double getCGPA() {
        if (subjects.isEmpty()) return 0;
        int t = 0; for (Subject s : subjects) t += s.getGradePoints();
        return (double) t / subjects.size();
    }

    // ── Abstract method ───────────────────────────────
    @Override
    public String getInfo() {
        return getRollNo() + " | " + getName() + " | " + branch +
               " | Sec-" + section + " | " + batch + " Batch | Sem " + semester;
    }

    // ── Reportable: build grade report as HTML ────────
    @Override
    public String buildReportHTML() {
        StringBuilder sb = new StringBuilder();
        String gradeClass = gradeToClass(getGrade());

        sb.append("<div class='report-card'>")
          .append("<div class='rpt-header'>")
          .append("<div class='rpt-inst'>Academic Grade Report</div>")
          .append("<div class='rpt-title'>Semester ").append(semester).append("<br>Examination Result</div>")
          .append("<div class='rpt-meta'>")
          .append("<div class='rmi'><strong>").append(batch).append(" Batch</strong>Batch</div>")
          .append("<div class='rmi'><strong>").append(branch).append("</strong>Department</div>")
          .append("<div class='rmi'><strong>Section ").append(section).append("</strong>Section</div>")
          .append("</div></div>")

          .append("<div class='student-strip'>")
          .append("<div class='si'><div class='sil'>Roll Number</div><div class='rbadge'>").append(getRollNo()).append("</div></div>")
          .append("<div class='si'><div class='sil'>Student Name</div><div class='siv'>").append(getName()).append("</div></div>")
          .append("<div class='si'><div class='sil'>Semester</div><div class='siv'>Semester ").append(semester).append("</div></div>")
          .append("<div class='si'><div class='sil'>Section</div><div class='siv'>").append(section).append("</div></div>")
          .append("</div>")

          .append("<div class='grade-summary'>")
          .append("<div class='sb'><div class='sl'>Total Marks</div><div class='sv'>").append(getTotalMarks()).append("</div><div class='ss'>out of ").append(getTotalMaxMarks()).append("</div></div>")
          .append("<div class='sb'><div class='sl'>Percentage</div><div class='sv'>").append(String.format("%.1f", getPercentage())).append("%</div><div class='ss'>overall</div></div>")
          .append("<div class='sb'><div class='sl'>CGPA</div><div class='sv'>").append(String.format("%.2f", getCGPA())).append("</div><div class='ss'>out of 10.00</div></div>")
          .append("<div class='sb hl'><div class='sl'>Overall Grade</div><div class='sv'>").append(getGrade()).append("</div><div class='ss'>").append(getPassedCount()).append("/").append(subjects.size()).append(" passed</div></div>")
          .append("</div>")

          .append("<div class='tbl-wrap'>")
          .append("<div class='tbl-head'>Subject-wise Performance</div>")
          .append("<table><thead><tr><th>Code</th><th>Subject Name</th><th>Marks Obtained</th><th>Percentage</th><th>Grade</th></tr></thead><tbody>");

        for (Subject s : subjects) {
            String code = (s.getCode() == null || s.getCode().isEmpty()) ? "—" : s.getCode();
            sb.append("<tr>")
              .append("<td>").append(code.equals("—") ? "<span style='color:var(--g400)'>—</span>" : "<span class='cbadge'>" + code + "</span>").append("</td>")
              .append("<td><strong>").append(s.getName()).append("</strong></td>")
              .append("<td><div class='bw'><div class='bb'><div class='bf' style='width:").append(String.format("%.0f", s.getPercentage())).append("%'></div></div>")
              .append("<span class='bn'>").append(s.getMarks()).append("</span><span class='bt'>/").append(s.getTotalMarks()).append("</span></div></td>")
              .append("<td>").append(String.format("%.1f", s.getPercentage())).append("%</td>")
              .append("<td><span class='gp ").append(gradeToClass(s.getGrade())).append("'>").append(s.getGrade()).append("</span></td>")
              .append("</tr>");
        }

        sb.append("</tbody></table></div>")
          .append("<div class='rpt-footer'><p>Generated by <strong>EduGrade Pro</strong></p>")
          .append("<div class='ft-btns'>")
          .append("<button class='bsm bedit' onclick=\"editReport('").append(getRollNo()).append("','").append(semester).append("')\">✏️ Edit</button>")
          .append("<button class='bsm bprint' onclick='window.print()'>🖨️ Print</button>")
          .append("</div></div>")
          .append("</div>");

        return sb.toString();
    }

    // ── Reportable: build table row ────────────────────
    @Override
    public String buildSummaryRow(int i) {
        return "<tr>" +
            "<td style='color:rgba(255,255,255,.28);font-size:10px;'>" + i + "</td>" +
            "<td><span class='rtag'>" + getRollNo() + "</span></td>" +
            "<td class='nmc'>" + getName() + "</td>" +
            "<td>" + batch + " Batch</td>" +
            "<td>" + branch + "</td>" +
            "<td>" + section + "</td>" +
            "<td>Sem " + semester + "</td>" +
            "<td style='font-family:monospace;font-size:11px;'>" + getTotalMarks() + "/" + getTotalMaxMarks() + "</td>" +
            "<td style='font-weight:600;color:" + (getPercentage() >= 50 ? "#4ade80" : "#f87171") + ";'>" + String.format("%.1f", getPercentage()) + "%</td>" +
            "<td><span class='gp " + gradeToClass(getGrade()) + "'>" + getGrade() + "</span></td>" +
            "<td><div class='abs'>" +
            "<button class='ab av' onclick=\"viewReport('" + getRollNo() + "','" + semester + "')\">👁 View</button>" +
            "<button class='ab ae' onclick=\"editReport('" + getRollNo() + "','" + semester + "')\">✏️ Edit</button>" +
            "<button class='ab ad' onclick=\"deleteReport('" + getRollNo() + "','" + semester + "')\">🗑 Del</button>" +
            "</div></td>" +
            "</tr>";
    }

    private String gradeToClass(String g) {
        switch (g) {
            case "O": return "gO";
            case "A+": return "gAp";
            case "A": return "gA";
            case "B+": return "gBp";
            case "B": return "gB";
            case "C": return "gC";
            default: return "gF";
        }
    }
}
