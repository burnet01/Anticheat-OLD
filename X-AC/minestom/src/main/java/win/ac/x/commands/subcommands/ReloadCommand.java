package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.managers.CheckManager;
import win.ac.x.utils.ConfigCache;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class ReloadCommand extends MXSubCommand {
    public ReloadCommand() {
        super("reload");
    }

    @Override
    public String getDescription() {
        return "Reload config";
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
        ConfigCache.loadConfig();
        CheckManager.init();
        sender.sendMessage(wrapColors("&aConfig reloaded!"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return ImmutableList.of();
    }
}