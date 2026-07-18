package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.XMinestom;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXSubCommand;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class PunishCommand extends MXSubCommand {
    public PunishCommand() {
        super("punish");
    }

    @Override
    public String getDescription() {
        return "Force punish a player";
    }

    @Override
    public String getUsage() {
        return "/" + getName() + " punish <player>";
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
        PlayerProfile profile = PlayerContainer.getProfileByName(args[0]);
        if (profile == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        XMinestom.getPunishmentHandler().onPunish(profile, "MANUAL", "force", "Force punishment", profile.getVl());
        sender.sendMessage(wrapColors("&cPunished " + args[0]));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return null;
        return ImmutableList.of();
    }
}