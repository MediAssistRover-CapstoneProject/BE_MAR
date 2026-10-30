package com.mediassistrover.backend;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.enums.ZoneType;
import com.mediassistrover.backend.service.ZonePolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ZonePolicyTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void acceptsBothPolygonRepresentations() throws Exception {
        ZonePolicy.validate(ZoneType.KEEP_OUT,json.readTree("{\"points\":[[0,0],[1,0],[1,1],[0,1]]}"),null);
        ZonePolicy.validate(ZoneType.SPEED,json.readTree("[[0,0],[1,0],[1,1],[0,1],[0,0]]"),new BigDecimal("0.15"));
    }
    @Test void rejectsMissingOrExcessiveSpeed() throws Exception {
        var p=json.readTree("[[0,0],[1,0],[0,1]]");
        for(BigDecimal speed:new BigDecimal[]{null,BigDecimal.ZERO,new BigDecimal("0.81")})
            assertThatThrownBy(()->ZonePolicy.validate(ZoneType.SPEED,p,speed)).hasMessageContaining("speedLimitMps");
    }
    @Test void rejectsCrossedAndDegeneratePolygons() throws Exception {
        for(String source:new String[]{"[]","[[0,0],[1,1],[2,2]]","[[0,0],[1,1],[0,1],[1,0]]","[[0,0],[0,0],[1,1]]"}){
            var p=json.readTree(source);assertThatThrownBy(()->ZonePolicy.validate(ZoneType.KEEP_OUT,p,null)).isInstanceOf(RuntimeException.class);
        }
    }
}
