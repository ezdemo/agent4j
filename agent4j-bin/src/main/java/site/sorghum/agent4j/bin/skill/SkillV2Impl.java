package site.sorghum.agent4j.bin.skill;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SkillV2 实现类。
 *
 * @author Sorghum
 */
public class SkillV2Impl implements SkillV2 {

    private final String name;
    private final String description;
    private final String body;
    private final Scope scope;
    private final String path;
    private final RunAs runAs;
    private final List<String> allowedTools;
    private final String model;

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
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getBody() {
        return body;
    }

    @Override
    public Scope getScope() {
        return scope;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public RunAs getRunAs() {
        return runAs;
    }

    @Override
    public List<String> getAllowedTools() {
        return allowedTools;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String toString() {
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