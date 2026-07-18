package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXSubCommand;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class DebugCommand extends MXSubCommand {
    public DebugCommand() {
        super("debug");
    }

    @Override
    public String getDescription() {
        return "Toggle debug mode(check verbose)";
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
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String[] args) {
        Player player = (Player) sender;
        PlayerProfile profile = PlayerContainer.getProfile(player);
        if (profile == null) {
            sender.sendMessage(wrapColors("&cProfile not initialized!"));
            return true;
        }
        sender.sendMessage(wrapColors("&cDebug: &e" + profile.toggleDebug()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return ImmutableList.of();
    }
}