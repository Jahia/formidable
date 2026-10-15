package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.folders.SubmitterFolder;
import org.jahia.modules.formidable.content.folders.SubmitterFolder.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubmitterFolderTest {

    private static final String PREFIX = "orvane-supplier-";

    private static SubmitterFolder.Result resolve(String... groups) {
        return SubmitterFolder.resolve(PREFIX, List.of(groups), "orvane");
    }

    @Test
    void theRestOfTheGroupNameIsTheSubfolder() {
        SubmitterFolder.Result result = resolve("/groups/users", "/groups/orvane-supplier-northlight");
        assertEquals(Outcome.FOUND, result.outcome());
        assertEquals("northlight", result.subfolder());
    }

    @Test
    void aGroupOfTheFormsOwnSiteCounts() {
        assertEquals("northlight", resolve("/sites/orvane/groups/orvane-supplier-northlight").subfolder());
    }

    @Test
    void aGroupOfAnotherSiteDoesNot() {
        SubmitterFolder.Result result = resolve("/sites/rival/groups/orvane-supplier-northlight");
        assertEquals(Outcome.NONE, result.outcome());
        assertNull(result.subfolder());
    }

    @Test
    void noMatchingGroupIsNone() {
        assertEquals(Outcome.NONE, resolve("/groups/users", "/groups/privileged").outcome());
        assertEquals(Outcome.NONE, resolve().outcome());
        assertEquals(Outcome.NONE, SubmitterFolder.resolve(PREFIX, null, "orvane").outcome());
    }

    @Test
    void theNameMustBeLongerThanThePrefix() {
        assertEquals(Outcome.NONE, resolve("/groups/orvane-supplier-").outcome());
    }

    @Test
    void severalGroupsLeadingToDifferentSubfoldersAreRefused() {
        assertEquals(Outcome.SEVERAL, resolve("/groups/orvane-supplier-northlight", "/groups/orvane-supplier-harbor").outcome());
    }

    @Test
    void theSameGroupSeenTwiceIsOneSubfolder() {
        SubmitterFolder.Result result = resolve("/groups/orvane-supplier-northlight", "/sites/orvane/groups/orvane-supplier-northlight");
        assertEquals(Outcome.FOUND, result.outcome());
        assertEquals("northlight", result.subfolder());
    }

    @Test
    void theMatchIsCaseSensitiveLikeGroupNames() {
        assertEquals(Outcome.NONE, resolve("/groups/Orvane-Supplier-northlight").outcome());
    }

    @Test
    void aNameThatCouldLeaveTheFolderIsUnsafe() {
        for (String rest : List.of("..", "../x", "a..b", ".hidden", "a b", "a/b", "-x")) {
            // a group named like this cannot be told from the path alone when it holds a slash: the path is then not a group name
            SubmitterFolder.Result result = resolve("/groups/" + PREFIX + rest);
            Outcome expected = rest.contains("/") ? Outcome.NONE : Outcome.UNSAFE;
            assertEquals(expected, result.outcome(), rest);
            assertNull(result.subfolder(), rest);
        }
    }

    @Test
    void aSafeNameKeepsItsCharacters() {
        assertEquals("north_light-2.0@co", resolve("/groups/" + PREFIX + "north_light-2.0@co").subfolder());
    }

    @Test
    void anythingButAGroupPathIsIgnored() {
        assertEquals(Outcome.NONE, resolve("/users/orvane-supplier-northlight", "orvane-supplier-northlight",
                "/sites/orvane/groups/sub/orvane-supplier-northlight", "/groups/sub/orvane-supplier-northlight", "/groups/").outcome());
    }

    @Test
    void aBlankPrefixFindsNothing() {
        assertEquals(Outcome.NONE, SubmitterFolder.resolve(" ", List.of("/groups/x"), "orvane").outcome());
        assertEquals(Outcome.NONE, SubmitterFolder.resolve(null, List.of("/groups/x"), "orvane").outcome());
    }
}
