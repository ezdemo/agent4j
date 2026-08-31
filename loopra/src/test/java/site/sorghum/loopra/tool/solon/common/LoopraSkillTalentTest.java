package site.sorghum.loopra.tool.solon.common;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.talents.cli.SkillProvider;
import org.noear.solon.ai.talents.mount.SkillDir;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraSkillTalentTest {
    @Test
    void refreshReturnsTheSameSkillListContent() {
        RecordingSkillProvider provider = new RecordingSkillProvider();
        LoopraSkillTalent talent = new LoopraSkillTalent(provider);

        assertEquals(talent.skilllist(), talent.skillrefresh());
        assertTrue(provider.refreshed);
    }

    private static class RecordingSkillProvider implements SkillProvider {
        private boolean refreshed;

        @Override public void refresh() { refreshed = true; }
        @Override public int getSkillCount() { return 0; }
        @Override public Collection<SkillDir> getSkillAll() { return List.of(); }
        @Override public Collection<SkillDir> searchSkill(String query) { return List.of(); }
        @Override public SkillDir getSkill(String name) { return null; }
        @Override public String readSkill(String name) { return null; }
        @Override public boolean isSkillAllowed(SkillDir skill) { return true; }
    }
}
