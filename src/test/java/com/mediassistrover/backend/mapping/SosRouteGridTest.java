package com.mediassistrover.backend.mapping;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SosRouteGridTest {
    @Test void blockedDestinationUsesAFreeCellOnTheObservedSideWithinRange() {
        var grid = new SosRouteGrid(7, 1, .25, 0, 0, 0,
                new boolean[]{true, true, true, false, true, true, true});
        var target = new SosRouteGrid.Point(.90, .125);
        var observation = new SosRouteGrid.Point(.375, .125);
        var otherSide = new SosRouteGrid.Point(1.375, .125);

        assertTrue(grid.hasClearView(otherSide, target));
        assertTrue(grid.shortestToVisibleNeighbor(List.of(otherSide), target, observation, .75).isEmpty());
        assertTrue(grid.shortestToVisibleNeighbor(List.of(observation), target, observation, .25).isEmpty());
        var route = grid.shortestToVisibleNeighbor(List.of(observation), target, observation, .75).orElseThrow();
        assertEquals(new SosRouteGrid.Point(.625, .125), route.points().getLast());
        assertTrue(route.points().stream().allMatch(grid::walkable));
    }

    @Test void shortestChoosesNearestOriginAndAvoidsBlockedCorners() {
        var grid = new SosRouteGrid(3,3,.1,0,0,0,
                new boolean[]{true,false,true, true,true,true, true,true,true});
        var route = grid.shortest(List.of(new SosRouteGrid.Point(.05,.05),
                new SosRouteGrid.Point(.25,.25)),new SosRouteGrid.Point(.25,.05)).orElseThrow();
        assertEquals(.2,route.distance(),1e-9);
        assertEquals(new SosRouteGrid.Point(.25,.25),route.points().getFirst());
        var detour = grid.shortest(List.of(new SosRouteGrid.Point(.05,.05)),
                new SosRouteGrid.Point(.25,.05)).orElseThrow();
        assertEquals(.4,detour.distance(),1e-9);
    }

    @Test void disconnectedCellsHaveNoPathAndCoordinatesRespectRotation() {
        var blocked = new SosRouteGrid(2,2,1,0,0,0,new boolean[]{true,false,false,true});
        assertTrue(blocked.shortest(List.of(new SosRouteGrid.Point(.5,.5)),
                new SosRouteGrid.Point(1.5,1.5)).isEmpty());
        var rotated = new SosRouteGrid(2,1,.1,2,3,Math.PI/2,new boolean[]{true,true});
        var route = rotated.shortest(List.of(new SosRouteGrid.Point(1.95,3.05)),
                new SosRouteGrid.Point(1.95,3.15)).orElseThrow();
        assertEquals(.1,route.distance(),1e-9);
        assertFalse(rotated.walkable(new SosRouteGrid.Point(Double.NaN,0)));
    }
}
