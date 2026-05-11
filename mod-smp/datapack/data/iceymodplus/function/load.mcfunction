# iceymod+ datapack — initialize all scoreboard objectives.
# Builtin objectives auto-track MC's per-player statistics; we just bind
# nice display names to them and pick one for the sidebar.

scoreboard objectives add ip_mining minecraft.mined:minecraft.diamond_ore "§b⛏ Mining"
scoreboard objectives add ip_pvp minecraft.killed:minecraft.player "§c⚔ PvP"
scoreboard objectives add ip_playtime minecraft.custom:minecraft.play_time "§e⏱ Playtime"
scoreboard objectives add ip_mobkills minecraft.custom:minecraft.mob_kills "§4☠ Mob Kills"
scoreboard objectives add ip_fish minecraft.custom:minecraft.fish_caught "§b🎣 Fishing"
scoreboard objectives add ip_jumps minecraft.custom:minecraft.jump "§e⤴ Jumps"
scoreboard objectives add ip_dmgdealt minecraft.custom:minecraft.damage_dealt "§c⚔ DmgDealt"
scoreboard objectives add ip_dmgtaken minecraft.custom:minecraft.damage_taken "§4❤ DmgTaken"
scoreboard objectives add ip_deaths minecraft.custom:minecraft.deaths "§7☠ Deaths"
scoreboard objectives add ip_walk minecraft.custom:minecraft.walk_one_cm "§a👟 Walk(cm)"
scoreboard objectives add ip_sneak minecraft.custom:minecraft.sneak_time "§8👤 Sneak"

# PvP leaderboard on the sidebar by default. Players can run
# `/scoreboard objectives setdisplay sidebar ip_mining` etc. to switch.
scoreboard objectives setdisplay sidebar ip_pvp

tellraw @a {"text":"[iceymod+] datapack loaded — auto-buffs from your own stats","color":"aqua","bold":true}
tellraw @a {"text":"  /scoreboard objectives setdisplay sidebar <obj>  to change sidebar","color":"gray"}
