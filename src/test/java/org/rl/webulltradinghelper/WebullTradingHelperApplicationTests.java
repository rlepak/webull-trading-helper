package org.rl.webulltradinghelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {"webull.streaming.enabled=false", "binary-log.enabled=false",
        "aggregation.enabled=true", "health-log.interval-ms=300000"})
class WebullTradingHelperApplicationTests {

    @Test
    void startupLogsIncludeOneImmediateHealthSummary(CapturedOutput output) {
        String text = output.getOut();
        assertTrue(text.contains("Started WebullTradingHelperApplicationTests"));
        assertEquals(1, text.lines().filter(line -> line.contains("Health session=")).count());
        assertTrue(text.contains("connected=false"));
        assertTrue(text.contains("recording=DISABLED"));
        assertTrue(text.contains("aggregation=ENABLED activeBars=0 barsCompletedTotal=0"));
        assertFalse(text.contains("Webull market data {"));
    }
}
