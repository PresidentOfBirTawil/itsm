package com.quell.web;

import com.quell.assistant.Conversation;
import com.quell.assistant.ReportingAssistant;
import com.quell.domain.Employee;
import com.quell.repository.EmployeeRepository;
import com.quell.security.QuellUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The reporting assistant's chat page. The conversation lives in the
 * session. The page works without JavaScript (each answer is a form post);
 * with it, static/js/assistant.js sends answers in the background and swaps
 * in the new part of the chat, rendered by the same template.
 */
@Controller
@RequestMapping("/employee/assistant")
public class AssistantController {

    static final String SESSION_KEY = "quell.assistant";
    private static final String PAGE = "employee/assistant";

    private final ReportingAssistant assistant;
    private final EmployeeRepository employees;

    public AssistantController(ReportingAssistant assistant, EmployeeRepository employees) {
        this.assistant = assistant;
        this.employees = employees;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal QuellUserDetails user, HttpSession session, Model model) {
        Conversation conversation = conversation(session);
        assistant.start(conversation, employee(user));
        model.addAttribute("conversation", conversation);
        return PAGE;
    }

    @PostMapping
    public String answer(@AuthenticationPrincipal QuellUserDetails user, @RequestParam(defaultValue = "") String text,
                         @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
                         HttpSession session, Model model) {
        Conversation conversation = conversation(session);
        Employee employee = employee(user);
        assistant.start(conversation, employee);
        assistant.answer(conversation, employee, text);
        return reply(conversation, requestedWith, model);
    }

    @PostMapping("/restart")
    public String restart(@AuthenticationPrincipal QuellUserDetails user,
                          @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
                          HttpSession session, Model model) {
        Conversation conversation = conversation(session);
        assistant.restart(conversation, employee(user));
        return reply(conversation, requestedWith, model);
    }

    /** Just the chat part for the page's script; the whole page otherwise. */
    private static String reply(Conversation conversation, String requestedWith, Model model) {
        if ("fetch".equals(requestedWith)) {
            model.addAttribute("conversation", conversation);
            return PAGE + " :: chat";
        }
        return "redirect:/employee/assistant";
    }

    private static Conversation conversation(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(SESSION_KEY) instanceof Conversation conversation) {
                return conversation;
            }
            Conversation conversation = new Conversation();
            session.setAttribute(SESSION_KEY, conversation);
            return conversation;
        }
    }

    private Employee employee(QuellUserDetails user) {
        return employees.findById(user.getId()).orElseThrow();
    }
}
