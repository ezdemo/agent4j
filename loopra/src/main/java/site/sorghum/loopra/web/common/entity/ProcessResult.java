package site.sorghum.loopra.web.common.entity;

import lombok.Data;

/**
 * Git 进程执行结果。
 *
 * @author Sorghum
 */
@Data
public class ProcessResult {
    public int exitCode;
    public String stdout;
    public String stderr;
}
