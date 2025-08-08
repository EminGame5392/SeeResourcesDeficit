package ru.gdev.seeresourcesdeficit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SeeResourcesDeficit extends JavaPlugin implements Listener, TabCompleter {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private FileConfiguration config;
    private final Map<String, ItemData> deficitItems = new HashMap<>();
    private final Map<Material, String> materialToItemId = new HashMap<>();
    private final Map<String, Boolean> forcedDeficit = new HashMap<>();
    private final Map<Player, Long> lastEffectTimes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("seeresourcesdeficit").setTabCompleter(this);
    }

    private String colorize(String message) {
        if (message == null) return "";
        message = Bukkit.getServer().getBukkitVersion().contains("1.16") ?
                translateHexColorCodes(message) :
                message.replace('&', '§');
        return message.replace('&', '§');
    }

    private String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "§x§" + matcher.group(1).replaceAll("(.)", "§$1"));
        }
        return matcher.appendTail(buffer).toString();
    }

    private void loadConfig() {
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        ConfigurationSection itemsSection = config.getConfigurationSection("deficit_items");
        if (itemsSection == null) return;

        deficitItems.clear();
        materialToItemId.clear();

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
            if (itemSection == null) continue;

            Material material = Material.matchMaterial(itemSection.getString("material"));
            if (material == null) continue;

            int limit = itemSection.getInt("limit");
            ConfigurationSection limitEndSection = itemSection.getConfigurationSection("limit_end");
            if (limitEndSection == null) continue;

            ItemData itemData = new ItemData(material, limit, limitEndSection);
            deficitItems.put(itemId, itemData);
            materialToItemId.put(material, itemId);
            forcedDeficit.putIfAbsent(itemId, false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seeresourcesdeficit.admin")) {
            sender.sendMessage(colorize("&cУ вас нету прав!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit reload|enable|disable"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                loadConfig();
                sender.sendMessage(colorize("&aКонфигурация перезагружена!"));
                return true;
            case "enable":
                if (args.length < 2) {
                    sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit enable [предмет]"));
                    return true;
                }
                if (!deficitItems.containsKey(args[1])) {
                    sender.sendMessage(colorize("&cПредмет не найден в конфигурации!"));
                    return true;
                }
                forcedDeficit.put(args[1], true);
                sender.sendMessage(colorize("&aДефицит для предмета &e" + args[1] + " &aпринудительно включен!"));
                return true;
            case "disable":
                if (args.length < 2) {
                    sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit disable [предмет]"));
                    return true;
                }
                if (!deficitItems.containsKey(args[1])) {
                    sender.sendMessage(colorize("&cПредмет не найден в конфигурации!"));
                    return true;
                }
                forcedDeficit.put(args[1], false);
                sender.sendMessage(colorize("&aДефицит для предмета &e" + args[1] + " &aпринудительно выключен!"));
                return true;
            default:
                sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit reload|enable|disable"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("seeresourcesdeficit.admin")) return completions;

        if (args.length == 1) {
            completions.add("reload");
            completions.add("enable");
            completions.add("disable");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            completions.addAll(deficitItems.keySet());
        }
        return completions;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        if (!materialToItemId.containsKey(material)) return;

        String itemId = materialToItemId.get(material);
        ItemData itemData = deficitItems.get(itemId);
        if (itemData == null) return;

        if (forcedDeficit.getOrDefault(itemId, false) || itemData.getCount() >= itemData.getLimit()) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            handleDeficit(event.getPlayer(), itemData);
        } else {
            itemData.incrementCount();
        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        Material material = event.getItem().getItemStack().getType();
        if (!materialToItemId.containsKey(material)) return;

        String itemId = materialToItemId.get(material);
        ItemData itemData = deficitItems.get(itemId);
        if (itemData == null) return;

        if (forcedDeficit.getOrDefault(itemId, false) || itemData.getCount() >= itemData.getLimit()) {
            event.setCancelled(true);
            event.getItem().remove();
            handleDeficit(event.getPlayer(), itemData);
        } else {
            itemData.incrementCount();
        }
    }

    private void handleDeficit(Player player, ItemData itemData) {
        long currentTime = System.currentTimeMillis();
        if (lastEffectTimes.containsKey(player) && currentTime - lastEffectTimes.get(player) < 1000) return;
        lastEffectTimes.put(player, currentTime);

        ConfigurationSection limitEnd = itemData.getLimitEndSection();

        if (limitEnd.getBoolean("VirtualEntity.enable")) {
            handleVirtualEntityEffects(player, limitEnd.getConfigurationSection("VirtualEntity"));
        }

        if (limitEnd.getBoolean("Title.enable")) {
            String title = colorize(limitEnd.getString("Title.title"));
            String subtitle = colorize(limitEnd.getString("Title.subtitle"));
            player.sendTitle(title, subtitle, 10, 70, 20);
        }

        if (limitEnd.getBoolean("BossBar.enable")) {
            String bossBarText = colorize(limitEnd.getString("BossBar.bossbar"));
            BarStyle style = BarStyle.valueOf(limitEnd.getString("BossBar.type"));
            BarColor color = BarColor.valueOf(limitEnd.getString("BossBar.color"));
            int time = limitEnd.getInt("BossBar.time");

            BossBar bossBar = Bukkit.createBossBar(bossBarText, color, style);
            bossBar.addPlayer(player);
            bossBar.setVisible(true);

            new BukkitRunnable() {
                @Override
                public void run() {
                    bossBar.removeAll();
                }
            }.runTaskLater(this, time * 20L);
        }

        if (limitEnd.getBoolean("Message.enable")) {
            for (String line : limitEnd.getStringList("Message.message")) {
                player.sendMessage(colorize(line));
            }
        }
    }

    private void handleVirtualEntityEffects(Player player, ConfigurationSection virtualEntitySection) {
        if (virtualEntitySection == null) return;

        for (String effect : virtualEntitySection.getStringList("types")) {
            switch (effect.toLowerCase()) {
                case "explosion":
                    player.getWorld().createExplosion(player.getLocation(), 0f, false);
                    break;
                case "lightning":
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    break;
                case "particleanimation1":
                    createParticleAnimation1(player);
                    break;
                case "particleanimation2":
                    createParticleAnimation2(player);
                    break;
            }
        }
    }

    private void createParticleAnimation1(Player player) {
        new BukkitRunnable() {
            double radius = 1;
            double y = 0;
            int step = 0;

            @Override
            public void run() {
                if (step >= 20) {
                    cancel();
                    return;
                }

                for (double angle = 0; angle < 360; angle += 20) {
                    double radians = Math.toRadians(angle);
                    double x = radius * Math.cos(radians);
                    double z = radius * Math.sin(radians);
                    player.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                            player.getLocation().add(x, y, z), 1, 0, 0, 0, 0);
                }

                radius += 0.2;
                y += 0.1;
                step++;
            }
        }.runTaskTimer(this, 0, 2);
    }

    private void createParticleAnimation2(Player player) {
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= 30) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 360; i += 15) {
                    double radians = Math.toRadians(i + step * 12);
                    double x = Math.cos(radians) * 2;
                    double z = Math.sin(radians) * 2;
                    player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                            player.getLocation().add(x, 1, z), 1, 0, 0, 0, 0);
                }

                step++;
            }
        }.runTaskTimer(this, 0, 1);
    }

    private static class ItemData {
        private final Material material;
        private final int limit;
        private final ConfigurationSection limitEndSection;
        private int count = 0;

        public ItemData(Material material, int limit, ConfigurationSection limitEndSection) {
            this.material = material;
            this.limit = limit;
            this.limitEndSection = limitEndSection;
        }

        public Material getMaterial() {
            return material;
        }

        public int getLimit() {
            return limit;
        }

        public int getCount() {
            return count;
        }

        public void incrementCount() {
            count++;
        }

        public ConfigurationSection getLimitEndSection() {
            return limitEndSection;
        }
    }
}