package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RouteCodecTest {
    private static final Route ROUTE = new Route(Arrays.asList(
            new Route.Waypoint(10, -20, 80, Route.Speed.SLOW),
            new Route.Waypoint(30, 40, 90, Route.Speed.MEDIUM, 0),
            new Route.Waypoint(50, 60, 100, Route.Speed.FAST, 96)), Route.TerminalAction.RETURN, 70);

    private static void assertSame(Route route) {
        assertEquals(3, route.size());
        assertEquals(Route.TerminalAction.RETURN, route.getTerminal());
        assertEquals(70, route.getLoiterRadius(), 1e-9);
        assertFalse(route.get(0).isDrop());
        assertEquals(Route.Speed.SLOW, route.get(0).speed);
        assertEquals(0, route.get(1).dropLength, 1e-9);
        assertEquals(96, route.get(2).dropLength, 1e-9);
        assertEquals(60, route.get(2).z, 1e-9);
    }

    @Test
    void packetRoundTripKeepsDropPoints() {
        ByteBuf buf = Unpooled.buffer();
        RouteCodec.write(buf, ROUTE);
        assertSame(RouteCodec.read(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void nbtRoundTripKeepsDropPoints() {
        assertSame(RouteCodec.fromNbt(RouteCodec.toNbt(ROUTE), Route.TerminalAction.LOITER));
    }
}
