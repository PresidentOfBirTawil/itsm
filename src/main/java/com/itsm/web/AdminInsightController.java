package com.itsm.web;

import java.util.Arrays;

import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.report.ReportService;
import com.itsm.report.Reports;
import com.itsm.repository.AuditEntryRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The administrator's reports and the audit log across all incidents. */
@Controller
@RequestMapping("/admin")
public class AdminInsightController {

    /** The audit log shows at most this many entries; the filters narrow it down. */
    private static final int AUDIT_LOG_LIMIT = 300;

    private final ReportService reportService;
    private final AuditEntryRepository auditEntries;

    public AdminInsightController(ReportService reportService, AuditEntryRepository auditEntries) {
        this.reportService = reportService;
        this.auditEntries = auditEntries;
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        Reports reports = reportService.build();
        model.addAttribute("reports", reports);
        model.addAttribute("largestStatus", Reports.largest(reports.byStatus()));
        model.addAttribute("largestPriority", Reports.largest(reports.byPriority()));
        model.addAttribute("largestCategory", Reports.largest(reports.byCategory()));
        return "admin/reports";
    }

    /** Every recorded action on every incident, newest first. Can be narrowed to one incident or one kind of action. */
    @GetMapping("/audit")
    public String auditLog(@RequestParam(required = false) String code,
                           @RequestParam(required = false) IncidentAction action, Model model) {
        model.addAttribute("entries", auditEntries.search(codePattern(code), action, Limit.of(AUDIT_LOG_LIMIT)));
        model.addAttribute("code", code);
        model.addAttribute("action", action);
        model.addAttribute("actions", Arrays.asList(IncidentAction.values()));
        model.addAttribute("limit", AUDIT_LOG_LIMIT);
        return "admin/audit";
    }

    /**
     * What the "incident code" box means: a bare number is that one incident
     * ("7" is INC-0007), anything else is matched as part of the code.
     */
    static String codePattern(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String text = code.trim().toUpperCase();
        if (text.matches("\\d{1,9}")) {
            return Incident.codeFor(Long.parseLong(text));
        }
        return "%" + text.replace("%", "").replace("_", "") + "%";
    }
}
