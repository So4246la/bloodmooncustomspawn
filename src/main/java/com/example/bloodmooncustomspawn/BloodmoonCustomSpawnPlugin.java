package com.example.bloodmooncustomspawn;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.api.mobs.MythicMob;

import me.mrgeneralq.bloodmoon.api.BloodmoonAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;



public class BloodmoonCustomSpawnPlugin extends JavaPlugin {

private final Set<UUID> spawnedMythicEntities = new HashSet<>();

    private FileConfiguration config;
    private Random random = new Random();
    private final Map<String, Integer> mobWeights = new HashMap<>();
    private final Set<UUID> spawnedEntities = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        if (Bukkit.getPluginManager().getPlugin("bloodmoon-advanced") == null || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().warning("Required plugins (BloodmoonAdvanced or MythicMobs) not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

  if (!config.isConfigurationSection("enabled-mythicmobs")) {
        ConfigurationSection mythicSection = config.createSection("enabled-mythicmobs");
        mythicSection.set("CustomZombie", 5);
        mythicSection.set("GREENSLIME", 5);
        saveConfig();
    }
	syncMobConfig();

        loadMobWeights();

        int spawnInterval = config.getInt("spawn-interval-seconds", 10) * 20;

        new BukkitRunnable() {
            @Override
            public void run() {
                handleBloodmoonSpawning();
            }
        }.runTaskTimer(this, spawnInterval, spawnInterval);

        new BukkitRunnable() {
            private boolean previouslyRunning = false;

            @Override
            public void run() {
                boolean currentlyRunning = false;
                for (World world : Bukkit.getWorlds()) {
                    currentlyRunning |= BloodmoonAPI.bloodmoonIsRunning(world);
                }

                if (!previouslyRunning && currentlyRunning) {
                    getLogger().info("[Bloodmoon] Started!");
                }

                if (previouslyRunning && !currentlyRunning) {
                    getLogger().info("[Bloodmoon] Ended! Cleaning up mobs...");
                    handleBloodmoonCleanup();
                }

                previouslyRunning = currentlyRunning;
            }
        }.runTaskTimer(this, 20L, 20L);

        saveDefaultConfig();
        config = getConfig();
        loadMobWeights();

        getLogger().info("BloodmoonCustomSpawnPlugin enabled and linked with BloodmoonAdvanced and MythicMobs!");
    }

private void syncMobConfig() {
    File mobsFile = new File(getDataFolder().getParentFile(), "bloodmoon-advanced/mobs.yml");
    if (!mobsFile.exists()) {
        getLogger().warning("mobs.yml file not found at " + mobsFile.getAbsolutePath());
        return;
    }

    YamlConfiguration bmMobsConfig = YamlConfiguration.loadConfiguration(mobsFile);
    ConfigurationSection mobsListSection = bmMobsConfig.getConfigurationSection("mobs");
    if (mobsListSection == null) {
        getLogger().warning("Section 'mobs' not found in mobs.yml");
        return;
    }

    Set<String> currentBmMobs = mobsListSection.getKeys(false);

    // enabled-bloodmoon-mobsのみ同期
    ConfigurationSection bmSection = config.getConfigurationSection("enabled-bloodmoon-mobs");
    if (bmSection == null) {
        bmSection = config.createSection("enabled-bloodmoon-mobs");
    }

    syncSectionWithCurrentMobs(bmSection, currentBmMobs);

    if (!config.isConfigurationSection("enabled-mythicmobs")) {
        ConfigurationSection mythicSection = config.createSection("enabled-mythicmobs");
        mythicSection.set("CustomZombie", 5);
        mythicSection.set("GREENSLIME", 5);
    }

    saveConfig();
}

private void syncSectionWithCurrentMobs(ConfigurationSection section, Set<String> currentMobs) {
    Set<String> keysToRemove = new HashSet<>();
    for (String key : section.getKeys(false)) {
        if (!currentMobs.contains(key)) {
            keysToRemove.add(key);
        }
    }
    keysToRemove.forEach(key -> section.set(key, null));

    for (String mob : currentMobs) {
        if (!section.contains(mob)) {
            section.set(mob, 1);
        }
    }
}


private void loadMobWeights() {
    loadWeights("enabled-mobs");
    loadWeights("enabled-bloodmoon-mobs");
    loadWeights("enabled-mythicmobs");
}

private void loadWeights(String sectionName) {
    ConfigurationSection section = config.getConfigurationSection(sectionName);
    if (section != null) {
        for (String mob : section.getKeys(false)) {
            mobWeights.put(mob.toUpperCase(), section.getInt(mob));
        }
    }
}


    private void handleBloodmoonSpawning() {
        for (World world : Bukkit.getWorlds()) {
            if (!BloodmoonAPI.bloodmoonIsRunning(world)) continue;

            int minRadius = config.getInt("spawn-radius.min", 20);
            int maxRadius = config.getInt("spawn-radius.max", 80);
            int mobsPerPlayer = config.getInt("mobs-per-player", 3);

            for (Player player : world.getPlayers()) {
                if (player.getGameMode() != GameMode.SURVIVAL) continue;

                for (int i = 0; i < mobsPerPlayer; i++) {
                    String mobName = getRandomMobByWeight();
                    Location spawnLoc = getRandomLocationAroundPlayer(player.getLocation(), minRadius, maxRadius);

if (config.getConfigurationSection("enabled-mythicmobs") != null &&
    config.getConfigurationSection("enabled-mythicmobs").contains(mobName)) {

    try {
        Entity spawned = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobName, spawnLoc);
        if (spawned != null) {
            spawnedMythicEntities.add(spawned.getUniqueId());
        } else {
            getLogger().warning("Failed to spawn MythicMob: " + mobName);
        }
    } catch (io.lumine.mythic.api.exceptions.InvalidMobTypeException e) {
        getLogger().warning("Invalid MythicMob type: " + mobName);
    }

}
 else if (config.getConfigurationSection("enabled-bloodmoon-mobs") != null &&
           config.getConfigurationSection("enabled-bloodmoon-mobs").contains(mobName)) {
    BloodmoonAPI.spawnBloodmoonMob(mobName, spawnLoc);
} else {
    try {
        EntityType type = EntityType.valueOf(mobName);
        Entity entity = world.spawnEntity(spawnLoc, type);
        entity.addScoreboardTag("BloodmoonVanilla");
        spawnedEntities.add(entity.getUniqueId());
    } catch (IllegalArgumentException e) {
        getLogger().warning("Invalid mob name: " + mobName);
    }
}

                }
            }
        }
    }

