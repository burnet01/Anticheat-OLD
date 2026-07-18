package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.XMinestom;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.ml.ClientML;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class MLCommand extends MXSubCommand {
    public MLCommand() {
        super("ml");
    }

    @Override
    public String getDescription() {
        return "ML engine controls";
    }

    @Override
    public String getUsage() {
        return "/" + XMinestom.command + " " + getName() + " <status/train>";
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
        String action = args[0];
        if (action.equalsIgnoreCase("status")) {
            sender.sendMessage(wrapColors("&9&l[ML] &fML Status: " + (ClientML.isRunning() ? "&aRunning" : "&cStopped")));
        } else if (action.equalsIgnoreCase("train")) {
            sender.sendMessage(wrapColors("&9&l[ML] &fTraining started..."));
            ClientML.forceTrain();
            sender.sendMessage(wrapColors("&9&l[ML] &aTraining complete!"));
        } else {
            sender.sendMessage(wrapColors("&cUsage: " + getUsage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("status", "train");
        }
        return ImmutableList.of();
    }
}