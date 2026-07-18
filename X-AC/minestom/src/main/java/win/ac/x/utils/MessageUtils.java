package win.ac.x.utils;

import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtils {

    private static final Pattern HEX_PATTERN =
                    Pattern.compile("(?i)&#([A-F0-9]{6})");

    public static void broadcast(String message) {
        Component component = wrapColorsComponent(message);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            PlayerProfile profile = PlayerContainer.getProfile(player);
            if (profile == null || !profile.isAlerts()) {
                continue;
            }
            player.sendMessage(component);
        }
    }

    public static void sendToPlayer(Player player, String message) {
        player.sendMessage(wrapColorsComponent(message));
    }

    public static String wrapColors(String input) {
        if (input == null) return null;
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer(input.length());

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);

        return sb.toString().replace('&', '§');
    }

    public static Component wrapColorsComponent(String input) {
        return LegacyComponentSerializer.legacySection().deserialize(wrapColors(input));
    }

    public static String wrapColors(String... v) {
        final StringBuilder builder = new StringBuilder();
        for (final String s : v) {
            final String wrapped = wrapColors(s);
            builder.append((builder.length() == 0) ? wrapped : "\n" + wrapped);
        }
        return builder.toString();
    }

    public static String getDate() {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd:MM:yyyy");
        return sdf.format(date);
    }
}