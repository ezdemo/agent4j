package site.sorghum.loopra.bin.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.ai.mcp.client.McpServerParameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProjectConfigTest {

    @TempDir
    Path workspace;

    @Test
    void resolvesConfigUnderProjectLoopraDirectory() {
        assertEquals(
                workspace.toAbsolutePath().normalize().resolve(".loopra/mcp-servers.json"),
                McpProjectConfig.path(workspace)
        );
    }

    @Test
    void readsStandardMcpServersFormat() throws Exception {
        Path config = writeConfig("""
                {
                  "mcpServers": {
                    "local-tools": {
                      "type": "stdio",
                      "command": "node",
                      "args": ["server.js"],
                      "env": {"MODE": "project"}
                    },
                    "disabled-tools": {
                      "type": "stdio",
                      "command": "ignored",
                      "enabled": false
                    }
                  }
                }
                """);

        Map<String, McpServerParameters> servers = McpProjectConfig.read(config);

        assertEquals(2, servers.size());
        assertEquals("stdio", servers.get("local-tools").getTypeOrTransport());
        assertEquals("node", servers.get("local-tools").getCommand());
        assertEquals("project", servers.get("local-tools").getEnv().get("MODE"));
        assertFalse(servers.get("disabled-tools").isEnabled());
    }

    @Test
    void readsLoopraServersArrayFormatAndNormalizesTimeout() throws Exception {
        Path config = writeConfig("""
                {
                  "servers": [
                    {
                      "name": "project-remote",
                      "type": "http",
                      "url": "http://127.0.0.1:4318/mcp",
                      "timeout": "30s",
                      "headers": {"Authorization": "Bearer project-token"}
                    }
                  ]
                }
                """);

        Map<String, McpServerParameters> servers = McpProjectConfig.read(config);
        McpServerParameters server = servers.get("project-remote");

        assertEquals(1, servers.size());
        assertEquals("streamable", server.getTypeOrTransport());
        assertEquals("http://127.0.0.1:4318/mcp", server.getUrl());
        assertEquals(Duration.ofSeconds(30), server.getTimeout());
        assertEquals("Bearer project-token", server.getHeaders().get("Authorization"));
        assertTrue(server.isEnabled());
    }

    @Test
    void missingConfigReturnsEmptyMap() throws Exception {
        assertTrue(McpProjectConfig.read(workspace.resolve(".loopra/mcp-servers.json")).isEmpty());
        Files.createDirectories(workspace.resolve(".loopra"));
        assertTrue(McpProjectConfig.read(workspace.resolve(".loopra/mcp-servers.json")).isEmpty());
    }

    private Path writeConfig(String content) throws Exception {
        Path config = McpProjectConfig.path(workspace);
        Files.createDirectories(config.getParent());
        Files.writeString(config, content);
        return config;
    }
}
