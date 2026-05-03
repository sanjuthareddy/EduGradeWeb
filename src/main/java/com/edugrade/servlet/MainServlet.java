package com.edugrade.servlet;

import com.edugrade.model.Student;
import com.edugrade.model.Subject;
import com.edugrade.service.GradeService;
import com.edugrade.service.StorageService;
import com.edugrade.utils.ValidationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;


public class MainServlet extends HttpServlet {

    private GradeService service = new GradeService();

    // App-level student list — shared across all sessions, persisted to file
    private static ArrayList<Student> studentList = null;

    @Override
    public void init() {
        // Load data from file when server starts
        studentList = StorageService.load();
        System.out.println("Loaded " + studentList.size() + " reports from storage.");
    }

    private ArrayList<Student> getStudents(HttpSession session) {
        if (studentList == null) studentList = StorageService.load();
        return studentList;
    }

    // ── GET: serve main page ──────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(true);
        ArrayList<Student> students = getStudents(session);

        String path = req.getServletPath();

        // API: get locked fields for a roll number
        if (path.startsWith("/api")) {
            String roll = req.getParameter("roll");
            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            if (roll != null && !roll.isEmpty()) {
                Student ex = service.findByRoll(students, roll.toUpperCase());
                if (ex != null) {
                    out.print("{\"found\":true,\"name\":\"" + ex.getName() + "\"," +
                              "\"branch\":\"" + ex.getBranch() + "\"," +
                              "\"section\":\"" + ex.getSection() + "\"," +
                              "\"batch\":" + ex.getBatch() + "}");
                } else {
                    out.print("{\"found\":false}");
                }
            } else {
                out.print("{\"found\":false}");
            }
            return;
        }

