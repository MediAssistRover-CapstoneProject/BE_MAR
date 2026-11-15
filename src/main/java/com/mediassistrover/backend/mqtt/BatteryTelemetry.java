package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.enums.BatteryUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Battery display telemetry only; never authorizes movement or estimates charge on BE. */
public final class BatteryTelemetry {
    public static final double FRESH_SECONDS = 60.;
    private static final Set<String> STATUSES=Set.of("ESTIMATED","WARMING_UP","STALE","INVALID","UNCONFIGURED");
    private BatteryTelemetry() { }

    public record Reading(int raw,BatteryUnit unit,BigDecimal percent,BigDecimal voltageV,
                          String estimateMethod,String profileId,String estimateStatus,Boolean curveCalibrated,
                          Integer seriesCells,Integer parallelCells,Integer nominalCapacityMah,BigDecimal nominalVoltageV,
                          Instant capturedAt) {
        public double ageSec(Instant now) {return Math.max(0.,Duration.between(capturedAt,now).toMillis()/1000.);}
        public boolean fresh(Instant now) {return raw>0 && !"INVALID".equals(estimateStatus)
                && !capturedAt.isAfter(now.plusSeconds(5)) && ageSec(now)<=FRESH_SECONDS;}
        public boolean estimated(Instant now) {
            return fresh(now) && unit==BatteryUnit.MILLIVOLT && "ESTIMATED".equals(estimateStatus)
                    && "VOLTAGE_CURVE".equals(estimateMethod) && percent!=null && voltageV!=null;
        }
    }

    public static Reading read(JsonNode battery,Instant occurredAt) {
        if(battery==null || !battery.isObject() || occurredAt==null
                || !battery.path("value").isIntegralNumber() || !battery.path("value").canConvertToInt()
                || !battery.path("age_sec").isNumber())return null;
        int raw=battery.path("value").intValue();double age=battery.path("age_sec").doubleValue();
        if(raw<0 || raw>65535 || !Double.isFinite(age) || age<0 || age>31536000)return null;
        BatteryUnit unit="MILLIVOLT".equals(battery.path("unit").asText())?BatteryUnit.MILLIVOLT:BatteryUnit.UNKNOWN;
        String status=battery.path("estimate_status").asText("UNCONFIGURED");
        if(!STATUSES.contains(status))status="UNCONFIGURED";
        String method="VOLTAGE_CURVE".equals(battery.path("estimate_method").asText())?"VOLTAGE_CURVE":"UNAVAILABLE";
        BigDecimal percent=number(battery,"percent",0,100),voltage=number(battery,"voltage_v",0.001,65.535);
        if(!"ESTIMATED".equals(status)) {
            percent=null;voltage=null;
        } else if(unit!=BatteryUnit.MILLIVOLT || !"VOLTAGE_CURVE".equals(method) || percent==null || voltage==null || raw==0) {
            percent=null;voltage=null;status="INVALID";
        }
        if(percent!=null)percent=percent.setScale(2,RoundingMode.HALF_UP);
        return new Reading(raw,unit,percent,voltage,method,text(battery,"profile_id",128),status,
                battery.path("curve_calibrated").isBoolean()?battery.path("curve_calibrated").booleanValue():null,
                integer(battery,"series_cells",1,32),integer(battery,"parallel_cells",1,32),
                integer(battery,"nominal_capacity_mah",1,10000000),number(battery,"nominal_voltage_v",0.001,655.35),
                occurredAt.minusMillis((long)(age*1000)));
    }

    public static void apply(Robot robot,JsonNode battery,Instant occurredAt,Instant now) {
        Reading reading=read(battery,occurredAt);
        if(reading!=null && (robot.getBatteryCapturedAt()==null || !reading.capturedAt().isBefore(robot.getBatteryCapturedAt()))) {
            robot.setBatteryRaw(reading.raw());robot.setBatteryUnit(reading.unit());robot.setBatteryCapturedAt(reading.capturedAt());
        }
        // A new heartbeat without a usable estimate invalidates the prior percentage.
        robot.setBatteryPercent(reading!=null && reading.estimated(now)
                && reading.capturedAt().equals(robot.getBatteryCapturedAt())?reading.percent():null);
    }

    private static BigDecimal number(JsonNode value,String key,double min,double max) {
        JsonNode field=value.path(key);
        if(!field.isNumber() || !Double.isFinite(field.doubleValue()))return null;
        BigDecimal result=field.decimalValue();
        return result.compareTo(BigDecimal.valueOf(min))>=0 && result.compareTo(BigDecimal.valueOf(max))<=0?result:null;
    }
    private static Integer integer(JsonNode value,String key,int min,int max) {
        JsonNode field=value.path(key);
        return field.isIntegralNumber() && field.canConvertToInt() && field.intValue()>=min && field.intValue()<=max?field.intValue():null;
    }
    private static String text(JsonNode value,String key,int max) {
        JsonNode field=value.path(key);
        return field.isTextual() && !field.textValue().isBlank() && field.textValue().length()<=max?field.textValue():null;
    }
}
