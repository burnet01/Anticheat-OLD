package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.X;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.data.RotationsContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.core.AsyncScheduler;
import win.ac.x.utils.MessageUtils;
import win.ac.x.utils.NetworkUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ActivityCommand extends MXSubCommand {
    public ActivityCommand() {
        super("activity");
    }

    @Override
    public String getDescription() {
        return "Link to show mouse movements in the MX panel";
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
                sender.sendMessage(MessageUtils.wrapColors("&9Uploading, wait for it..."));
                final String result = NetworkUtil.createPaste(RotationsContainer.getJson(target.getPlayer().getUniqueId()));
                if (result == null) {
                    sender.sendMessage(MessageUtils.wrapColors("&cUnknown error while loading :("));
                } else {
                    sender.sendMessage(MessageUtils.wrapColors(
                                    "&e&e",
                                    "&e" + target.getPlayer().getName() + "&9 data: &6" + result,
                                    "&cYou need to paste the link into the MX panel application!",
                                    "&fPlease download it here:",
                                    "&7https://drive.google.com/file/d/1b_7RHz0mIuk4PXvcFScFZf57VE-VCfQg/view?usp=sharing",
                                    "&e&e"
                                    )
                    );
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
