execute store result score #time ark_long_days run time of minecraft:overworld query minecraft:day
execute if score #time ark_long_days matches 0..11999 unless score #phase ark_long_days matches 0 run function ark_long_days:day
execute if score #time ark_long_days matches 12000..23999 unless score #phase ark_long_days matches 1 run function ark_long_days:night
schedule function ark_long_days:tick 1t replace
