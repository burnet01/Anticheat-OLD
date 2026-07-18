package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.managers.DatasetManager;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class TrainCommand extends MXSubCommand {
    public TrainCommand() {
        super("train");
    }

    @Override
    public String getDescription() {
        return "Train ML models with collected dataset";
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
        int count = DatasetManager.getCount();
        sender.sendMessage(wrapColors("&9&l[Train] &fDataset samples: &e" + count));
        if (count == 0) {
            sender.sendMessage(wrapColors("&cNo dataset samples found. Collect data first."));
            return true;
        }
        sender.sendMessage(wrapColors("&aTraining not yet implemented for this platform."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return ImmutableList.of();
    }
}