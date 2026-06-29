package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;
import site.sorghum.agent4j.web.model.ApiResponse;

import java.util.Map;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 桌面宠物 API — 提供 pet.json 元数据与 spritesheet.webp 文件。
 * <p>
 * 宠物文件位于 {@code ~/.agent4j/pet/} 目录。
 */
@Api(tags = "宠物")
@Controller
@Mapping("/api/pet")
public class PetController {

    private static final Path PET_DIR =
            Paths.get(System.getProperty("user.home"), ".agent4j", "pet");

    @ApiOperation(value = "获取宠物元数据", notes = "返回 pet.json 的内容")
    @Get
    @Mapping("/info")
    @SneakyThrows
    public ApiResponse<?> info() {
        Path petJson = PET_DIR.resolve("pet.json");
        if (!Files.exists(petJson)) {
            return ApiResponse.ok("{}");
        }
        return ApiResponse.ok(ONode.ofJson(Files.readString(petJson, StandardCharsets.UTF_8)));
    }

    @ApiOperation(value = "保存宠物位置", notes = "将拖动位置写入 pet.json")
    @Put
    @Mapping("/position")
    @SneakyThrows
    public ApiResponse<?> savePosition(@Body Map<String, Object> pos) {
        Path petJson = PET_DIR.resolve("pet.json");
        ONode root = Files.exists(petJson)
                ? ONode.ofJson(Files.readString(petJson, StandardCharsets.UTF_8))
                : ONode.ofJson("{}").asObject();
        // 合并写入：x/y 归入 position 子对象，其余写入根
        if (pos.containsKey("x") || pos.containsKey("y")) {
            ONode positionNode = root.get("position") != null
                    ? root.get("position") : ONode.ofJson("{}").asObject();
            if (pos.containsKey("x")) positionNode.set("x", pos.get("x"));
            if (pos.containsKey("y")) positionNode.set("y", pos.get("y"));
            root.set("position", positionNode);
        }
        if (pos.containsKey("sizeIndex")) {
            root.set("sizeIndex", pos.get("sizeIndex"));
        }
        Files.writeString(petJson, root.toJson(), StandardCharsets.UTF_8);
        return ApiResponse.ok(root);
    }

    @ApiOperation(value = "获取宠物精灵图", notes = "返回 spritesheet.webp 文件字节")
    @Get
    @Mapping("/spritesheet")
    @SneakyThrows
    public void spritesheet() {
        Context current = Context.current();
        Path sheet = PET_DIR.resolve("spritesheet.webp");
        if (!Files.exists(sheet)) {
            return;
        }
        current.headerSet("Content-Type", "image/webp");
        current.output(Files.readAllBytes(sheet));
    }
}
