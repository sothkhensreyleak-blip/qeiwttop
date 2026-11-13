package com.qeiwt.qeiwttop;

import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class QeiwtTop extends JavaPlugin implements CommandExecutor, TabCompleter {

    private record Entry(String name, double value) {}

    private final Map<String, TopDef> defs = new LinkedHashMap<>();
    private volatile Map<String, List<Entry>> cache = new HashMap<>();
    private Economy econ;
    private boolean papi;
    private BukkitTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("top").setExecutor(this);
        getCommand("top").setTabCompleter(this);
        load();
    }

    private void load() {
        if (task != null) task.cancel();
        reloadConfig();
        papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        setupEconomy();

        defs.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("tops");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                TopDef def = TopDef.parse(key.toLowerCase(), s, this);
                if (def != null) defs.put(def.key, def);
            }
        }

        long ticks = Math.max(5, getConfig().getLong("refresh-seconds", 60)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(this, this::refresh, 20L, ticks);
    }

    private void setupEconomy() {
        econ = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }

    private double valueOf(TopDef def, OfflinePlayer p) {
        try {
            switch (def.type) {
                case STATISTIC:
                    return p.getStatistic(def.statistic) / def.divide;
                case VAULT:
                    return econ == null ? Double.NaN : econ.getBalance(p) / def.divide;
                case PLACEHOLDER:
                    if (!papi) return Double.NaN;
                    String raw = PlaceholderAPI.setPlaceholders(p, def.placeholder)
                            .replace(",", "").replaceAll("[^0-9.\\-]", "");
                    return raw.isEmpty() ? Double.NaN : Double.parseDouble(raw) / def.divide;
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    private void refresh() {
        if (econ == null) setupEconomy();
        int size = getConfig().getInt("size", 10);
        boolean hideZero = getConfig().getBoolean("hide-zero", true);

        Map<String, List<Entry>> lists = new HashMap<>();
        for (String k : defs.keySet()) lists.put(k, new ArrayList<>());

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            String name = p.getName();
            if (name == null) continue;
            for (TopDef def : defs.values()) {
                double v = valueOf(def, p);
                if (Double.isNaN(v) || (hideZero && v <= 0)) continue;
                lists.get(def.key).add(new Entry(name, v));
            }
        }

        Map<String, List<Entry>> next = new HashMap<>();
        for (var e : lists.entrySet()) {
            List<Entry> l = e.getValue();
            l.sort((a, b) -> Double.compare(b.value(), a.value()));
            next.put(e.getKey(), new ArrayList<>(l.subList(0, Math.min(size, l.size()))));
        }
        cache = next;
    }

    private String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + key, ""));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(msg("list").replace("%tops%", String.join(", ", defs.keySet())));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("qeiwttop.reload")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            load();
            refresh();
            sender.sendMessage(msg("reloaded"));
            return true;
        }

        TopDef def = defs.get(args[0].toLowerCase());
        if (def == null) {
            sender.sendMessage(msg("unknown"));
            return true;
        }

        sender.sendMessage(msg("header").replace("%title%", def.title));
        List<Entry> list = cache.getOrDefault(def.key, List.of());
        if (list.isEmpty()) {
            sender.sendMessage(msg("empty"));
            return true;
        }
        int rank = 1;
        for (Entry e : list) {
            sender.sendMessage(msg("line")
                    .replace("%rank%", String.valueOf(rank++))
                    .replace("%player%", e.name())
                    .replace("%value%", def.format(e.value())));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>();
        List<String> all = new ArrayList<>(defs.keySet());
        all.add("list");
        if (s.hasPermission("qeiwttop.reload")) all.add("reload");
        for (String t : all) if (t.startsWith(args[0].toLowerCase())) out.add(t);
        return out;
    }
}
