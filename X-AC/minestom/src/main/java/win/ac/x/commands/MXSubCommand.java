package win.ac.x.commands;

import win.ac.x.XMinestom;
import lombok.Getter;
import net.minestom.server.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public abstract class MXSubCommand {

    protected final String name;

    public MXSubCommand(String name) {
        this.name = name;
    }

    public abstract String getDescription();

    public String getUsage() {
        return "/" + XMinestom.command + " " + getName();
    }


    public abstract int getMinArgs();

    public abstract int getMaxArgs();

    public abstract boolean onlyPlayerCanUse();

    public abstract boolean onCommand(@NotNull CommandSender sender, String[] args);

    public abstract List<String> onTabComplete(CommandSender sender, String[] args);
}