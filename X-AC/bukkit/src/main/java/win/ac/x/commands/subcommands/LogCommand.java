package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.X;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.utils.LogUtils;
import win.ac.x.utils.MessageUtils;
import win.ac.x.utils.NetworkUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LogCommand extends MXSubCommand {
    public LogCommand() {
        super("log");
    }

    @Override
    public String getDescription() {
        return "Upload player log to pastebin";
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
        PlayerProfile target = PlayerContainer.getProfileByName(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found... Sorry!");
            return true;
        }
        { // uploading
            AsyncScheduler.run(() -> {
                final StringBuilder logBuilder = new StringBuilder();
                for (final String l : LogUtils.getLog(args[0])) logBuilder.append("\n").append(l);
                for (final String l : target.getLogs()) logBuilder.append("\n").append(l);
                sender.sendMessage(MessageUtils.wrapColors("&9Uploading to pastebin, wait for it..."));
                final String result = NetworkUtil.createPaste("Info about " + target.getPlayer().getName() + "\n\n" + logBuilder);
                if (result == null) {
                    sender.sendMessage(MessageUtils.wrapColors("&cUnknown error while loading :("));
                } else {
                    sender.sendMessage(MessageUtils.wrapColors("&9Info about &e" + target.getPlayer().getName() + "&7: " + result));
                }
            });
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return null;
        return ImmutableList.of(); // return empty list
    }
}
