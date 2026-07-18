package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.XMinestom;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.utils.LogUtils;
import win.ac.x.utils.NetworkUtil;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class LogCommand extends MXSubCommand {
    public LogCommand() {
        super("log");
    }

    @Override
    public String getDescription() {
        return "Create a paste of log for a player";
    }

    @Override
    public String getUsage() {
        return "/" + XMinestom.command + " " + getName() + " <player>";
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
        String targetName = args[0];
        String log = LogUtils.getSimpleLog(targetName);

        if (log.isEmpty()) {
            sender.sendMessage(wrapColors("&cNo logs found for " + targetName));
            return true;
        }

        String pasteUrl = NetworkUtil.createPaste(log);
        if (pasteUrl != null) {
            sender.sendMessage(wrapColors("&aPaste created: &f" + pasteUrl));
        } else {
            sender.sendMessage(wrapColors("&cFailed to create paste. Sending raw log:"));
            sender.sendMessage(log);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return null;
        return ImmutableList.of();
    }
}