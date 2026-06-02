package site.sorghum.agent4j.bin.skill;

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SkillV2 实现类。
 *
 * @author Sorghum
 */
public record SkillV2Impl(String name, String description, String body, Scope scope, String path, RunAs runAs,
                          List<String> allowedTools, String model) implements SkillV2 {

    public SkillV2Impl(String name, String description, String body, Scope scope,
                       String path, RunAs runAs, List<String> allowedTools, String model) {
        this.name = name;
        this.description = description != null ? description : "";
        this.body = body != null ? body : "";
        this.scope = scope;
        this.path = path;
        this.runAs = runAs != null ? runAs : RunAs.INLINE;
        this.allowedTools = allowedTools != null ? allowedTools : Collections.emptyList();
        this.model = model;
    }

    @Override
    public @NonNull String toString() {
        return String.format("Skill{name='%s', scope=%s, runAs=%s}", name, scope, runAs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkillV2Impl skill = (SkillV2Impl) o;
        return Objects.equals(name, skill.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}