package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.XMinestom;
import win.ac.x.commands.MXSubCommand;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class StatsCommand extends MXSubCommand {
    public StatsCommand() {
        super("stats");
    }

    @Override
    public String getDescription() {
        return "Show global statistics";
    }

    @Override
    public int getMinArgs() {
        return 0;
    }

    @Override
    public int getMaxArgs() {
        return 0;
    }

    @Override
    public boolean onlyPlayerCanUse() {
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String[] args) {
        int totalBanned = 0;
        for (int i : XMinestom.bannedPerMinuteList) totalBanned += i;
        int totalBlocked = 0;
        for (int i : XMinestom.blockedPerMinuteList) totalBlocked += i;

        sender.sendMessage(wrapColors("&9&l[MX Stats]"));
        sender.sendMessage(wrapColors("&fBanned (total): &c" + totalBanned));
        sender.sendMessage(wrapColors("&fBlocked (total): &c" + totalBlocked));
        sender.sendMessage(wrapColors("&fBanned/min: &c" + XMinestom.bannedPerMinuteCount));
        sender.sendMessage(wrapColors("&fBlocked/min: &c" + XMinestom.blockedPerMinuteCount));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return ImmutableList.of();
    }
}