    private String getRandomMobByWeight() {
        int totalWeight = mobWeights.values().stream().mapToInt(Integer::intValue).sum();
        int randomValue = random.nextInt(totalWeight);

        for (Map.Entry<String, Integer> entry : mobWeights.entrySet()) {
            randomValue -= entry.getValue();
            if (randomValue < 0) return entry.getKey();
        }

        return "ZOMBIE";
    }

    private Location getRandomLocationAroundPlayer(Location center, int minRadius, int maxRadius) {
        double radius = minRadius + (maxRadius - minRadius) * random.nextDouble();
        double angle = random.nextDouble() * Math.PI * 2;

        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        World world = center.getWorld();

        int y = world.getHighestBlockYAt((int) x, (int) z) + 2;

        return new Location(world, x, y, z);
    }

private void handleBloodmoonCleanup() {
    for (UUID uuid : spawnedEntities) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null && !entity.isDead()) {
            entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, entity.getLocation(), 20);
            entity.remove();
        }
    }
    spawnedEntities.clear();

    for (UUID uuid : spawnedMythicEntities) {
        Entity mythicEntity = Bukkit.getEntity(uuid);
        if (mythicEntity != null && !mythicEntity.isDead()) {
            mythicEntity.getWorld().spawnParticle(Particle.LARGE_SMOKE, mythicEntity.getLocation(), 20);
            mythicEntity.remove();
        }
    }
    spawnedMythicEntities.clear();
}
}
