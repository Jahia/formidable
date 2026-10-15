package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.PairRule;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairRuleTest {

    private static SubmittedFile file(String field, String mime) {
        return new SubmittedFile(field, "f", mime, new byte[]{1});
    }

    private static PairRule rule() throws ConfigException {
        return PairRule.parse(List.of("captions<-media:video/*")).get(0);
    }

    @Test
    void parsesDependentFieldFieldAndPattern() throws ConfigException {
        assertEquals(new PairRule("captions", "media", "video/*"), rule());
        assertEquals(new PairRule("c", "m", "image/png"), PairRule.parse(List.of(" c <- m : IMAGE/PNG ")).get(0));
    }

    @Test
    void refusesALineThatIsNotARule() {
        for (String line : List.of("captions-media:video/*", "captions<-media", "<-media:video/*", "captions<-:video/*",
                "captions<-media:video", "captions<-media:not a type", "same<-same:video/*")) {
            assertThrows(ConfigException.class, () -> PairRule.parse(List.of(line)), line);
        }
    }

    @Test
    void everyVideoNeedsItsCaptionsFile() throws ConfigException {
        PairRule rule = rule();
        assertTrue(rule.isBrokenBy(List.of(file("media", "video/mp4"))), "a video and no captions");
        assertFalse(rule.isBrokenBy(List.of(file("media", "video/mp4"), file("captions", "text/vtt"))));
        assertTrue(rule.isBrokenBy(List.of(file("media", "video/mp4"), file("media", "video/webm"), file("captions", "text/vtt"))),
                "two videos, one captions file");
        assertFalse(rule.isBrokenBy(List.of(file("media", "video/mp4"), file("media", "video/webm"),
                file("captions", "text/vtt"), file("captions", "text/vtt"))));
    }

    @Test
    void filesThatDoNotMatchThePatternNeedNothing() throws ConfigException {
        PairRule rule = rule();
        assertFalse(rule.isBrokenBy(List.of(file("media", "image/png"), file("media", "application/pdf"))));
        assertFalse(rule.isBrokenBy(List.of()));
        assertFalse(rule.isBrokenBy(List.of(file("other", "video/mp4"))), "a video in another field is not this rule's business");
    }

    @Test
    void anImageAndAVideoInOneFieldOnlyTheVideoNeedsCaptions() throws ConfigException {
        PairRule rule = rule();
        List<SubmittedFile> files = new ArrayList<>(List.of(file("media", "image/jpeg"), file("media", "video/mp4")));
        assertTrue(rule.isBrokenBy(files));
        files.add(file("captions", "text/vtt"));
        assertFalse(rule.isBrokenBy(files));
    }

    @Test
    void matchesTheDetectedTypeWithoutParametersAndIgnoringCase() throws ConfigException {
        PairRule rule = rule();
        assertTrue(rule.matches("VIDEO/MP4"));
        assertTrue(rule.matches("video/mp4; codecs=avc1"));
        assertFalse(rule.matches("videos/mp4"));
        assertFalse(rule.matches(null));
        assertFalse(rule.matches(" "));
    }

    @Test
    void anExactPatternAndTheWildcardsMatchWhatTheySay() throws ConfigException {
        assertTrue(PairRule.parse(List.of("a<-b:image/png")).get(0).matches("image/png"));
        assertFalse(PairRule.parse(List.of("a<-b:image/png")).get(0).matches("image/jpeg"));
        assertTrue(PairRule.parse(List.of("a<-b:*/*")).get(0).matches("anything/else"));
        assertTrue(PairRule.parse(List.of("a<-b:*")).get(0).matches("anything/else"));
    }

    @Test
    void firstBrokenNamesTheRuleThatFails() throws ConfigException {
        List<PairRule> rules = PairRule.parse(List.of("captions<-media:video/*", "alt<-media:image/*"));
        List<SubmittedFile> files = List.of(file("media", "video/mp4"), file("captions", "text/vtt"), file("media", "image/png"));
        assertEquals("alt", PairRule.firstBroken(rules, files).orElseThrow().dependentField());
        assertTrue(PairRule.firstBroken(rules, List.of(file("media", "video/mp4"), file("captions", "text/vtt"))).isEmpty());
    }
}
