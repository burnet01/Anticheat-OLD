package win.ac.x.commands.subcommands;

import com.google.common.collect.ImmutableList;
import win.ac.x.api.data.PlayerContainer;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.commands.MXSubCommand;
import win.ac.x.utils.ConfigCache;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static win.ac.x.utils.MessageUtils.wrapColors;

public final class ActivityCommand extends MXSubCommand {
    public ActivityCommand() {
        super("activity");
    }

    @Override
    public String getDescription() {
        return "Show player activity list";
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
        StringBuilder sb = new StringBuilder();
        sb.append(wrapColors("&9&l[MX Activity]\n"));
        for (PlayerProfile profile : PlayerContainer.getUuidPlayerProfileMap().values()) {
            String name = profile.getPlayer().getUsername();
            float vl = profile.getVl();
            int flags = profile.getFlagCount();
            sb.append(wrapColors(" &f" + name + " &8VL: &c" + (int) vl + "&8/&7" + (int) ConfigCache.VL_LIMIT + " &8Flags: &e" + flags + "\n"));
        }
        sender.sendMessage(sb.toString());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return ImmutableList.of();
    }
}