        // Serve main HTML page
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println(buildPage(students, null, null, null));
    }

    // ── POST: handle form submissions ─────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(true);
        ArrayList<Student> students = getStudents(session);

        String action = req.getParameter("action");
        String error  = null;
        String success = null;
        String activeTab = "form";

        if ("add".equals(action)) {
            try {
                String rollNo   = req.getParameter("rollNo").trim().toUpperCase();
                String name     = req.getParameter("name").trim();
                String branch   = req.getParameter("branch").trim();
                String section  = req.getParameter("section").trim().toUpperCase();
                String batchStr = req.getParameter("batch").trim();
                String semester = req.getParameter("semester").trim();

                if (rollNo.isEmpty()) throw new ValidationException("Roll Number is required.");
                if (name.isEmpty())   throw new ValidationException("Student Name is required.");
                if (branch.isEmpty()) throw new ValidationException("Branch is required.");
                if (section.isEmpty())throw new ValidationException("Section is required.");
                if (semester.isEmpty())throw new ValidationException("Semester is required.");
                int batch;
                try { batch = Integer.parseInt(batchStr); }
                catch (NumberFormatException e) { throw new ValidationException("Enter a valid Batch Year."); }

                // Build subjects from form arrays
                String[] subNames   = req.getParameterValues("subName[]");
                String[] subCodes   = req.getParameterValues("subCode[]");
                String[] subMarks   = req.getParameterValues("subMarks[]");
                String[] subTotals  = req.getParameterValues("subTotal[]");

                if (subNames == null || subNames.length == 0)
                    throw new ValidationException("At least one subject is required.");

                Student student = new Student(rollNo, name, branch, section, batch, semester);
                boolean anySubject = false;

                for (int i = 0; i < subNames.length; i++) {
                    String sname = subNames[i].trim();
                    if (sname.isEmpty()) continue; // skip blank rows

                    String code = (subCodes != null && i < subCodes.length) ? subCodes[i].trim().toUpperCase() : "";
                    String mStr = (subMarks  != null && i < subMarks.length)  ? subMarks[i].trim()  : "0";
                    String tStr = (subTotals != null && i < subTotals.length) ? subTotals[i].trim() : "100";

                    int marks, total;
                    try { marks = Integer.parseInt(mStr); } catch (NumberFormatException e) { throw new ValidationException("Invalid marks for subject: " + sname); }
                    try { total = Integer.parseInt(tStr); total = total <= 0 ? 100 : total; } catch (NumberFormatException e) { total = 100; }

                    if (marks < 0)     throw new ValidationException("Marks cannot be negative for: " + sname);
                    if (marks > total) throw new ValidationException("Marks exceed total for: " + sname + " (" + marks + " > " + total + ")");

                    student.addSubject(new Subject(code, sname, marks, total));
                    anySubject = true;
                }

                if (!anySubject) throw new ValidationException("At least one subject with a name is required.");

                service.addStudent(students, student);
                StorageService.save(students);
                success = "Report for " + name + " (Sem " + semester + ") saved successfully!";
                activeTab = "dash";

            } catch (ValidationException e) {
                error = e.getMessage();
                activeTab = "form";
            }

        } else if ("delete".equals(action)) {
            String roll = req.getParameter("rollNo");
            String sem  = req.getParameter("semester");
            service.deleteStudent(students, roll, sem);
            StorageService.save(students);
            success = "Report deleted.";
            activeTab = "dash";

        } else if ("edit".equals(action)) {
            // Update subject marks only
            String roll = req.getParameter("rollNo");
            String sem  = req.getParameter("semester");
            Student student = service.findByRollAndSem(students, roll, sem);
            if (student != null) {
                String[] marks = req.getParameterValues("marks[]");
                if (marks != null) {
                    for (int i = 0; i < student.getSubjects().size() && i < marks.length; i++) {
                        try {
                            int m = Integer.parseInt(marks[i].trim());
                            Subject sub = student.getSubjects().get(i);
                            if (m >= 0 && m <= sub.getTotalMarks()) sub.setMarks(m);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                StorageService.save(students);
                success = "Report updated for " + student.getName();
            }
            activeTab = "dash";
        }

        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println(buildPage(students, error, success, activeTab));
    }

    // ─────────────────────────────────────────────────
    //  BUILD FULL HTML PAGE
    // ─────────────────────────────────────────────────
    private String buildPage(ArrayList<Student> students, String error, String success, String activeTab) {
        String formActive = !"dash".equals(activeTab) ? "on" : "";
        String dashActive = "dash".equals(activeTab) ? "on" : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='en'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>EduGrade Pro</title>")
          .append("<link href='https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;600&display=swap' rel='stylesheet'>")
          .append("<style>").append(getCSS()).append("</style>")
          .append("</head><body>")
          .append("<div class='bg-pat'></div><div class='bg-grid'></div>")
          .append("<div class='pw'>")

          // HEADER
          .append("<header>")
          .append("<div class='logo'><div class='logo-ic'>🎓</div><div class='logo-tx'><strong>EduGrade Pro</strong><span>Academic Evaluation System</span></div></div>")
          .append("<div class='hnav'>")
          .append("<button class='nbtn ").append(formActive).append("' onclick=\"sw('form')\">📝 New Report</button>")
          .append("<button class='nbtn ").append(dashActive).append("' onclick=\"sw('dash')\">📊 All Reports <span class='rc'>").append(students.size()).append("</span></button>")
          .append("</div></header>")

          // FORM TAB
          .append("<div id='tForm' class='").append(formActive).append("'>")
          .append("<div class='mf'>")
          .append("<div class='form-panel'>")
          .append("<div class='card'>")
          .append("<div class='ch'><h2>📋 Student Details</h2><p>Fields marked <span class='req'>*</span> are required</p></div>")
          .append("<div class='cb'>")
          .append("<form method='post' action='/' id='mainForm'>")
          .append("<input type='hidden' name='action' value='add'>")

          // Error / success
          .append(error   != null ? "<div class='abox error show'>⚠️ " + error + "</div>" : "")
          .append(success != null ? "<div class='abox suc show'>✅ " + success + "</div>" : "")

          // Roll + Batch
          .append("<div class='fr'>")
          .append("<div class='fg'><label>Roll Number <span class='req'>*</span></label><input type='text' name='rollNo' id='fRoll' placeholder='e.g. 23CS001' oninput='onRollCheck()'></div>")
          .append("<div class='fg'><label>Batch <span class='req'>*</span></label><select name='batch' id='fBatch'>").append(buildBatchOptions()).append("</select><div class='lock-hint' id='batchHint'>🔒 Locked</div></div>")
          .append("</div>")
          .append("<div class='abox warn' id='rollWarn'></div>")

          // Name
          .append("<div class='fg'><label>Student Name <span class='req'>*</span></label><input type='text' name='name' id='fName' placeholder='e.g. Arjun Sharma'><div class='lock-hint' id='nameHint'>🔒 Locked</div></div>")

          // Dept + Section
          .append("<div class='fr'>")
          .append("<div class='fg'><label>Branch <span class='req'>*</span></label><select name='branch' id='fBranch'>").append(buildBranchOptions()).append("</select><div class='lock-hint' id='branchHint'>🔒 Locked</div></div>")
          .append("<div class='fg'><label>Section <span class='req'>*</span></label><input type='text' name='section' id='fSec' placeholder='A / B / C' maxlength='5'><div class='lock-hint' id='secHint'>🔒 Locked</div></div>")
          .append("</div>")

          // Semester
          .append("<div class='fg'><label>Semester <span class='req'>*</span></label><select name='semester'>").append(buildSemOptions()).append("</select></div>")

          .append("<div class='divider'></div>")

          // Subjects
          .append("<div class='sc-hdr'><label style='margin:0;'>Subjects &amp; Marks <span class='req'>*</span></label></div>")
          .append("<div class='sc-cols'>")
          .append("<span class='sc-lbl'>Code <span class='opt'>(opt)</span></span>")
          .append("<span class='sc-lbl'>Subject Name <span class='req'>*</span></span>")
          .append("<span class='sc-lbl'>Marks <span class='req'>*</span></span>")
          .append("<span class='sc-lbl'>Out Of <span class='req'>*</span></span>")
          .append("<span></span></div>")
          .append("<div class='slist' id='slist'></div>")
          .append("<button type='button' class='addb' onclick='addRow()'>➕ Add Subject</button>")

          .append("<div class='divider'></div>")
          .append("<div style='display:flex;gap:9px;'>")
          .append("<button type='submit' class='bprim' style='flex:1;'><span>✨ Generate &amp; Save Report</span></button>")
          .append("<button type='button' class='bnew' onclick='resetForm()'>🆕 New</button>")
          .append("</div>")
          .append("</form></div></div></div>")

          // Right: placeholder
          .append("<div>")
          .append("<div class='ph' id='phPanel'><div class='pi'>📄</div>")
          .append("<p>Fill in student details and marks,<br>then click <strong>Generate &amp; Save Report</strong>.<br><br>Switch to <strong>All Reports</strong> to view, edit, or print.</p></div>")
          .append("</div>")
          .append("</div></div>")

          // DASH TAB
          .append("<div id='tDash' class='").append(dashActive).append("'>")
          .append("<div class='dash'>")
          .append("<div class='dt'><div class='dt-title'>All Reports<span>").append(students.size()).append(" report").append(students.size() != 1 ? "s" : "").append(" saved</span></div>")
          .append("<div class='dc'><div class='sb2'><span class='si2'>🔍</span><input type='text' id='srch' placeholder='Name or Roll No…' oninput='filterTable()'></div>")
          .append("<select class='fsel' id='fSemF' onchange='filterTable()'><option value=''>All Semesters</option>").append(buildSemOptions()).append("</select>")
          .append("</div></div>")

          // Stats
          .append("<div class='ss2'>").append(service.buildStatsHTML(students)).append("</div>")

          // Table
          .append("<div class='dtw'><table class='dtb' id='mainTable'>")
          .append("<thead><tr><th>#</th><th>Roll No</th><th>Name</th><th>Batch</th><th>Branch</th><th>Sec</th><th>Sem</th><th>Marks</th><th>%</th><th>Grade</th><th>Actions</th></tr></thead>")
          .append("<tbody id='dtbody'>");

        if (students.isEmpty()) {
            sb.append("<tr><td colspan='11' style='text-align:center;padding:50px;color:rgba(255,255,255,.25);'>No reports yet. Add your first report!</td></tr>");
        } else {
            for (int i = 0; i < students.size(); i++) {
                sb.append(students.get(i).buildSummaryRow(i + 1));
            }
        }

        sb.append("</tbody></table></div>")
          .append("</div></div>")

          // Edit Modal
          .append("<div class='mov' id='editMod' onclick='closeModOv(event)'><div class='mod' id='editModContent'></div></div>")
          // View Modal
          .append("<div class='mov' id='viewMod' onclick='closeViewOv(event)'><div class='mod'>")
          .append("<div class='mhd'><h3>📄 Student Report</h3><button class='mcls' onclick='closeView()'>✕</button></div>")
          .append("<div class='mbd' id='viewBd'></div></div></div>")

          .append("<script>").append(getJS(students)).append("</script>")
          .append("</div></body></html>");

        return sb.toString();
    }

    // Build report data as JS array for client-side view
    private String getJS(ArrayList<Student> students) {
        StringBuilder sb = new StringBuilder();

        // Embed all report HTML into JS for client-side viewing
        sb.append("var reportData = {};");
        for (Student s : students) {
            String key = s.getRollNo() + "_" + s.getSemester();
            sb.append("reportData['").append(key).append("'] = `").append(s.buildReportHTML().replace("`", "'")).append("`;");
        }

        sb.append("""
            // TABS
            function sw(t) {
                document.getElementById('tForm').classList.toggle('on', t==='form');
                document.getElementById('tDash').classList.toggle('on', t==='dash');
                document.querySelectorAll('.nbtn').forEach((b,i)=>b.classList.toggle('on',i===(t==='form'?0:1)));
            }

            // SUBJECT ROWS
            function addRow(code,name,marks,total) {
                var list = document.getElementById('slist');
                var d = document.createElement('div');
                d.className = 'srow';
                d.innerHTML = `<input type='text' name='subCode[]' placeholder='CS301' value='${code||""}' maxlength='15'>
                    <input type='text' name='subName[]' placeholder='Subject Name' value='${name||""}'>
                    <input type='number' name='subMarks[]' placeholder='0' value='${marks||""}' min='0' max='9999' oninput='inlineChk(this)'>
                    <input type='number' name='subTotal[]' placeholder='100' value='${total||100}' min='1' max='9999'>
                    <button type='button' class='rmb' onclick='rmRow(this)'>✕</button>`;
                list.appendChild(d);
            }
            function rmRow(btn) {
                if(document.querySelectorAll('.srow').length<=1){alert('At least one subject is required!');return;}
                btn.parentElement.remove();
            }
            function inlineChk(inp) {
                var row=inp.closest('.srow');
                var tot=parseFloat(row.querySelector('[name="subTotal[]"]').value)||100;
                var m=parseFloat(inp.value);
                var bad=!isNaN(m)&&!isNaN(tot)&&tot>0&&m>tot;
                inp.classList.toggle('er',bad);
                row.classList.toggle('re',bad);
            }

            // ROLL CHECK — lock fields
            function onRollCheck() {
                var roll = document.getElementById('fRoll').value.trim().toUpperCase();
                if (!roll) { unlockAll(); return; }
                fetch('/api?roll=' + encodeURIComponent(roll))
                    .then(r=>r.json())
                    .then(data=>{
                        if (data.found) {
                            lockField('fName',   data.name,   'nameHint');
                            lockSelect('fBranch', data.branch, 'branchHint');
                            lockField('fSec',    data.section,'secHint');
                            lockSelect('fBatch', String(data.batch), 'batchHint');
                            var w=document.getElementById('rollWarn');
                            w.innerHTML='✅ Roll No <strong>'+roll+'</strong> → <strong>'+data.name+'</strong> | '+data.branch+' | Sec '+data.section+'. Fields locked.';
                            w.className='abox info show';
                        } else { unlockAll(); }
                    }).catch(()=>{});
            }
            function lockField(id, val, hintId) {
                var el=document.getElementById(id);
                el.value=val; el.readOnly=true; el.classList.add('locked');
                document.getElementById(hintId).classList.add('show');
            }
            function lockSelect(id, val, hintId) {
                var el=document.getElementById(id);
                el.value=val; el.disabled=true;
                el.style.background='var(--green-bg)'; el.style.color='var(--green)'; el.style.fontWeight='600';
                document.getElementById(hintId).classList.add('show');
            }
            function unlockAll() {
                ['fName','fSec'].forEach(id=>{
                    var el=document.getElementById(id);
                    el.readOnly=false; el.classList.remove('locked'); el.style.cssText='';
                });
                ['fBranch','fBatch'].forEach(id=>{
                    var el=document.getElementById(id);
                    el.disabled=false; el.style.cssText='';
                });
                ['nameHint','branchHint','secHint','batchHint'].forEach(id=>document.getElementById(id).classList.remove('show'));
                var w=document.getElementById('rollWarn'); w.className='abox warn'; w.innerHTML='';
            }

            // RESET FORM
            function resetForm() {
                document.getElementById('mainForm').reset();
                document.getElementById('slist').innerHTML='';
                addRow();
                unlockAll();
            }

            // VIEW REPORT
            function viewReport(roll, sem) {
                var key = roll+'_'+sem;
                document.getElementById('viewBd').innerHTML = reportData[key] || '<p>Not found</p>';
                document.getElementById('viewMod').classList.add('open');
                setTimeout(()=>{
                    document.querySelectorAll('#viewBd .bf').forEach(b=>{var w=b.style.width;b.style.width='0';requestAnimationFrame(()=>{b.style.width=w;});});
                },120);
            }
            function closeView() { document.getElementById('viewMod').classList.remove('open'); }
            function closeViewOv(e) { if(e.target.id==='viewMod') closeView(); }

            // EDIT REPORT (marks only)
            function editReport(roll, sem) {
                var key = roll+'_'+sem;
                var rpt = reportData[key];
                if (!rpt) return;
                // Build edit form in modal
                var html = '<div class=\\'mhd\\'><h3>✏️ Edit Marks — '+roll+' Sem '+sem+'</h3><button class=\\'mcls\\' onclick=\\'closeEdit()\\'>✕</button></div>';
                html += '<div class=\\'mbd\\'>';
                html += '<form method=\\'post\\' action=\\'/\\'>';
                html += '<input type=\\'hidden\\' name=\\'action\\' value=\\'edit\\'>';
                html += '<input type=\\'hidden\\' name=\\'rollNo\\' value=\\''+roll+'\\'>';
                html += '<input type=\\'hidden\\' name=\\'semester\\' value=\\''+sem+'\\'>';
                html += '<p style=\\'font-size:13px;color:var(--g600);margin-bottom:14px;\\'>Only marks can be edited. Roll, Name, Branch, Section are locked.</p>';
                // We build edit fields from the report — use a table approach
                // The actual marks will be submitted back
                var parser = new DOMParser();
                var doc = parser.parseFromString(rpt, 'text/html');
                var rows = doc.querySelectorAll('tbody tr');
                rows.forEach((row, i) => {
                    var cells = row.querySelectorAll('td');
                    if (cells.length < 5) return;
                    var subName = cells[1].innerText;
                    var marksCell = cells[2].innerText.trim();
                    var parts = marksCell.split('/');
                    var curMarks = parts[0].trim();
                    var total = parts[1] ? parts[1].trim() : '100';
                    html += '<div class=\\'fg\\'><label>'+subName+'</label>';
                    html += '<input type=\\'number\\' name=\\'marks[]\\'  value=\\''+curMarks+'\\' min=\\'0\\' max=\\''+total+'\\' style=\\'max-width:160px;\\'></div>';
                });
                html += '<div style=\\'margin-top:16px;display:flex;gap:8px;\\'>';
                html += '<button type=\\'submit\\' class=\\'bprim\\' style=\\'flex:1;\\'><span>💾 Update Report</span></button>';
                html += '<button type=\\'button\\' class=\\'bnew\\' onclick=\\'closeEdit()\\'>Cancel</button>';
                html += '</div></form></div>';
                document.getElementById('editModContent').innerHTML = html;
                document.getElementById('editMod').classList.add('open');
            }
            function closeEdit() { document.getElementById('editMod').classList.remove('open'); }
            function closeModOv(e) { if(e.target.id==='editMod') closeEdit(); }

            // DELETE
            function deleteReport(roll, sem) {
                if (!confirm('Delete report for Roll No '+roll+' Semester '+sem+'?')) return;
                var form = document.createElement('form');
                form.method='post'; form.action='/';
                form.innerHTML='<input name=\\'action\\' value=\\'delete\\'>'+
                    '<input name=\\'rollNo\\' value=\\''+roll+'\\'>'+
                    '<input name=\\'semester\\' value=\\''+sem+'\\'>';
                document.body.appendChild(form);
                form.submit();
            }

            // FILTER TABLE
            function filterTable() {
                var q = document.getElementById('srch').value.toLowerCase();
                var sem = document.getElementById('fSemF').value;
                document.querySelectorAll('#dtbody tr').forEach(tr => {
                    var txt = tr.innerText.toLowerCase();
                    var semCell = tr.cells[6] ? tr.cells[6].innerText : '';
                    var matchQ = !q || txt.includes(q);
                    var matchSem = !sem || semCell.includes(sem);
                    tr.style.display = (matchQ && matchSem) ? '' : 'none';
                });
            }

            // INIT
            addRow();
        """);

        return sb.toString();
    }

    // ─── HTML helpers ──────────────────────────────────
    private String buildBranchOptions() {
        StringBuilder sb = new StringBuilder("<option value=''>Select Branch</option>");
        for (String b : GradeService.BRANCHES)
            sb.append("<option value='").append(b).append("'>").append(b).append("</option>");
        return sb.toString();
    }

    private String buildSemOptions() {
        StringBuilder sb = new StringBuilder("<option value=''>Select Semester</option>");
        for (String s : GradeService.SEMESTERS)
            sb.append("<option value='").append(s).append("'>Semester ").append(s).append("</option>");
        return sb.toString();
    }

    private String buildBatchOptions() {
        StringBuilder sb = new StringBuilder("<option value=''>Select Batch</option>");
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        for (int y = year; y >= 2000; y--)
            sb.append("<option value='").append(y).append("'>").append(y).append(" Batch</option>");
        return sb.toString();
    }

    private String getCSS() {
        return """
            :root{
              --navy:#0a1628;--navy-mid:#0f2044;--navy-light:#1a3260;
              --gold:#d4a843;--gold-light:#f0c96a;--gold-pale:#fdf3d8;
              --cream:#faf8f3;--white:#fff;
              --g100:#f5f4f0;--g200:#e8e5dd;--g400:#9a9487;--g600:#5c574e;
              --red:#c0392b;--red-bg:#fde8e8;--red-bd:#f5b7b1;
              --green:#1a7a4a;--green-bg:#f0f9f4;--green-bd:#86efac;
              --warn:#b7770d;--warn-bg:#fef9e7;--warn-bd:#f9e79f;
              --shadow:0 20px 60px rgba(10,22,40,0.18);
              --ring:0 0 0 3px rgba(212,168,67,0.25);
            }
            *{margin:0;padding:0;box-sizing:border-box;}
            html{font-size:20px;zoom:1.25;}
            body{font-family:'DM Sans',sans-serif;background:var(--navy);min-height:100vh;color:var(--navy);overflow-x:hidden;font-size:16px;}
            .bg-pat{position:fixed;inset:0;z-index:0;background:radial-gradient(ellipse 80% 60% at 20% 10%,rgba(212,168,67,.08) 0%,transparent 60%),radial-gradient(ellipse 60% 80% at 80% 90%,rgba(37,99,235,.06) 0%,transparent 60%),var(--navy);}
            .bg-grid{position:fixed;inset:0;z-index:0;opacity:.04;background-image:linear-gradient(rgba(255,255,255,.6) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.6) 1px,transparent 1px);background-size:48px 48px;}
            .pw{position:relative;z-index:1;min-height:100vh;display:flex;flex-direction:column;}
            header{padding:22px 48px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid rgba(212,168,67,.15);}
            .logo{display:flex;align-items:center;gap:14px;}
            .logo-ic{width:48px;height:48px;background:linear-gradient(135deg,var(--gold),var(--gold-light));border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:24px;}
            .logo-tx{color:var(--white);}
            .logo-tx strong{font-family:'Playfair Display',serif;font-size:22px;display:block;line-height:1;}
            .logo-tx span{font-size:12px;letter-spacing:.1em;text-transform:uppercase;color:var(--gold);opacity:.75;}
            .hnav{display:flex;gap:8px;}
            .nbtn{padding:10px 20px;border-radius:9px;border:1.5px solid rgba(255,255,255,.1);background:transparent;color:rgba(255,255,255,.55);font-family:'DM Sans',sans-serif;font-size:14px;font-weight:500;cursor:pointer;transition:all .2s;display:flex;align-items:center;gap:7px;}
            .nbtn:hover{border-color:rgba(212,168,67,.4);color:var(--gold-light);}
            .nbtn.on{background:rgba(212,168,67,.15);border-color:var(--gold);color:var(--gold-light);}
            .rc{background:var(--gold);color:var(--navy);border-radius:100px;padding:2px 8px;font-size:12px;font-weight:700;}
            #tForm,#tDash{display:none;}
            #tForm.on,#tDash.on{display:block;}
            .mf{padding:36px 48px;display:grid;grid-template-columns:480px 1fr;gap:28px;align-items:start;max-width:1400px;margin:0 auto;width:100%;}
            .card{background:var(--cream);border-radius:18px;box-shadow:var(--shadow);overflow:hidden;}
            .ch{background:var(--navy-mid);padding:22px 28px;border-bottom:2px solid rgba(212,168,67,.2);}
            .ch h2{font-family:'Playfair Display',serif;font-size:19px;color:var(--white);display:flex;align-items:center;gap:9px;}
            .ch p{color:var(--g400);font-size:13px;margin-top:4px;}
            .cb{padding:26px;}
            .fg{margin-bottom:16px;}
            label{display:block;font-size:12px;font-weight:700;letter-spacing:.07em;text-transform:uppercase;color:var(--g600);margin-bottom:7px;}
            .req{color:var(--red);}.opt{color:var(--g400);font-weight:400;font-size:11px;letter-spacing:0;text-transform:none;}
            input,select{width:100%;padding:12px 15px;border:1.5px solid var(--g200);border-radius:9px;font-family:'DM Sans',sans-serif;font-size:15px;color:var(--navy);background:var(--white);outline:none;transition:border-color .2s,box-shadow .2s;}
            input:focus,select:focus{border-color:var(--gold);box-shadow:var(--ring);}
            input.er,select.er{border-color:var(--red)!important;background:#fffafa;}
            input.locked{background:var(--green-bg);color:var(--green);border-color:var(--green-bd)!important;font-weight:600;cursor:not-allowed;}
            input::placeholder{color:var(--g400);}select{cursor:pointer;}
            .fr{display:grid;grid-template-columns:1fr 1fr;gap:13px;}
            .divider{height:1px;background:var(--g200);margin:20px 0;}
            .lock-hint{font-size:12px;color:var(--green);margin-top:5px;display:none;align-items:center;gap:4px;}
            .lock-hint.show{display:flex;}
            .sc-hdr{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px;}
            .sc-cols{display:grid;grid-template-columns:110px 1fr 75px 75px 34px;gap:8px;padding:0 12px;margin-bottom:6px;}
            .sc-lbl{font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:var(--g400);text-align:center;}
            .slist{display:flex;flex-direction:column;gap:8px;}
            .srow{background:var(--white);border:1.5px solid var(--g200);border-radius:10px;padding:12px 13px;display:grid;grid-template-columns:110px 1fr 75px 75px 34px;gap:8px;align-items:center;transition:border-color .2s;}
            .srow:hover{border-color:rgba(212,168,67,.35);}
            .srow.re{border-color:var(--red);background:#fffafa;}
            .srow input{border:1.5px solid var(--g200);border-radius:6px;padding:8px 10px;font-size:14px;background:var(--g100);}
            .srow input:focus{border-color:var(--gold);box-shadow:none;background:var(--white);}
            .srow input.er{border-color:var(--red);background:#fffafa;}
            .srow input::placeholder{font-size:12px;color:var(--g400);}
            .rmb{width:34px;height:34px;background:var(--red-bg);border:none;border-radius:7px;cursor:pointer;color:var(--red);font-size:16px;display:flex;align-items:center;justify-content:center;transition:background .2s;}
            .rmb:hover{background:var(--red-bd);}
            .addb{width:100%;padding:11px;background:transparent;border:1.5px dashed var(--g200);border-radius:8px;color:var(--g400);font-family:'DM Sans',sans-serif;font-size:14px;cursor:pointer;transition:all .2s;margin-top:8px;display:flex;align-items:center;justify-content:center;gap:6px;}
            .addb:hover{border-color:var(--gold);color:var(--gold);background:var(--gold-pale);}
            .abox{border-radius:10px;padding:14px 18px;font-size:14px;margin-bottom:11px;display:none;line-height:1.55;}
            .abox.show{display:block;}
            .abox.error{background:var(--red-bg);border:1px solid var(--red-bd);color:var(--red);}
            .abox.warn{background:var(--warn-bg);border:1px solid var(--warn-bd);color:var(--warn);}
            .abox.info{background:var(--green-bg);border:1px solid var(--green-bd);color:var(--green);}
            .abox.suc{background:#e8f5ee;border:1px solid #86efac;color:#1a7a4a;}
            .bprim{width:100%;padding:15px;background:linear-gradient(135deg,var(--navy),var(--navy-light));color:var(--white);border:none;border-radius:9px;font-family:'DM Sans',sans-serif;font-size:16px;font-weight:600;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:8px;transition:all .3s;position:relative;overflow:hidden;}
            .bprim::before{content:'';position:absolute;inset:0;background:linear-gradient(135deg,var(--gold),var(--gold-light));opacity:0;transition:opacity .3s;}
            .bprim:hover::before{opacity:1;}
            .bprim:hover{color:var(--navy);}
            .bprim span{position:relative;z-index:1;}
            .bnew{padding:15px 20px;background:rgba(255,255,255,.08);border:1.5px solid rgba(255,255,255,.15);border-radius:9px;color:rgba(255,255,255,.7);font-family:'DM Sans',sans-serif;font-size:15px;font-weight:600;cursor:pointer;white-space:nowrap;transition:all .2s;}
            .bnew:hover{background:rgba(255,255,255,.15);color:var(--white);}
            .ph{background:rgba(255,255,255,.04);border:1.5px dashed rgba(212,168,67,.17);border-radius:15px;padding:70px 44px;text-align:center;color:rgba(255,255,255,.26);}
            .ph .pi{font-size:60px;margin-bottom:16px;}
            .ph p{font-size:16px;line-height:1.85;}
            .ph strong{color:rgba(212,168,67,.5);}
            /* REPORT */
            .report-card{background:var(--white);border-radius:15px;box-shadow:var(--shadow);overflow:hidden;}
            .rpt-header{background:linear-gradient(135deg,var(--navy) 0%,var(--navy-light) 100%);padding:26px 30px 20px;position:relative;overflow:hidden;}
            .rpt-header::before{content:'';position:absolute;top:-34px;right:-34px;width:150px;height:150px;border-radius:50%;background:rgba(212,168,67,.08);}
            .rpt-inst{font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:var(--gold);margin-bottom:5px;font-weight:700;}
            .rpt-title{font-family:'Playfair Display',serif;font-size:28px;color:var(--white);font-weight:900;line-height:1.1;margin-bottom:14px;}
            .rpt-meta{display:flex;gap:20px;flex-wrap:wrap;}
            .rmi{color:rgba(255,255,255,.5);font-size:14px;}
            .rmi strong{color:var(--white);display:block;font-size:16px;}
            .student-strip{background:var(--gold-pale);border-bottom:2px solid rgba(212,168,67,.2);padding:20px 36px;display:flex;gap:28px;align-items:center;flex-wrap:wrap;}
            .si .sil{font-size:11px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:var(--g400);margin-bottom:4px;}
            .si .siv{font-size:16px;font-weight:600;color:var(--navy);}
            .rbadge{background:var(--navy);color:var(--gold);padding:5px 14px;border-radius:100px;font-family:'JetBrains Mono',monospace;font-size:15px;font-weight:600;}
            .grade-summary{padding:24px 36px;display:grid;grid-template-columns:repeat(4,1fr);gap:14px;border-bottom:1px solid var(--g200);}
            .sb{background:var(--g100);border-radius:12px;padding:18px;text-align:center;}
            .sb .sl{font-size:11px;font-weight:700;letter-spacing:.07em;text-transform:uppercase;color:var(--g400);margin-bottom:5px;}
            .sb .sv{font-family:'Playfair Display',serif;font-size:30px;font-weight:700;color:var(--navy);}
            .sb .ss{font-size:12px;color:var(--g400);margin-top:3px;}
            .sb.hl{background:var(--navy);}
            .sb.hl .sl{color:rgba(255,255,255,.4);}
            .sb.hl .sv{color:var(--gold-light);}
            .sb.hl .ss{color:rgba(255,255,255,.3);}
            .tbl-wrap{padding:22px 36px;}
            .tbl-head{font-family:'Playfair Display',serif;font-size:18px;color:var(--navy);margin-bottom:11px;display:flex;align-items:center;gap:8px;}
            .tbl-head::after{content:'';flex:1;height:1.5px;background:var(--g200);}
            table{width:100%;border-collapse:collapse;font-size:15px;}
            thead tr{background:var(--navy);}
            thead th{padding:12px 16px;text-align:left;font-size:12px;font-weight:700;letter-spacing:.07em;text-transform:uppercase;color:rgba(255,255,255,.5);}
            thead th:first-child{border-radius:7px 0 0 7px;}thead th:last-child{border-radius:0 7px 7px 0;text-align:center;}
            tbody tr{border-bottom:1px solid var(--g200);}
            tbody tr:hover{background:var(--g100);}
            tbody td{padding:14px 16px;color:var(--navy);vertical-align:middle;}
            tbody td:last-child{text-align:center;}
            .cbadge{font-family:'JetBrains Mono',monospace;font-size:12px;font-weight:600;background:var(--navy-mid);color:var(--gold);padding:3px 9px;border-radius:5px;display:inline-block;}
            .gp{display:inline-flex;align-items:center;padding:5px 14px;border-radius:100px;font-size:13px;font-weight:700;}
            .gO,.gAp{background:#e8f5ee;color:#1a7a4a;}.gA,.gBp{background:#e0f0ff;color:#1558b0;}
            .gB{background:#f0e8ff;color:#6b21a8;}.gC{background:#fff8e0;color:#92600a;}.gF{background:#fde8e8;color:#c0392b;}
            .bw{display:flex;align-items:center;gap:6px;}
            .bb{flex:1;height:4px;background:var(--g200);border-radius:100px;overflow:hidden;}
            .bf{height:100%;border-radius:100px;background:linear-gradient(90deg,var(--gold),var(--gold-light));}
            .bn{font-weight:600;font-size:12px;color:var(--navy);min-width:26px;}.bt{font-size:10px;color:var(--g400);}
            .rpt-footer{border-top:1px solid var(--g200);padding:18px 36px;display:flex;align-items:center;justify-content:space-between;background:var(--g100);}
            .rpt-footer p{font-size:13px;color:var(--g400);}
            .ft-btns{display:flex;gap:6px;}
            .bsm{padding:9px 18px;border:none;border-radius:8px;font-family:'DM Sans',sans-serif;font-size:13px;font-weight:600;cursor:pointer;transition:all .2s;display:flex;align-items:center;gap:4px;}
            .bedit{background:rgba(212,168,67,.15);color:var(--gold);border:1px solid rgba(212,168,67,.3);}
            .bedit:hover{background:rgba(212,168,67,.28);}
            .bprint{background:var(--navy);color:var(--white);}
            .bprint:hover{background:var(--navy-light);}
            /* DASH */
            .dash{padding:36px 48px;max-width:1400px;margin:0 auto;width:100%;}
            .dt{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px;}
            .dt-title{font-family:'Playfair Display',serif;font-size:30px;color:var(--white);}
            .dt-title span{display:block;font-family:'DM Sans',sans-serif;font-size:14px;color:rgba(255,255,255,.36);margin-top:4px;font-weight:400;}
            .dc{display:flex;gap:7px;flex-wrap:wrap;}
            .sb2{position:relative;}
            .sb2 input{background:rgba(255,255,255,.07);border:1.5px solid rgba(255,255,255,.1);color:var(--white);border-radius:8px;padding:10px 14px 10px 38px;width:240px;font-size:14px;}
            .sb2 input::placeholder{color:rgba(255,255,255,.26);}
            .sb2 input:focus{border-color:var(--gold);background:rgba(255,255,255,.1);}
            .sb2 .si2{position:absolute;left:10px;top:50%;transform:translateY(-50%);color:rgba(255,255,255,.28);font-size:12px;pointer-events:none;}
            .fsel{background:rgba(255,255,255,.07);border:1.5px solid rgba(255,255,255,.1);color:rgba(255,255,255,.68);border-radius:8px;padding:10px 14px;font-size:14px;font-family:'DM Sans',sans-serif;}
            .fsel:focus{border-color:var(--gold);}
            .fsel option{background:var(--navy-mid);color:var(--white);}
            .ss2{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:24px;}
            .stb{background:rgba(255,255,255,.05);border:1px solid rgba(255,255,255,.07);border-radius:14px;padding:22px 20px;text-align:center;}
            .stb .sv{font-family:'Playfair Display',serif;font-size:36px;font-weight:700;color:var(--white);}
            .stb .sl{font-size:12px;color:rgba(255,255,255,.36);text-transform:uppercase;letter-spacing:.07em;margin-top:5px;}
            .stb.gld .sv{color:var(--gold-light);}
            .dtw{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.07);border-radius:13px;overflow:hidden;}
            .dtb{width:100%;border-collapse:collapse;}
            .dtb thead tr{background:rgba(255,255,255,.06);border-bottom:1px solid rgba(255,255,255,.07);}
            .dtb thead th{padding:14px 16px;text-align:left;font-size:12px;font-weight:700;letter-spacing:.09em;text-transform:uppercase;color:rgba(255,255,255,.36);}
            .dtb tbody tr{border-bottom:1px solid rgba(255,255,255,.05);transition:background .14s;}
            .dtb tbody tr:hover{background:rgba(212,168,67,.06);}
            .dtb tbody tr:last-child{border-bottom:none;}
            .dtb tbody td{padding:16px 18px;font-size:14px;color:rgba(255,255,255,.68);}
            .rtag{font-family:'JetBrains Mono',monospace;font-size:13px;background:rgba(212,168,67,.15);color:var(--gold);padding:4px 10px;border-radius:5px;}
            .nmc{font-weight:600;color:var(--white);font-size:15px;}
            .abs{display:flex;gap:4px;}
            .ab{padding:7px 14px;border:none;border-radius:6px;font-family:'DM Sans',sans-serif;font-size:12px;font-weight:600;cursor:pointer;transition:all .14s;}
            .av{background:rgba(37,99,235,.2);color:#60a5fa;}.av:hover{background:rgba(37,99,235,.34);}
            .ae{background:rgba(212,168,67,.18);color:var(--gold);}.ae:hover{background:rgba(212,168,67,.32);}
            .ad{background:rgba(192,57,43,.18);color:#f87171;}.ad:hover{background:rgba(192,57,43,.32);}
            .stats-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;}
            /* MODAL */
            .mov{position:fixed;inset:0;background:rgba(0,0,0,.72);z-index:1000;display:flex;align-items:center;justify-content:center;padding:14px;opacity:0;pointer-events:none;transition:opacity .22s;}
            .mov.open{opacity:1;pointer-events:all;}
            .mod{background:var(--cream);border-radius:15px;width:100%;max-width:780px;max-height:92vh;overflow-y:auto;box-shadow:0 40px 100px rgba(0,0,0,.5);transform:translateY(17px);transition:transform .22s;}
            .mov.open .mod{transform:translateY(0);}
            .mhd{background:var(--navy-mid);padding:16px 20px;display:flex;align-items:center;justify-content:space-between;border-bottom:2px solid rgba(212,168,67,.2);position:sticky;top:0;z-index:1;}
            .mhd h3{font-family:'Playfair Display',serif;font-size:18px;color:var(--white);}
            .mcls{background:rgba(255,255,255,.07);border:none;color:rgba(255,255,255,.44);font-size:17px;width:30px;height:30px;border-radius:6px;cursor:pointer;}
            .mcls:hover{background:rgba(192,57,43,.3);color:#f87171;}
            .mbd{padding:24px;}
            @media print{.bg-pat,.bg-grid,header,.form-panel,.ph,.bprint,.bedit,.rpt-footer .ft-btns,#tDash{display:none!important;} .mf{grid-template-columns:1fr;padding:0;} .report-card{box-shadow:none;border-radius:0;}}
            @media(max-width:1040px){.mf,.dash{padding:16px;} .mf{grid-template-columns:1fr;} .grade-summary,.ss2{grid-template-columns:repeat(2,1fr);}}
        """;
    }
}
