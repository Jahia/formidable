package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.test.modules.formidable.quarantine.stub.StubCallLog.StubCall;
import org.jahia.test.modules.formidable.quarantine.stub.StubControl;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The control of the stub checks, for the test suite, under {@code /modules/formidable-quarantine/stub}:
 * <ul>
 * <li>{@code GET /calls}, with an optional {@code check} parameter: the calls the stubs received, oldest first, all
 * of them or those of one check node; and whether the outage is on;</li>
 * <li>{@code POST /calls/clear}: forgets the calls;</li>
 * <li>{@code POST /outage/lift} and {@code POST /outage/set}: lifts or sets the outage of the stubs.</li>
 * </ul>
 * A call names the node type of the stub, the check node, the SHA-256 of the file and the kind of call: never a byte.
 *
 * <p>Server administrators only: any other caller gets 403 and nothing changes. A POST also needs the header
 * {@code X-Requested-With}.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=" + StubServlet.ALIAS}, immediate = true)
public class StubServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String ALIAS = "/formidable-quarantine/stub";
    static final String CALLS = "/calls";
    static final String CLEAR = "/calls/clear";
    static final String LIFT = "/outage/lift";
    static final String SET = "/outage/set";
    static final String CHECK_PARAMETER = "check";

    private final transient ServerAdministrators administrators;
    private final transient AtomicReference<StubControl> control = new AtomicReference<>();

    public StubServlet() {
        this(ServerAdministrators.ofThePlatform());
    }

    StubServlet(ServerAdministrators administrators) {
        this.administrators = administrators;
    }

    @Reference
    public void setControl(StubControl control) {
        this.control.set(control);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        if (!administrators.currentUserIsOne()) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_FORBIDDEN, JsonAnswers.error(JsonAnswers.FORBIDDEN));
            return;
        }
        if (!CALLS.equals(JsonAnswers.operationOf(req, ALIAS))) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_NOT_FOUND, JsonAnswers.error("GET serves " + CALLS));
            return;
        }
        String check = req.getParameter(CHECK_PARAMETER);
        List<StubCall> calls = check == null ? control.get().log().calls() : control.get().log().callsOf(check);
        JsonAnswers.answer(resp, HttpServletResponse.SC_OK, state(calls));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (!administrators.currentUserIsOne()) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_FORBIDDEN, JsonAnswers.error(JsonAnswers.FORBIDDEN));
            return;
        }
        if (JsonAnswers.refusedAsCrossSite(req, resp)) {
            return;
        }
        switch (JsonAnswers.operationOf(req, ALIAS)) {
            case CLEAR:
                control.get().log().clear();
                break;
            case LIFT:
                control.get().outage().lift();
                break;
            case SET:
                control.get().outage().set();
                break;
            default:
                JsonAnswers.answer(resp, HttpServletResponse.SC_NOT_FOUND,
                        JsonAnswers.error("POST serves " + CLEAR + ", " + LIFT + " and " + SET));
                return;
        }
        JsonAnswers.answer(resp, HttpServletResponse.SC_OK, state(control.get().log().calls()));
    }

    private JSONObject state(List<StubCall> calls) {
        JSONArray array = new JSONArray();
        for (StubCall call : calls) {
            array.put(new JSONObject()
                    .put("nodeType", call.nodeType())
                    .put("checkName", nullable(call.checkName()))
                    .put("sha256", nullable(call.sha256()))
                    .put("kind", call.kind().name()));
        }
        return new JSONObject().put("outage", control.get().outage().isDown()).put("calls", array);
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }
}
