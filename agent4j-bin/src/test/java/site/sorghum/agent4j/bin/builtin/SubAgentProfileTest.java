package site.sorghum.agent4j.bin.builtin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentProfileTest {

    @Test
    void resolvesEveryBuiltInProfile() {
        for (String id : new String[]{"explore", "implement", "test", "review", "plan"}) {
            assertEquals(id, SubAgentProfile.from(id).id());
        }
    }

    @Test
    void readOnlyProfilesExposeOnlyReadOnlyTools() {
        for (SubAgentProfile profile : new SubAgentProfile[]{
                SubAgentProfile.EXPLORE, SubAgentProfile.REVIEW, SubAgentProfile.PLAN}) {
            assertTrue(profile.allowedTools().contains("read"));
            assertTrue(profile.allowedTools().contains("finish"));
            assertFalse(profile.allowedTools().contains("edit"));
            assertFalse(profile.allowedTools().contains("bash"));
        }
        assertNull(SubAgentProfile.IMPLEMENT.allowedTools());
        assertNull(SubAgentProfile.TEST.allowedTools());
    }

    @Test
    void rejectsUnknownProfile() {
        assertThrows(IllegalArgumentException.class, () -> SubAgentProfile.from("designer"));
    }
}
