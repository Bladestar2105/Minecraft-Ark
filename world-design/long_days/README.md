# Longer daylight

This Minecraft Java 26.3 datapack provides about 30 minutes of daylight and 10
minutes of night at the normal 20 game ticks per second. It changes the native
Overworld clock rate; game simulation, crop growth and tick rate are unchanged.
Moon and villager schedules continue to follow the Overworld clock. Sleeping,
clock pause/resume and `minecraft:advance_time` retain their normal behavior.

The fresh installer copies the six runtime files into
`data/ark/datapacks/ark_long_days/`. Existing packs are preserved and cause a
preflight error. For an existing stopped server, copy this directory to that
location deliberately, keeping `pack.mcmeta` directly inside `ark_long_days`.
A normal server start or `reload` activates its single scheduled loop.

To remove it, run these console commands in order before deleting its directory:

```text
schedule clear ark_long_days:tick
time of minecraft:overworld rate 1
datapack disable "file/ark_long_days"
scoreboard objectives remove ark_long_days
```

If the rate was already one, that command can report no change. No world data
or player state needs to be removed.
