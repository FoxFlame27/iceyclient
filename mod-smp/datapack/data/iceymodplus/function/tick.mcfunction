# Auto-buff tick. Runs every server tick. Effects are refreshed for 5
# seconds so they stay applied while you hold the rank — drop off and
# they fade naturally.
#
# Datapack version uses simple count-threshold tiers (no leaderboard
# ranking) because comparing scores across players in pure mcfunction is
# painful. The mod jar has true rank-based + steal-on-kill semantics; for
# vanilla servers this is the next best thing.

# --- Mining → Haste tiers ---
execute as @a[scores={ip_mining=1..9}] run effect give @s minecraft:haste 5 0 true
execute as @a[scores={ip_mining=10..49}] run effect give @s minecraft:haste 5 1 true
execute as @a[scores={ip_mining=50..199}] run effect give @s minecraft:haste 5 2 true
execute as @a[scores={ip_mining=200..999}] run effect give @s minecraft:haste 5 3 true
execute as @a[scores={ip_mining=1000..}] run effect give @s minecraft:haste 5 4 true

# --- PvP → Strength tiers ---
execute as @a[scores={ip_pvp=1..2}] run effect give @s minecraft:strength 5 0 true
execute as @a[scores={ip_pvp=3..9}] run effect give @s minecraft:strength 5 1 true
execute as @a[scores={ip_pvp=10..29}] run effect give @s minecraft:strength 5 2 true
execute as @a[scores={ip_pvp=30..99}] run effect give @s minecraft:strength 5 3 true
execute as @a[scores={ip_pvp=100..}] run effect give @s minecraft:strength 5 4 true

# --- Mob Kills → Resistance tiers ---
execute as @a[scores={ip_mobkills=20..99}] run effect give @s minecraft:resistance 5 0 true
execute as @a[scores={ip_mobkills=100..499}] run effect give @s minecraft:resistance 5 1 true
execute as @a[scores={ip_mobkills=500..}] run effect give @s minecraft:resistance 5 2 true

# --- Fish Caught → Luck ---
execute as @a[scores={ip_fish=5..29}] run effect give @s minecraft:luck 5 0 true
execute as @a[scores={ip_fish=30..99}] run effect give @s minecraft:luck 5 1 true
execute as @a[scores={ip_fish=100..}] run effect give @s minecraft:luck 5 2 true

# --- Jumps → Jump Boost ---
execute as @a[scores={ip_jumps=100..999}] run effect give @s minecraft:jump_boost 5 0 true
execute as @a[scores={ip_jumps=1000..}] run effect give @s minecraft:jump_boost 5 1 true

# --- Walk (cm) → Speed tiers (100,000 cm = 1 km) ---
execute as @a[scores={ip_walk=100000..499999}] run effect give @s minecraft:speed 5 0 true
execute as @a[scores={ip_walk=500000..}] run effect give @s minecraft:speed 5 1 true

# --- Sneak time (ticks; 1200 = 1 min) → Slow Falling ---
execute as @a[scores={ip_sneak=1200..11999}] run effect give @s minecraft:slow_falling 5 0 true
execute as @a[scores={ip_sneak=12000..}] run effect give @s minecraft:slow_falling 5 1 true

# --- Playtime (ticks; 24000 = 20 min real-time-ish) → Saturation ---
execute as @a[scores={ip_playtime=72000..}] run effect give @s minecraft:saturation 5 0 true

# --- Deaths → Regeneration (comeback mechanic) ---
execute as @a[scores={ip_deaths=5..19}] run effect give @s minecraft:regeneration 5 0 true
execute as @a[scores={ip_deaths=20..}] run effect give @s minecraft:regeneration 5 1 true
