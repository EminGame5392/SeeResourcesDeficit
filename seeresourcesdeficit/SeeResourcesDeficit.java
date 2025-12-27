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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SeeResourcesDeficit extends JavaPlugin implements Listener, TabCompleter {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private FileConfiguration config;
    private final Map<String, ItemData> deficitItems = new HashMap<>();
    private final Map<Material, String> materialToItemId = new HashMap<>();
    private final Map<String, Boolean> forcedDeficit = new HashMap<>();
    private final Map<UUID, Long> lastEffectTimes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("seeresourcesdeficit").setTabCompleter(this);

        // Сообщение о запуске
        getLogger().info("SeeResourcesDeficit v" + getDescription().getVersion() + " успешно запущен!");
        getLogger().info("Поддерживаемые версии: 1.16.5-1.20.1");
    }

    private String colorize(String message) {
        if (message == null) return "";

        // Проверяем версию сервера для поддержки HEX цветов
        String version = Bukkit.getServer().getBukkitVersion();

        // Для версий 1.16 и выше поддерживаем HEX цвета
        if (version.contains("1.16") || version.contains("1.17") ||
                version.contains("1.18") || version.contains("1.19") ||
                version.contains("1.20")) {
            return translateHexColorCodes(message);
        } else {
            // Для старых версий - только стандартные цвета
            return message.replace('&', '§');
        }
    }

    private String translateHexColorCodes(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }

        String result = matcher.appendTail(buffer).toString();
        return result.replace('&', '§');
    }

    private void loadConfig() {
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        ConfigurationSection itemsSection = config.getConfigurationSection("deficit_items");
        if (itemsSection == null) {
            getLogger().warning("Секция 'deficit_items' не найдена в конфигурации!");
            return;
        }

        deficitItems.clear();
        materialToItemId.clear();

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
            if (itemSection == null) {
                getLogger().warning("Секция для предмета " + itemId + " не найдена!");
                continue;
            }

            String materialName = itemSection.getString("material");
            if (materialName == null) {
                getLogger().warning("Материал для предмета " + itemId + " не указан!");
                continue;
            }

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                getLogger().warning("Неизвестный материал: " + materialName + " для предмета " + itemId);
                continue;
            }

            int limit = itemSection.getInt("limit", 1000);
            ConfigurationSection limitEndSection = itemSection.getConfigurationSection("limit_end");
            if (limitEndSection == null) {
                getLogger().warning("Секция 'limit_end' для предмета " + itemId + " не найдена!");
                continue;
            }

            ItemData itemData = new ItemData(material, limit, limitEndSection);
            deficitItems.put(itemId, itemData);
            materialToItemId.put(material, itemId);
            forcedDeficit.putIfAbsent(itemId, false);

            getLogger().info("Загружен предмет: " + itemId + " (" + material + ") с лимитом " + limit);
        }

        getLogger().info("Загружено " + deficitItems.size() + " предметов с дефицитом");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seeresourcesdeficit.admin")) {
            sender.sendMessage(colorize("&cУ вас нет прав!"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
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
                    sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit enable [ID предмета]"));
                    sender.sendMessage(colorize("&7Доступные предметы: &e" + String.join(", ", deficitItems.keySet())));
                    return true;
                }
                String enableItem = args[1];
                if (!deficitItems.containsKey(enableItem)) {
                    sender.sendMessage(colorize("&cПредмет '&e" + enableItem + "&c' не найден в конфигурации!"));
                    sender.sendMessage(colorize("&7Доступные предметы: &e" + String.join(", ", deficitItems.keySet())));
                    return true;
                }
                forcedDeficit.put(enableItem, true);
                sender.sendMessage(colorize("&aДефицит для предмета &e" + enableItem + " &aпринудительно включен!"));
                return true;
            case "disable":
                if (args.length < 2) {
                    sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit disable [ID предмета]"));
                    sender.sendMessage(colorize("&7Доступные предметы: &e" + String.join(", ", deficitItems.keySet())));
                    return true;
                }
                String disableItem = args[1];
                if (!deficitItems.containsKey(disableItem)) {
                    sender.sendMessage(colorize("&cПредмет '&e" + disableItem + "&c' не найден в конфигурации!"));
                    sender.sendMessage(colorize("&7Доступные предметы: &e" + String.join(", ", deficitItems.keySet())));
                    return true;
                }
                forcedDeficit.put(disableItem, false);
                sender.sendMessage(colorize("&aДефицит для предмета &e" + disableItem + " &aпринудительно выключен!"));
                return true;
            case "list":
                sender.sendMessage(colorize("&6=== Список предметов с дефицитом ==="));
                for (Map.Entry<String, ItemData> entry : deficitItems.entrySet()) {
                    String itemId = entry.getKey();
                    ItemData data = entry.getValue();
                    boolean isForced = forcedDeficit.getOrDefault(itemId, false);
                    String status = isForced ? "&c(принудительно)" : (data.getCount() >= data.getLimit() ? "&c(лимит)" : "&a(активно)");
                    sender.sendMessage(colorize("&7- &e" + itemId + " &7- " + data.getMaterial() +
                            " &7[&f" + data.getCount() + "&7/&f" + data.getLimit() + "&7] " + status));
                }
                return true;
            case "reset":
                if (args.length < 2) {
                    sender.sendMessage(colorize("&cИспользование: /seeresourcesdeficit reset [ID предмета|all]"));
                    return true;
                }
                String resetItem = args[1];
                if (resetItem.equalsIgnoreCase("all")) {
                    for (ItemData data : deficitItems.values()) {
                        data.resetCount();
                    }
                    sender.sendMessage(colorize("&aСчетчики всех предметов сброшены!"));
                } else if (deficitItems.containsKey(resetItem)) {
                    deficitItems.get(resetItem).resetCount();
                    sender.sendMessage(colorize("&aСчетчик предмета &e" + resetItem + " &aсброшен!"));
                } else {
                    sender.sendMessage(colorize("&cПредмет не найден!"));
                }
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(colorize("&6=== SeeResourcesDeficit Помощь ==="));
        sender.sendMessage(colorize("&e/seeresourcesdeficit reload &7- Перезагрузить конфигурацию"));
        sender.sendMessage(colorize("&e/seeresourcesdeficit enable [предмет] &7- Включить принудительный дефицит"));
        sender.sendMessage(colorize("&e/seeresourcesdeficit disable [предмет] &7- Выключить принудительный дефицит"));
        sender.sendMessage(colorize("&e/seeresourcesdeficit list &7- Список предметов и их статус"));
        sender.sendMessage(colorize("&e/seeresourcesdeficit reset [предмет|all] &7- Сбросить счетчик"));
        sender.sendMessage(colorize("&7Алиасы: &e/seerd"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("seeresourcesdeficit.admin")) return completions;

        if (args.length == 1) {
            completions.add("reload");
            completions.add("enable");
            completions.add("disable");
            completions.add("list");
            completions.add("reset");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")) {
                completions.addAll(deficitItems.keySet());
            } else if (args[0].equalsIgnoreCase("reset")) {
                completions.addAll(deficitItems.keySet());
                completions.add("all");
            }
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
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Защита от спама эффектами (не чаще чем раз в секунду)
        if (lastEffectTimes.containsKey(playerId) && currentTime - lastEffectTimes.get(playerId) < 1000) {
            return;
        }
        lastEffectTimes.put(playerId, currentTime);

        ConfigurationSection limitEnd = itemData.getLimitEndSection();

        if (limitEnd.getBoolean("VirtualEntity.enable", false)) {
            handleVirtualEntityEffects(player, limitEnd.getConfigurationSection("VirtualEntity"));
        }

        if (limitEnd.getBoolean("Title.enable", false)) {
            String title = colorize(limitEnd.getString("Title.title", "&cДЕФИЦИТ"));
            String subtitle = colorize(limitEnd.getString("Title.subtitle", "&cНа этот предмет закончился лимит"));
            player.sendTitle(title, subtitle, 10, 70, 20);
        }

        if (limitEnd.getBoolean("BossBar.enable", false)) {
            String bossBarText = colorize(limitEnd.getString("BossBar.bossbar", "&cНа этот предмет закончился лимит!"));
            BarStyle style;
            try {
                style = BarStyle.valueOf(limitEnd.getString("BossBar.type", "SEGMENTED_10"));
            } catch (IllegalArgumentException e) {
                style = BarStyle.SOLID;
            }

            BarColor color;
            try {
                color = BarColor.valueOf(limitEnd.getString("BossBar.color", "RED"));
            } catch (IllegalArgumentException e) {
                color = BarColor.RED;
            }

            int time = limitEnd.getInt("BossBar.time", 5);

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

        if (limitEnd.getBoolean("Message.enable", false)) {
            List<String> messages = limitEnd.getStringList("Message.message");
            if (messages.isEmpty()) {
                player.sendMessage(colorize("&cДЕФИЦИТ &b>> &cЭтот предмет невозможно добыть из-за его дефицита!"));
            } else {
                for (String line : messages) {
                    player.sendMessage(colorize(line));
                }
            }
        }
    }

    private void handleVirtualEntityEffects(Player player, ConfigurationSection virtualEntitySection) {
        if (virtualEntitySection == null) return;

        List<String> effects = virtualEntitySection.getStringList("types");
        if (effects == null || effects.isEmpty()) return;

        for (String effect : effects) {
            if (effect == null) continue;

            switch (effect.toLowerCase()) {
                case "explosion":
                    player.getWorld().createExplosion(player.getLocation(), 0f, false, false);
                    break;
                case "lightning":
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    break;
                case "particleanimation1":
                case "particleanimation":
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

    @Override
    public void onDisable() {
        getLogger().info("SeeResourcesDeficit выключен");
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

        public void resetCount() {
            count = 0;
        }

        public ConfigurationSection getLimitEndSection() {
            return limitEndSection;
        }
    }
}