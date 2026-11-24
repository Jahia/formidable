package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.test.modules.formidable.samples.actions.field.ProviderStubServlet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The receiver of a forward target that a spec declares on this alias, for the test suite to read what a forward
 * action sent: a {@code POST} on the alias is a forwarded submission, and a
 * {@code GET} answers what the last one carried, as the number of submissions received since the last reset, the names
 * of the value parts and the names of the file parts. Names only, never a value and never a byte of a file. A
 * {@code POST} on {@code /reset} forgets everything, and lifts a refusal.
 *
 * <p>A spec makes a forward fail on demand: after a {@code POST} on {@code /refuse}, the sink answers every forwarded
 * submission with 503 and does not count it as received, until a {@code POST} on {@code /accept} or {@code /reset}.
 * The {@code GET} also answers whether the sink refuses, and how many forwards it refused.</p>
 *
 * <p>Open to any caller, on purpose, as the doubles of the providers are: it holds nothing real. The refusal is the
 * exception: only a server administrator sets it, with a {@code POST} that carries {@code X-Requested-With}, which a
 * cross-site form cannot send, so no visitor can make a forward of the platform fail. Any caller lifts it with
 * {@code /reset}, as any caller resets the sink: a test module accepts that.</p>
 */
@Component(
    service = { HttpServlet.class, Servlet.class },
    property = { "alias=" + ForwardSinkServlet.ALIAS },
    immediate = true
)
public class ForwardSinkServlet extends ProviderStubServlet {

    private static final long serialVersionUID = 1L;

    public static final String ALIAS = "/formidable-samples/forward-sink";
    static final String RESET_OPERATION = "/reset";
    static final String REFUSE_OPERATION = "/refuse";
    static final String ACCEPT_OPERATION = "/accept";
    /** The header a cross-site form or a simple request cannot send: a POST of the refusal needs it. */
    static final String REQUESTED_WITH = "X-Requested-With";
    private static final String ERROR = "error";
    /** The most the sink reads of one forwarded submission: the suite forwards small files. */
    static final int MAX_BODY_BYTES = 1024 * 1024;
    /** The header line that opens a part: its name, and its file name when the part is a file. */
    private static final Pattern PART = Pattern.compile(
            "Content-Disposition: form-data; name=\"([^\"]*)\"(; filename=)?", Pattern.CASE_INSENSITIVE);

    /** What one forwarded submission carried. */
    record Received(int count, List<String> fields, List<String> files) {}

    private static final Received NOTHING = new Received(0, List.of(), List.of());

    private final transient AtomicReference<Received> last = new AtomicReference<>(NOTHING);
    private final transient AtomicBoolean refusing = new AtomicBoolean();
    private final transient AtomicInteger refused = new AtomicInteger();
    private final transient ServerAdministrators administrators;

    public ForwardSinkServlet() {
        this(ServerAdministrators.ofThePlatform());
    }

    ForwardSinkServlet(ServerAdministrators administrators) {
        this.administrators = administrators;
    }

    @Override
    protected String alias() {
        return ALIAS;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        if (REFUSE_OPERATION.equals(operation) || ACCEPT_OPERATION.equals(operation)) {
            switchRefusal(req, resp, REFUSE_OPERATION.equals(operation));
            return;
        }
        if (!RESET_OPERATION.equals(operation) && refusing.get()) {
            refused.incrementAndGet();
            answer(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, new JSONObject().put(ERROR, "the sink refuses"));
            return;
        }
        Received received;
        if (RESET_OPERATION.equals(operation)) {
            received = NOTHING;
            last.set(NOTHING);
            refusing.set(false);
            refused.set(0);
        } else {
            String body = body(req.getInputStream());
            // two forwards at once each count: the read of the count and its write are one step
            received = last.updateAndGet(previous -> parts(body, previous.count() + 1));
        }
        answer(resp, HttpServletResponse.SC_OK, json(received));
    }

    /**
     * Sets or lifts the refusal: server administrators only, with {@link #REQUESTED_WITH}. Any other call gets 403 and
     * nothing changes.
     */
    private void switchRefusal(HttpServletRequest req, HttpServletResponse resp, boolean refuse) throws IOException {
        if (!administrators.currentUserIsOne()) {
            answer(resp, HttpServletResponse.SC_FORBIDDEN, new JSONObject().put(ERROR, "server administrators only"));
            return;
        }
        if (req.getHeader(REQUESTED_WITH) == null) {
            answer(resp, HttpServletResponse.SC_FORBIDDEN, new JSONObject().put(ERROR,
                    "a POST needs the header " + REQUESTED_WITH));
            return;
        }
        refusing.set(refuse);
        answer(resp, HttpServletResponse.SC_OK, json(last.get()));
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        answer(resp, HttpServletResponse.SC_OK, json(last.get()));
    }

    /** The body of a forwarded submission, capped, one char per byte so that a file's bytes cannot break the reading. */
    private static String body(InputStream in) throws IOException {
        return new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.ISO_8859_1);
    }

    /** The parts of a multipart body, by kind: a part with a file name is a file, any other a value. */
    static Received parts(String body, int count) {
        List<String> fields = new ArrayList<>();
        List<String> files = new ArrayList<>();
        Matcher part = PART.matcher(body);
        while (part.find()) {
            (part.group(2) == null ? fields : files).add(part.group(1));
        }
        return new Received(count, List.copyOf(fields), List.copyOf(files));
    }

    private JSONObject json(Received received) {
        return new JSONObject()
                .put("count", received.count())
                .put("fields", new JSONArray(received.fields()))
                .put("files", new JSONArray(received.files()))
                .put("refusing", refusing.get())
                .put("refused", refused.get());
    }
}
