package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.X;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.math.Simplification;
import win.ac.x.math.Statistics;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class InfoCommand extends MXSubCommand {
    public InfoCommand() {
        super("info");
    }

    @Override
    public String getDescription() {
        return "Get info about a player";
    }

    @Override
    public String getUsage() {
        return "/" + X.command + " " + getName() + " <player>";
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 1;
    }

    @Override
    public boolean onlyPlayerCanUse() {
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String[] args) {
        PlayerProfile playerProfile = PlayerContainer.getProfileByName(args[0]);
        if (playerProfile == null) {
            sender.sendMessage("§cPlayer not found... Sorry!");
            return true;
        }
        String sens = wrapColors("&4Not enough info!");
        final int calculated = playerProfile.calculateSensitivity();
        if (calculated > -1) {
            sens = ChatColor.BLUE.toString() + calculated;
        }
        StringBuilder pingLabel = new StringBuilder();
        String delimiter = "";
        for (long ping : playerProfile.getPing()) {
            ChatColor color = getColorForPing(ping);
            pingLabel.append(delimiter).append(color.toString()).append(ping);
            if (delimiter.isEmpty()) {
                delimiter = ChatColor.WHITE + ", ";
            }
        }
        final String[] info = new String[]{
                "",
                wrapColors("&fInfo about &c" + playerProfile.getPlayer().getName()),
                "",
                wrapColors("&fPing (ms): " + pingLabel),
                wrapColors("&fJitter (ms): &9" + Simplification.scaleVal(Statistics.getStandardDeviation(playerProfile.getPing()), 2)),
                wrapColors("&fSensitivity: " + sens),
                wrapColors("&fVL: &c" + playerProfile.getVl()),
                ""
        };
        for (String i : info) {
            sender.sendMessage(i);
        }
        return true;
    }

    private ChatColor getColorForPing(long ping) {
        if (ping > 1000) return ChatColor.DARK_RED;
        if (ping > 300) return ChatColor.RED;
        if (ping > 100) return ChatColor.YELLOW;
        return ChatColor.GREEN;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return null;
        return ImmutableList.of(); // return empty list
    }
}
