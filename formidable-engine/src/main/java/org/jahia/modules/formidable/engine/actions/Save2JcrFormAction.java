package org.jahia.modules.formidable.engine.actions;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Saves the submitted form data as child nodes under a designated JCR path.
 * Not yet implemented.
 */
@Component(service = FormAction.class)
public class Save2JcrFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(Save2JcrFormAction.class);

    @Override
    public String getNodeType() {
        return "fmdb:save2jcrAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {
        // TODO: persist form data as JCR child nodes of the form (or a configured storage path)
        log.warn("fmdb:save2jcrAction is not yet implemented — form data was not saved.");
    }
}

