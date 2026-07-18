package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.XMinestom;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.services.DatasetRecorder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class DatasetCommand extends MXSubCommand {
    public DatasetCommand() {
        super("dataset");
    }

    @Override
    public String getDescription() {
        return "Dataset recording controls";
    }

    @Override
    public String getUsage() {
        return "/" + XMinestom.command + " " + getName() + " <start/stop/list/info>";
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 2;
    }

    @Override
    public boolean onlyPlayerCanUse() {
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String[] args) {
        String action = args[0].toLowerCase();
        switch (action) {
            case "start": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can start recording.");
                    break;
                }
                Player player = (Player) sender;
                String label = args.length >= 2 ? args[1] : "legit";
                DatasetRecorder.startRecording(player.getUuid(), label);
                player.sendMessage(wrapColors("&aStarted recording dataset with label: " + label));
                break;
            }
            case "stop": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can stop recording.");
                    break;
                }
                Player player = (Player) sender;
                if (DatasetRecorder.isRecording(player.getUuid())) {
                    DatasetRecorder.stopRecording(player.getUuid());
                    player.sendMessage(wrapColors("&aStopped recording dataset."));
                } else {
                    player.sendMessage(wrapColors("&cNot recording."));
                }
                break;
            }
            case "list":
                sender.sendMessage(DatasetRecorder.listFiles());
                break;
            case "info": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can view info.");
                    break;
                }
                Player player = (Player) sender;
                boolean recording = DatasetRecorder.isRecording(player.getUuid());
                sender.sendMessage(wrapColors("&9&l[Dataset]"));
                sender.sendMessage(wrapColors(" &fRecording: " + (recording ? "&aYes" : "&cNo")));
                sender.sendMessage(wrapColors(" &fTotal files: &e" + DatasetRecorder.getFileCount()));
                break;
            }
            default:
                sender.sendMessage(wrapColors("&cUsage: " + getUsage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "list", "info");
        }
        return ImmutableList.of();
    }
}