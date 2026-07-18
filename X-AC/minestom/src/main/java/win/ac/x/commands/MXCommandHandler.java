package win.ac.x.commands;

import win.ac.x.XMinestom;
import win.ac.x.commands.subcommands.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

import java.util.*;

import static win.ac.x.utils.MessageUtils.wrapColors;
import static win.ac.x.utils.MessageUtils.wrapColorsComponent;

public final class MXCommandHandler extends Command {

    private final Map<String, MXSubCommand> subCommands = new LinkedHashMap<>();

    public MXCommandHandler() {
        super(XMinestom.command, "anticheat");
        setDefaultExecutor((sender, context) -> {
            this.showHelps(sender);
        });

        register(new AlertCommand());
        register(new LogCommand());
        register(new DebugCommand());
        register(new InfoCommand());
        register(new PunishCommand());
        register(new ReloadCommand());
        register(new StatsCommand());
        register(new ActivityCommand());
        register(new MLCommand());
        register(new DatasetCommand());
        register(new TrainCommand());
    }

    private void register(MXSubCommand sub) {
        subCommands.put(sub.getName().toLowerCase(), sub);
        addSyntax((sender, context) -> {
            String[] args = context.has("args") ? context.get("args") : new String[0];
            if (sub.onlyPlayerCanUse() && !(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by player!");
                return;
            }
            if (args.length > sub.getMaxArgs() || args.length < sub.getMinArgs()) {
                sender.sendMessage("Usage: " + sub.getUsage());
                return;
            }
            sub.onCommand(sender, args);
        }, ArgumentType.Word(sub.getName()), ArgumentType.StringArray("args").setDefaultValue(new String[0]));
    }

    private void showHelps(CommandSender sender) {
        sender.sendMessage(wrapColors("&9&l" + XMinestom.name + " &fCommands"));
        sender.sendMessage(Component.empty());
        for (MXSubCommand subCommand : subCommands.values()) {
            if (subCommand.onlyPlayerCanUse() && !(sender instanceof Player)) {
                continue;
            }
            String message = wrapColors("&e/" + XMinestom.command + " " + subCommand.getName() + " &f- &c" + subCommand.getDescription());
            if (sender instanceof Player) {
                Component textComponent = wrapColorsComponent(message)
                        .clickEvent(ClickEvent.suggestCommand("/" + XMinestom.command + " " + subCommand.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Command: " + subCommand.getName()
                                + "\nDescription: " + subCommand.getDescription()
                                + "\nUsage: " + subCommand.getUsage()
                        )));
                ((Player) sender).sendMessage(textComponent);
            } else {
                sender.sendMessage(message);
            }
        }
    }
}