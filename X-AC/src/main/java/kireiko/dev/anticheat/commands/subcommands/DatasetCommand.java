package kireiko.dev.anticheat.commands.subcommands;

import kireiko.dev.anticheat.MX;
import kireiko.dev.anticheat.checks.aim.ml.AimMLCheck;
import kireiko.dev.anticheat.commands.MXSubCommand;
import kireiko.dev.anticheat.managers.DatasetManager;
import kireiko.dev.anticheat.services.DatasetRecorder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class DatasetCommand extends MXSubCommand {

    private static final List<String> CHEAT_LABELS = Arrays.asList(
        "speed", "fly", "killaura", "aimbot", "velocity",
        "bhop", "reach", "scaffold", "autoclicker", "noclip", "silentaim", "spider"
    );

    public DatasetCommand() {
        super("dataset");
    }

    @Override
    public String getDescription() {
        return "Manage dataset: <legit|cheat|off|list> <player|all> [label] [letter]";
    }

    @Override
    public String getUsage() {
        return "/" + MX.command + " dataset <legit|cheat|off|list> <player|all> [label] [letter]";
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 4;
    }

    @Override
    public boolean onlyPlayerCanUse() {
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String[] args) {
        String mode = args[0].toLowerCase();

        if (mode.equals("list") || mode.equals("ls")) {
            sender.sendMessage(DatasetRecorder.listFiles());
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: " + getUsage());
            return true;
        }

        String targetArg = args[1].toLowerCase();

        if (!mode.equals("legit") && !mode.equals("cheat") && !mode.equals("off")) {
            sender.sendMessage("§cUse: legit, cheat, off, or list.");
            return true;
        }

        String label = null;
        String letter = null;

        if (mode.equals("legit")) {
            label = "legit";
            if (args.length >= 3 && !args[2].isEmpty()) {
                letter = args[2].toUpperCase();
            }
        } else if (mode.equals("cheat")) {
            label = (args.length >= 3 && !args[2].isEmpty()) ? args[2].toLowerCase() : "cheat";
            if (args.length >= 4 && !args[3].isEmpty()) {
                letter = args[3].toUpperCase();
            }
        }

        if (targetArg.equals("all") || targetArg.equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (label == null) {
                    AimMLCheck.RECORDING.remove(p.getUniqueId());
                    DatasetRecorder.stopRecording(p.getUniqueId());
                } else {
                    AimMLCheck.RECORDING.put(p.getUniqueId(), !label.equals("legit"));
                    DatasetRecorder.startRecording(p.getUniqueId(), label, letter);
                    startCountdown(p, label, letter, sender);
                }
                count++;
            }
            sender.sendMessage("§aApplied dataset mode §e" + mode.toUpperCase() + " §ato §e" + count + " §aonline players.");
        } else {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }

            if (label == null) {
                AimMLCheck.RECORDING.remove(target.getUniqueId());
                DatasetRecorder.stopRecording(target.getUniqueId());
                sender.sendMessage("§eStopped all recording for " + target.getName());
            } else {
                AimMLCheck.RECORDING.put(target.getUniqueId(), !label.equals("legit"));
                DatasetRecorder.startRecording(target.getUniqueId(), label, letter);
                String display = buildDisplay(label, letter);
                target.sendMessage(display + "§l [DATASET] Now recording — keep moving/clicking for 30s");
                String letterInfo = (letter != null) ? " §7letter: §e" + letter : " §7(letter: auto)";
                sender.sendMessage("§aNow recording §e" + label.toUpperCase() + letterInfo
                    + " §adata for " + target.getName() + " §7(30s auto-stop)");
                startCountdown(target, label, letter, sender);
            }
        }
        return true;
    }

    private String buildDisplay(String label, String letter) {
        String base = label.equals("legit") ? "§aLEGIT" : "§c" + label.toUpperCase();
        if (letter != null) {
            base += " §7[" + letter + "]";
        }
        return base;
    }

    private void startCountdown(Player target, String label, String letter, CommandSender sender) {
        UUID uuid = target.getUniqueId();
        String display = buildDisplay(label, letter);

        new BukkitRunnable() {
            int remaining = 30;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    AimMLCheck.RECORDING.remove(uuid);
                    DatasetRecorder.stopRecording(uuid);
                    cancel();
                    return;
                }
                if (!DatasetRecorder.isRecording(uuid)) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    DatasetRecorder.stopRecording(uuid);
                    AimMLCheck.RECORDING.remove(uuid);
                    int total = DatasetManager.getCount() + DatasetRecorder.getFileCount();
                    target.sendMessage("§e§l[DATASET] " + display + " §e30s recording period complete. "
                        + "§7Total files: §f" + total + " §7— You can stop moving now.");
                    if (sender instanceof Player && ((Player) sender).isOnline()) {
                        sender.sendMessage("§e[DATASET] 30s period complete for " + target.getName() + " (" + label + ")");
                    }
                    cancel();
                    return;
                }
                if (remaining <= 5 || remaining % 10 == 0) {
                    target.sendMessage("§7[DATASET] " + display + " §7Recording... §f" + remaining + "s §7remaining — keep clicking/moving");
                }
                remaining--;
            }
        }.runTaskTimer(MX.getInstance(), 0L, 20L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return Arrays.asList("legit", "cheat", "off", "list");
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) {
                suggestions.add(p.getName());
            }
            return suggestions;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cheat")) {
            return CHEAT_LABELS;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("legit")) {
            return Arrays.asList("A", "B", "C", "D", "E", "F");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("cheat")) {
            return Arrays.asList("A", "B", "C", "D", "E", "F");
        }
        return null;
    }
}
