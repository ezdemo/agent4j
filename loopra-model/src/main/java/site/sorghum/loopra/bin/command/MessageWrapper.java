package site.sorghum.loopra.bin.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息包装
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class MessageWrapper {
    /**
     * 消息
     */
    String message;
}
