package site.sorghum.cutin.core.context;

/**
 * 产物，表示一次循环执行中产生或保存的结构化内容。
 *
 * <p>产物与普通变量的区别在于它带有明确的类型，适合保存文件、图片、
 * JSON 对象等需要持久化和展示的内容。</p>
 */
public record Artifact(String name, String type, Object content) {
}
