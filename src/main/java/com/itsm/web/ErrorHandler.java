package com.itsm.web;

import java.net.URI;

import com.itsm.error.InvalidInputException;
import com.itsm.error.ItsmException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Turns errors into something a person can read, for every controller.
 *
 * <ul>
 *   <li>Something that does not exist: the 404 page.</li>
 *   <li>Something the user may not do: the 403 page.</li>
 *   <li>Any other broken rule, or a form that fails its checks: after
 *       pressing a button, back to the page the user came from with the
 *       message shown at the top (nothing was changed); when just opening a
 *       page, a page that shows the message.</li>
 *   <li>A request that cannot be understood at all: a "bad request" page.</li>
 * </ul>
 *
 * Controllers that know a better place to go back to, such as the report
 * form, catch the exception themselves. This class is the safety net.
 */
@ControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException e) {
        return page("error/404", HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ModelAndView unauthorized(UnauthorizedOperationException e, HttpServletRequest request) {
        log.warn("Refused: {} {} ({})", request.getMethod(), request.getRequestURI(), e.getMessage());
        return page("error/403", HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Every other broken rule. */
    @ExceptionHandler(ItsmException.class)
    public ModelAndView brokenRule(ItsmException e, HttpServletRequest request) {
        HttpStatus status = e instanceof InvalidInputException ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return backWithMessageOrPage(request, status, e.getMessage());
    }

    /** A form that failed its Bean Validation checks, in a controller that did not handle that itself. */
    @ExceptionHandler(BindException.class)
    public ModelAndView invalidForm(BindException e, HttpServletRequest request) {
        return backWithMessageOrPage(request, HttpStatus.BAD_REQUEST, describe(e));
    }

    /** A request with a missing or unreadable value, such as a status that does not exist. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView unreadableRequest(Exception e, HttpServletRequest request) {
        return backWithMessageOrPage(request, HttpStatus.BAD_REQUEST,
                "The request could not be understood. Please go back and try again.");
    }

    private static String describe(BindException e) {
        FieldError first = e.getFieldError();
        if (first == null) {
            return e.getAllErrors().isEmpty() ? "The form could not be read."
                    : e.getAllErrors().getFirst().getDefaultMessage() + ".";
        }
        if ("typeMismatch".equals(first.getCode())) {
            return "The value given for \"%s\" could not be understood.".formatted(first.getField());
        }
        return first.getDefaultMessage() + ".";
    }

    /**
     * After a button press (anything but GET): return to the page the user
     * was on and show the message there. Otherwise: show a page with the message.
     */
    private ModelAndView backWithMessageOrPage(HttpServletRequest request, HttpStatus status, String message) {
        if (!"GET".equals(request.getMethod())) {
            RequestContextUtils.getOutputFlashMap(request).put("error", message);
            return new ModelAndView("redirect:" + pageTheUserCameFrom(request));
        }
        return page("error/problem", status, message);
    }

    /** The path of the referring page if it belongs to this application, otherwise the user's dashboard. */
    private static String pageTheUserCameFrom(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                boolean sameSite = uri.getHost() == null || uri.getHost().equalsIgnoreCase(request.getServerName());
                if (sameSite && uri.getRawPath() != null && uri.getRawPath().startsWith("/")
                        && !uri.getRawPath().startsWith("//")) {
                    return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
                }
            } catch (IllegalArgumentException ignored) {
                // Not a usable address: fall through to the dashboard.
            }
        }
        return "/dashboard";
    }

    private static ModelAndView page(String view, HttpStatus status, String reason) {
        ModelAndView page = new ModelAndView(view);
        page.setStatus(status);
        page.addObject("status", status.value());
        page.addObject("reason", reason);
        return page;
    }
}
