#!/bin/bash
# Scripted dedicated-server regression run for ramp units: Dive, Destroy, low-fuel loiter dive, gunship Return,
# recon Loop and a low-fuel recon return. Summons units around the dev world's spawn, probes their positions with
# /tp every 4 s, then prints a summary. Meant for a flat dev world (run/world); run from the repo root:
#   tools/server_smoke_test.sh
# Expect: dive/destroy/lowfuel disappear (craters near their targets), "ret" and "lowloop" drop as items near spawn,
# "loop" keeps flying its two waypoints, "crashg" and "crashu" (Medium speed into a stone wall) explode without
# dropping an item. Server log: build/smoke/server.log
set -u
OUT=build/smoke
mkdir -p "$OUT"
LOG=$OUT/server.log
FIFO=$OUT/fifo
read -r SX SY SZ < <(python3 - <<'EOF'
import gzip, struct
d = gzip.open('run/world/level.dat').read()
vals = []
for k in (b'SpawnX', b'SpawnY', b'SpawnZ'):
    i = d.find(k) + len(k)
    vals.append(struct.unpack('>i', d[i:i+4])[0])
print(*vals)
EOF
)
GROUND=$SY
UP=$((SY + 31))
LAUNCH=$((SY + 1))
rm -f "$FIFO"; mkfifo "$FIFO"
# Registry entries the world knows but the mod set no longer has (renamed items, dev-only example content) would
# stop the server at FML's "/fml confirm" prompt, which doesn't read the console that early: preselect the answer
(JAVA_TOOL_OPTIONS=-Dfml.queryResult=confirm ./gradlew runServer < "$FIFO" > "$LOG" 2>&1 &)
exec 3>"$FIFO"
say() { echo "$1" >&3; }
for _ in $(seq 1 120); do grep -q 'Done (' "$LOG" && break; sleep 2; done
UNIT='combat_drones:ramp_unit'
summon() { # name x z type fuel heading route-nbt
    say "summon $UNIT $2 $UP $3 {Type:\"$4\",Fuel:$5d,Health:100f,LaunchY:${LAUNCH}d,HomeX:${SX}d,HomeZ:${SZ}d,Speed:0.8d,Heading:$6d,CustomName:\"$1\",Route:$7}"
}
wp() { echo "{X:$1d,Z:$2d,Alt:30d,Speed:\"${3:-FAST}\"}"; }
WALL_X=$((SX + 30))
say "kill @e[type=$UNIT]"; say "kill @e[type=item]"; sleep 1
# A tall stone wall across the crash cases' path (east of spawn, south side), removed again at the end
say "fill $WALL_X $SY $((SZ + 50)) $((WALL_X + 1)) $((SY + 80)) $((SZ + 100)) stone"
summon dive    "$SX" $((SZ - 20)) loitering_light 3600 180 "{Terminal:\"DIVE\",LoiterRadius:24d,Waypoints:[$(wp "$SX" $((SZ - 150)))]}"
summon ret     $((SX + 20)) "$SZ" strike_plane 6000 -90 "{Terminal:\"RETURN\",LoiterRadius:24d,Waypoints:[$(wp $((SX + 200)) "$SZ")]}"
summon lowfuel "$SX" $((SZ + 20)) loitering_light 300 0 "{Terminal:\"LOITER\",LoiterRadius:24d,Waypoints:[$(wp "$SX" $((SZ + 100)))]}"
summon destroy $((SX - 20)) "$SZ" loitering_light 3600 90 "{Terminal:\"DESTROY\",LoiterRadius:24d,Target:{X:$((SX - 120))d,Y:${GROUND}d,Z:$((SZ - 60))d},Waypoints:[$(wp $((SX - 100)) "$SZ")]}"
summon loop    $((SX + 20)) $((SZ - 40)) recon_uav 24000 180 "{Terminal:\"LOOP\",LoiterRadius:24d,Waypoints:[$(wp "$SX" $((SZ - 100))),$(wp $((SX + 80)) $((SZ - 100)))]}"
summon lowloop $((SX + 20)) $((SZ + 40)) recon_uav 2000 0 "{Terminal:\"LOOP\",LoiterRadius:24d,Waypoints:[$(wp "$SX" $((SZ + 100))),$(wp $((SX + 80)) $((SZ + 100)))]}"
summon crashg  $((SX - 30)) $((SZ + 60)) attack_plane 6000 -90 "{Terminal:\"LOITER\",LoiterRadius:24d,Waypoints:[$(wp $((SX + 160)) $((SZ + 60)) MEDIUM)]}"
summon crashu  $((SX - 30)) $((SZ + 90)) recon_uav 24000 -90 "{Terminal:\"LOITER\",LoiterRadius:24d,Waypoints:[$(wp $((SX + 160)) $((SZ + 90)) MEDIUM)]}"
for _ in $(seq 1 25); do sleep 4; say "tp @e[type=$UNIT] ~ ~ ~"; done
say "fill $WALL_X $SY $((SZ + 50)) $((WALL_X + 1)) $((SY + 80)) $((SZ + 100)) air"
say "tp @e[type=item] ~ ~ ~"
say "save-all"
say "stop"
sleep 10
exec 3>&-
for name in dive destroy lowfuel ret loop lowloop crashg crashu; do
    printf '%-8s %2s probes, last: %s\n' "$name" "$(grep -c "Teleported $name " "$LOG")" \
        "$(grep "Teleported $name " "$LOG" | tail -1 | sed -E 's/.*to //' | cut -c1-48)"
done
echo "Dropped units:"; grep -E 'Teleported item\.item\.combat_drones' "$LOG" | sed -E 's/.*Teleported /  /'
grep -iE 'exception' "$LOG" | head -5
