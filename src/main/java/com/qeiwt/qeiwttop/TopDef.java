package com.qeiwt.qeiwttop;

import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;

public class TopDef {
    public enum Type { STATISTIC, VAULT, PLACEHOLDER }

    public final String key;
    public final String title;
    public final Type type;
    public final Statistic statistic;
    public final String placeholder;
    public final double divide;
    public final String prefix;
    public final String suffix;
    private final String pattern;

    private TopDef(String key, ConfigurationSection s, Type type, Statistic stat) {
        this.key = key;
        this.title = s.getString("title", key);
        this.type = type;
        this.statistic = stat;
        this.placeholder = s.getString("placeholder", "");
        double d = s.getDouble("divide", 1);
        this.divide = d == 0 ? 1 : d;
        this.prefix = s.getString("prefix", "");
        this.suffix = s.getString("suffix", "");
        this.pattern = s.getString("format", "#,##0.##");
    }

    /** Returns null (and the reason via QeiwtTop logger) if the section is invalid. */
    public static TopDef parse(String key, ConfigurationSection s, QeiwtTop plugin) {
        Type type;
        try {
            type = Type.valueOf(s.getString("type", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Top '" + key + "': type មិនត្រឹមត្រូវ (STATISTIC / VAULT / PLACEHOLDER)");
            return null;
        }
        Statistic stat = null;
        if (type == Type.STATISTIC) {
            try {
                stat = Statistic.valueOf(s.getString("statistic", "").toUpperCase());
                if (stat.getType() != Statistic.Type.UNTYPED) {
                    plugin.getLogger().warning("Top '" + key + "': statistic នេះត្រូវការ material/entity មិនគាំទ្រ");
                    return null;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Top '" + key + "': statistic មិនត្រឹមត្រូវ");
                return null;
            }
        }
        if (type == Type.PLACEHOLDER && s.getString("placeholder", "").isEmpty()) {
            plugin.getLogger().warning("Top '" + key + "': ខ្វះ placeholder");
            return null;
        }
        return new TopDef(key, s, type, stat);
    }

    public String format(double value) {
        return prefix + new DecimalFormat(pattern).format(value) + suffix;
    }
}
