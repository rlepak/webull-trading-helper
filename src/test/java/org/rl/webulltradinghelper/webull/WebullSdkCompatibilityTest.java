package org.rl.webulltradinghelper.webull;

import com.webull.openapi.core.auth.signer.HmacSHA256Signer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WebullSdkCompatibilityTest {
    @Test void requestSignerWorksOnJava21() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(mac.doFinal("test-request".getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, new HmacSHA256Signer().getSign("test-request", "test-secret"));
    }
}
