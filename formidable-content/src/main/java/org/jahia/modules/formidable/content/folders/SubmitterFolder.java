package org.jahia.modules.formidable.content.folders;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds which subfolder of the target folders a submitter belongs to, from the groups they are a member of: the one
 * group whose name starts with the configured prefix, the rest of the name being the subfolder
 * ({@code orvane-supplier-} + {@code orvane-supplier-northlight} gives {@code northlight}). Pure and stateless.
 *
 * <p>Only groups of the platform ({@code /groups/<name>}) and of the form's own site
 * ({@code /sites/<site>/groups/<name>}) are considered: a group another site's administrators control does not point
 * this site's folders. The subfolder is checked as a name that cannot leave the folder it is looked up in. The match
 * is exact and case-sensitive, like group names.</p>
 *
 * <p>This only <em>names</em> a folder. Whether it exists, and whether the submitter may read it, is checked by the
 * action; it is that last check, made with the submitter's own session, that keeps one company out of another's folder.</p>
 */
public final class SubmitterFolder {

    /** A subfolder is a plain node name: no separator, no dot-only name, nothing a path can be built from. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.@-]*");

    /** What the lookup found. */
    public enum Outcome {
        /** Exactly one group matched: its subfolder is in {@link Result#subfolder()}. */
        FOUND,
        /** No group of the submitter matches the prefix. */
        NONE,
        /** Several groups, leading to different subfolders, match: the submitter belongs to more than one. */
        SEVERAL,
        /** One group matches, but what is left of its name cannot be a subfolder name. */
        UNSAFE
    }

    /**
     * The answer.
     *
     * @param outcome   what was found
     * @param subfolder the subfolder name when {@code outcome} is {@link Outcome#FOUND}, otherwise {@code null}
     */
    public record Result(Outcome outcome, String subfolder) {
    }

    private SubmitterFolder() {
    }

    /**
     * @param prefix     the configured group prefix, not blank
     * @param groupPaths the paths of the groups the submitter is a member of ({@code /groups/x}, {@code /sites/s/groups/x})
     * @param siteKey    the form's site, whose own groups count
     * @return the subfolder, or why there is none
     */
    public static Result resolve(String prefix, Collection<String> groupPaths, String siteKey) {
        if (prefix == null || prefix.isBlank() || groupPaths == null) {
            return new Result(Outcome.NONE, null);
        }
        Set<String> rests = new LinkedHashSet<>();
        for (String path : groupPaths) {
            String name = groupName(path, siteKey);
            if (name != null && name.length() > prefix.length() && name.startsWith(prefix)) {
                rests.add(name.substring(prefix.length()));
            }
        }
        if (rests.isEmpty()) {
            return new Result(Outcome.NONE, null);
        }
        if (rests.size() > 1) {
            return new Result(Outcome.SEVERAL, null);
        }
        String rest = rests.iterator().next();
        if (!SAFE_NAME.matcher(rest).matches() || rest.contains("..")) {
            return new Result(Outcome.UNSAFE, null);
        }
        return new Result(Outcome.FOUND, rest);
    }

    /**
     * @param path    a group path
     * @param siteKey the form's site
     * @return the group's name when the path is a platform group or a group of that site, otherwise {@code null}
     */
    static String groupName(String path, String siteKey) {
        if (path == null) {
            return null;
        }
        String globalRoot = "/groups/";
        String siteRoot = siteKey == null || siteKey.isBlank() ? null : "/sites/" + siteKey + "/groups/";
        String name;
        if (path.startsWith(globalRoot)) {
            name = path.substring(globalRoot.length());
        } else if (siteRoot != null && path.startsWith(siteRoot)) {
            name = path.substring(siteRoot.length());
        } else {
            return null;
        }
        // a nested path (a group stored in a sub-folder of /groups) is not a plain group name
        return name.isEmpty() || name.indexOf('/') >= 0 ? null : name;
    }
}
