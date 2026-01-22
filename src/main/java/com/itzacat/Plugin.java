package com.itzacat;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Plugin extends JavaPlugin implements Listener
{
  private static final Logger LOGGER = Logger.getLogger("skiesfarming");

  private enum GrowthMode { VANILLA, FIXED }

  private final Map<UUID, FarmingProfile> profiles = new HashMap<>();
  private final Map<Material, Integer> cropXp = new EnumMap<>(Material.class);
  private final Map<Material, Integer> cropUnlock = new EnumMap<>(Material.class);

  private Leveling leveling;
  private boolean autoRefarmEnabled;
  private int autoRefarmDelayTicks;
  private boolean autoRefarmOnlyWhenNotHoe;
  private GrowthMode growthMode;
  private int fixedGrowTicks;

  private File dataFile;
  private FileConfiguration dataConfig;

  @Override
  public void onEnable()
  {
    saveDefaultConfig();
    loadSettings();
    loadLevels();
    loadData();
    getServer().getPluginManager().registerEvents(this, this);
    registerPlaceholders();
    LOGGER.info("skiesfarming enabled");
  }

  @Override
  public void onDisable()
  {
    saveData();
    LOGGER.info("skiesfarming disabled");
  }

  private void loadSettings()
  {
    reloadConfig();
    FileConfiguration cfg = getConfig();
    autoRefarmEnabled = cfg.getBoolean("auto-refarm.enabled", true);
    autoRefarmDelayTicks = Math.max(1, cfg.getInt("auto-refarm.delay-ticks", 100));
    autoRefarmOnlyWhenNotHoe = cfg.getBoolean("auto-refarm.only-when-not-using-hoe", true);
    growthMode = GrowthMode.valueOf(cfg.getString("growth.mode", "VANILLA").toUpperCase());
    fixedGrowTicks = Math.max(1, cfg.getInt("growth.fixed-grow-ticks", 1200));

    cropXp.clear();
    ConfigurationSection xpSection = cfg.getConfigurationSection("crop-xp");
    if (xpSection != null)
    {
      for (String key : xpSection.getKeys(false))
      {
        Material material = Material.matchMaterial(key);
        if (material != null)
        {
          cropXp.put(material, xpSection.getInt(key));
        }
      }
    }

    cropUnlock.clear();
    ConfigurationSection unlockSection = cfg.getConfigurationSection("unlock-levels");
    if (unlockSection != null)
    {
      for (String key : unlockSection.getKeys(false))
      {
        Material material = Material.matchMaterial(key);
        if (material != null)
        {
          cropUnlock.put(material, unlockSection.getInt(key));
        }
      }
    }
  }

  private void loadLevels()
  {
    File levelsFile = new File(getDataFolder(), "levels.yml");
    if (!levelsFile.exists())
    {
      saveResource("levels.yml", false);
    }

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(levelsFile);
    String growthMode = cfg.getString("growth-mode", "LINEAR").toUpperCase();
    int defaultPerLevel = Math.max(1, cfg.getInt("default-per-level", 300));
    Map<Integer, Integer> perLevel = new HashMap<>();

    if ("LINEAR".equals(growthMode))
    {
      int base = Math.max(1, cfg.getInt("linear.base", 100));
      int increment = Math.max(0, cfg.getInt("linear.increment", 50));
      int roundTo = Math.max(0, cfg.getInt("linear.round-to", 5));
      
      // Pre-calculate first 1000 levels (should be more than enough)
      for (int i = 1; i <= 1000; i++)
      {
        int xp = base + (i - 1) * increment;
        if (roundTo > 0)
        {
          xp = Math.round(xp / (float) roundTo) * roundTo;
        }
        perLevel.put(i, Math.max(1, xp));
      }
    }
    else if ("EXPONENTIAL".equals(growthMode))
    {
      int base = Math.max(1, cfg.getInt("exponential.base", 100));
      double multiplier = Math.max(1.0, cfg.getDouble("exponential.multiplier", 1.15));
      int roundTo = Math.max(0, cfg.getInt("exponential.round-to", 5));
      
      // Pre-calculate first 1000 levels
      for (int i = 1; i <= 1000; i++)
      {
        double xp = base * Math.pow(multiplier, i - 1);
        if (roundTo > 0)
        {
          xp = Math.round(xp / roundTo) * roundTo;
        }
        perLevel.put(i, Math.max(1, (int) xp));
      }
    }
    else if ("CUSTOM".equals(growthMode))
    {
      ConfigurationSection section = cfg.getConfigurationSection("levels");
      if (section != null)
      {
        for (String key : section.getKeys(false))
        {
          try
          {
            int level = Integer.parseInt(key);
            int cost = Math.max(1, section.getInt(key));
            perLevel.put(level, cost);
          }
          catch (NumberFormatException ignored)
          {
            getLogger().warning("Invalid level key in levels.yml: " + key);
          }
        }
      }
    }
    else
    {
      getLogger().warning("Unknown growth-mode '" + growthMode + "', using default-per-level fallback");
    }

    leveling = new Leveling(perLevel, defaultPerLevel);
    getLogger().info("Loaded leveling system with growth-mode: " + growthMode);
  }

  private void loadData()
  {
    dataFile = new File(getDataFolder(), "players.yml");
    if (!dataFile.exists())
    {
      try
      {
        getDataFolder().mkdirs();
        dataFile.createNewFile();
      }
      catch (IOException e)
      {
        getLogger().severe("Could not create players.yml: " + e.getMessage());
      }
    }

    dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    ConfigurationSection players = dataConfig.getConfigurationSection("players");
    if (players != null)
    {
      for (String key : players.getKeys(false))
      {
        UUID uuid = UUID.fromString(key);
        int xp = players.getInt(key + ".xp", 0);
        String name = players.getString(key + ".name", "");
        profiles.put(uuid, new FarmingProfile(name, xp));
      }
    }
  }

  private void saveData()
  {
    YamlConfiguration config = new YamlConfiguration();
    ConfigurationSection players = config.createSection("players");
    for (Map.Entry<UUID, FarmingProfile> entry : profiles.entrySet())
    {
      String key = entry.getKey().toString();
      FarmingProfile profile = entry.getValue();
      players.set(key + ".name", profile.getLastKnownName());
      players.set(key + ".xp", profile.getXp());
    }

    try
    {
      config.save(dataFile);
    }
    catch (IOException e)
    {
      getLogger().severe("Could not save players.yml: " + e.getMessage());
    }
  }

  private FarmingProfile getProfile(UUID uuid)
  {
    return profiles.computeIfAbsent(uuid, id -> new FarmingProfile("", 0));
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event)
  {
    FarmingProfile profile = getProfile(event.getPlayer().getUniqueId());
    profile.setLastKnownName(event.getPlayer().getName());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event)
  {
    FarmingProfile profile = getProfile(event.getPlayer().getUniqueId());
    profile.setLastKnownName(event.getPlayer().getName());
    saveData();
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event)
  {
    Material material = event.getBlockPlaced().getType();
    if (!isTrackedCrop(material))
    {
      return;
    }

    Player player = event.getPlayer();
    FarmingProfile profile = getProfile(player.getUniqueId());
    profile.setLastKnownName(player.getName());

    int level = profile.getLevel(leveling);
    int required = cropUnlock.getOrDefault(material, 0);
    if (level < required)
    {
      event.setCancelled(true);
      player.sendMessage("§cYou need farming level " + required + " to plant " + formatMaterial(material) + ".");
      return;
    }

    if (growthMode == GrowthMode.FIXED && isAgeable(material))
    {
      scheduleFixedGrowth(event.getBlockPlaced().getLocation(), material);
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event)
  {
    if (event.isCancelled())
    {
      return;
    }

    Block block = event.getBlock();
    Material material = block.getType();
    if (!isTrackedCrop(material))
    {
      return;
    }

    Player player = event.getPlayer();
    FarmingProfile profile = getProfile(player.getUniqueId());
    profile.setLastKnownName(player.getName());

    boolean mature = isMature(block);
    ItemStack tool = player.getInventory().getItemInMainHand();
    if (!mature && !isHoe(tool))
    {
      event.setCancelled(true);
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§cUse a hoe to break immature crops."));
      return;
    }

    if (!mature)
    {
      return;
    }

    // Handle auto-pickup of drops
    Collection<ItemStack> drops = block.getDrops(tool);
    if (!drops.isEmpty())
    {
      event.setDropItems(false); // Prevent default drops
      PlayerInventory inventory = player.getInventory();
      
      for (ItemStack drop : drops)
      {
        HashMap<Integer, ItemStack> leftover = inventory.addItem(drop);
        // Drop items that didn't fit in inventory
        for (ItemStack overflow : leftover.values())
        {
          block.getWorld().dropItemNaturally(block.getLocation(), overflow);
        }
      }
    }

    int before = profile.getLevel(leveling);
    profile.addXp(cropXp.getOrDefault(material, 0));
    int after = profile.getLevel(leveling);

    if (after > before)
    {
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§aFarming level up! New level: " + after));
    }
    else
    {
      // Calculate progress to next level
      int currentLevelXp = leveling.xpForLevel(after);
      int nextLevelXp = leveling.xpForLevel(after + 1);
      int currentXp = profile.getXp();
      int xpIntoLevel = currentXp - currentLevelXp;
      int xpNeeded = nextLevelXp - currentLevelXp;
      
      // Prevent division by zero
      double percentage = (xpNeeded > 0) ? (double) xpIntoLevel / xpNeeded * 100.0 : 0.0;
      
      // Create progress bar
      String progressBar = createProgressBar(percentage, 10);
      
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
        String.format("§aLevel %d %s §7%.1f%% (%d/%d XP)", after, progressBar, percentage, xpIntoLevel, xpNeeded)));
    }

    if (autoRefarmEnabled && shouldAutoRefarm(player.getInventory().getItemInMainHand()))
    {
      scheduleReplant(block.getLocation(), material);
    }
  }

  private boolean shouldAutoRefarm(ItemStack tool)
  {
    if (!autoRefarmOnlyWhenNotHoe)
    {
      return true;
    }
    return !isHoe(tool);
  }

  private boolean isHoe(ItemStack tool)
  {
    return tool != null && tool.getType().name().endsWith("_HOE");
  }

  private boolean isTrackedCrop(Material material)
  {
    return cropXp.containsKey(material) || cropUnlock.containsKey(material);
  }

  private boolean isAgeable(Material material)
  {
    return material.isBlock() && material.createBlockData() instanceof Ageable;
  }

  private boolean isMature(Block block)
  {
    if (!(block.getBlockData() instanceof Ageable))
    {
      return true;
    }
    Ageable ageable = (Ageable) block.getBlockData();
    return ageable.getAge() >= ageable.getMaximumAge();
  }

  private void scheduleReplant(final org.bukkit.Location location, final Material cropType)
  {
    new BukkitRunnable()
    {
      @Override
      public void run()
      {
        Block block = location.getBlock();
        Material below = block.getRelative(0, -1, 0).getType();
        boolean soilOk = below == Material.FARMLAND || (cropType == Material.NETHER_WART && below == Material.SOUL_SAND);
        if (!soilOk || block.getType() != Material.AIR)
        {
          return;
        }

        block.setType(cropType, false);
        if (block.getBlockData() instanceof Ageable)
        {
          Ageable ageable = (Ageable) block.getBlockData();
          ageable.setAge(0);
          block.setBlockData(ageable, false);
        }

        if (growthMode == GrowthMode.FIXED && isAgeable(cropType))
        {
          scheduleFixedGrowth(location, cropType);
        }
      }
    }.runTaskLater(this, autoRefarmDelayTicks);
  }

  private void scheduleFixedGrowth(final org.bukkit.Location location, final Material cropType)
  {
    new BukkitRunnable()
    {
      @Override
      public void run()
      {
        Block block = location.getBlock();
        if (block.getType() != cropType)
        {
          return;
        }
        if (!(block.getBlockData() instanceof Ageable))
        {
          return;
        }
        Ageable ageable = (Ageable) block.getBlockData();
        ageable.setAge(ageable.getMaximumAge());
        block.setBlockData(ageable, false);
      }
    }.runTaskLater(this, fixedGrowTicks);
  }

  private String formatMaterial(Material material)
  {
    return material.name().toLowerCase().replace('_', ' ');
  }

  private String createProgressBar(double percentage, int length)
  {
    int filled = (int) Math.round(percentage / 100.0 * length);
    filled = Math.max(0, Math.min(length, filled));
    
    StringBuilder bar = new StringBuilder("§7[");
    for (int i = 0; i < length; i++)
    {
      if (i < filled)
      {
        bar.append("§a▪");
      }
      else
      {
        bar.append("§8▪");
      }
    }
    bar.append("§7]");
    return bar.toString();
  }

  private void registerPlaceholders()
  {
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
    {
      new FarmingPlaceholders(this).register();
      getLogger().info("Registered PlaceholderAPI placeholders for skiesfarming.");
    }
  }

  private List<LeaderboardEntry> getTop(int limit)
  {
    Comparator<LeaderboardEntry> comparator = Comparator
      .comparingInt(LeaderboardEntry::level).reversed()
      .thenComparing(Comparator.comparingInt((LeaderboardEntry e) -> e.profile().getXp()).reversed());

    return profiles.entrySet().stream()
      .map(entry -> new LeaderboardEntry(entry.getKey(), entry.getValue(), entry.getValue().getLevel(leveling)))
      .sorted(comparator)
      .limit(limit)
      .collect(Collectors.toList());
  }

  private static final class LeaderboardEntry
  {
    private final UUID uuid;
    private final FarmingProfile profile;
    private final int level;

    LeaderboardEntry(UUID uuid, FarmingProfile profile, int level)
    {
      this.uuid = uuid;
      this.profile = profile;
      this.level = level;
    }

    UUID uuid()
    {
      return uuid;
    }

    FarmingProfile profile()
    {
      return profile;
    }

    int level()
    {
      return level;
    }
  }

  private static final class Leveling
  {
    private final Map<Integer, Integer> perLevelXp;
    private final int defaultPerLevel;

    Leveling(Map<Integer, Integer> perLevelXp, int defaultPerLevel)
    {
      this.perLevelXp = perLevelXp;
      this.defaultPerLevel = defaultPerLevel;
    }

    int levelForXp(int xp)
    {
      int level = 0;
      int remaining = Math.max(0, xp);
      int nextLevel = 1;

      while (true)
      {
        int cost = perLevelXp.getOrDefault(nextLevel, defaultPerLevel);
        if (cost <= 0)
        {
          break;
        }
        if (remaining < cost)
        {
          break;
        }
        remaining -= cost;
        level++;
        nextLevel++;
      }
      return level;
    }

    int xpForLevel(int level)
    {
      int totalXp = 0;
      for (int i = 1; i <= level; i++)
      {
        int cost = perLevelXp.getOrDefault(i, defaultPerLevel);
        if (cost <= 0)
        {
          break;
        }
        totalXp += cost;
      }
      return totalXp;
    }
  }

  private static final class FarmingProfile
  {
    private String lastKnownName;
    private int xp;

    FarmingProfile(String lastKnownName, int xp)
    {
      this.lastKnownName = lastKnownName;
      this.xp = xp;
    }

    int getXp()
    {
      return xp;
    }

    void addXp(int amount)
    {
      xp = Math.max(0, xp + Math.max(0, amount));
    }

    int getLevel(Leveling leveling)
    {
      return leveling == null ? 0 : leveling.levelForXp(xp);
    }

    String getLastKnownName()
    {
      return lastKnownName == null ? "" : lastKnownName;
    }

    void setLastKnownName(String name)
    {
      this.lastKnownName = name;
    }
  }

  private static final class FarmingPlaceholders extends PlaceholderExpansion
  {
    private final Plugin plugin;

    FarmingPlaceholders(Plugin plugin)
    {
      this.plugin = plugin;
    }

    @Override
    public String getIdentifier()
    {
      return "skiesfarming";
    }

    @Override
    public String getAuthor()
    {
      return "itzacat";
    }

    @Override
    public String getVersion()
    {
      return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist()
    {
      return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params)
    {
      if (player != null && player.getUniqueId() != null && "level".equalsIgnoreCase(params))
      {
        return String.valueOf(plugin.getProfile(player.getUniqueId()).getLevel(plugin.leveling));
      }

      if (params != null && params.toLowerCase().startsWith("top_"))
      {
        try
        {
          int index = Integer.parseInt(params.substring(4)) - 1;
          List<LeaderboardEntry> top = plugin.getTop(10);
          if (index >= 0 && index < top.size())
          {
            LeaderboardEntry entry = top.get(index);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.uuid());
            String name = offline.getName() != null ? offline.getName() : entry.profile().getLastKnownName();
            return name + ":" + entry.level();
          }
        }
        catch (NumberFormatException ignored)
        {
          return "";
        }
      }

      return "";
    }
  }
}
