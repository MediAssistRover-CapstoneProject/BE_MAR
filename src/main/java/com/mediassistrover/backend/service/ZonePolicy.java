package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.enums.ZoneType;
import com.mediassistrover.backend.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.ArrayList;

/** Coordinates use metres in the map frame. Validate before publishing to Pi. */
public final class ZonePolicy {
    private ZonePolicy() {}
    public static void validate(ZoneType type, JsonNode polygon, BigDecimal speed) {
        if(type==ZoneType.NO_STOP)return;
        if(type==ZoneType.SPEED && (speed==null || speed.signum()<=0 || speed.compareTo(new BigDecimal("0.8"))>0))
            throw new BusinessRuleException("SPEED zone requires speedLimitMps in (0, 0.8]");
        JsonNode points=polygon!=null && polygon.isObject()?polygon.path("points"):polygon;
        if(points==null || !points.isArray() || points.size()<3 || points.size()>256)
            throw new BusinessRuleException("Zone polygon requires 3..256 [x,y] map points");
        var p=new ArrayList<double[]>();
        for(JsonNode point:points){
            if(!point.isArray() || point.size()!=2 || !point.get(0).isNumber() || !point.get(1).isNumber())
                throw new BusinessRuleException("Zone vertices must be numeric [x,y] pairs in metres");
            double x=point.get(0).asDouble(),y=point.get(1).asDouble();
            if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>100000||Math.abs(y)>100000)
                throw new BusinessRuleException("Invalid zone coordinate");
            p.add(new double[]{x,y});
        }
        if(p.size()>3 && java.util.Arrays.equals(p.getFirst(),p.getLast()))p.removeLast();
        double area=0;
        for(int i=0;i<p.size();i++){
            var a=p.get(i);var b=p.get((i+1)%p.size());
            if(java.util.Arrays.equals(a,b))throw new BusinessRuleException("Duplicate adjacent zone vertices");
            area+=a[0]*b[1]-b[0]*a[1];
            for(int j=i+2;j<p.size();j++){
                if((j+1)%p.size()==i)continue;
                var c=p.get(j);var d=p.get((j+1)%p.size());
                if(cross(a,b,c)*cross(a,b,d)<=0 && cross(c,d,a)*cross(c,d,b)<=0
                    && Math.max(Math.min(a[0],b[0]),Math.min(c[0],d[0]))<=Math.min(Math.max(a[0],b[0]),Math.max(c[0],d[0]))
                    && Math.max(Math.min(a[1],b[1]),Math.min(c[1],d[1]))<=Math.min(Math.max(a[1],b[1]),Math.max(c[1],d[1])))
                    throw new BusinessRuleException("Zone polygon edges must not intersect");
            }
        }
        if(Math.abs(area)<1e-8)throw new BusinessRuleException("Zone polygon must have nonzero area");
    }
    private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
}
