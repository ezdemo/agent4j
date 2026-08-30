package site.sorghum.loopra.bin.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerExportServiceTest {

    @Test
    void createsReadableTitlesForMachineNames() {
        assertEquals("Bash Wait", McpServerExportService.readableToolTitle("bash_wait"));
        assertEquals("Browser New Tab", McpServerExportService.readableToolTitle("browser-new_tab"));
        assertEquals("Call Api", McpServerExportService.readableToolTitle("callApi"));
    }

    @Test
    void fallsBackForMissingNames() {
        assertEquals("Loopra Tool", McpServerExportService.readableToolTitle(null));
        assertEquals("Loopra Tool", McpServerExportService.readableToolTitle("  "));
    }
}
