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

public final class AlertCommand extends MXSubCommand {
    public AlertCommand() {
        super("alert");
    }

    @Override
    public String getDescription() {
        return "Toggle the alerts";
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
        sender.sendMessage(wrapColors("&cAlerts: &e" + profile.toggleAlerts()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return ImmutableList.of();
    }
}