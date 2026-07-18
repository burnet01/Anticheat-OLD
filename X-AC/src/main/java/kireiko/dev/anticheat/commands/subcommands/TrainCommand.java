package kireiko.dev.anticheat.commands.subcommands;

import kireiko.dev.anticheat.MX;
import kireiko.dev.anticheat.commands.MXSubCommand;
import kireiko.dev.anticheat.core.AsyncScheduler;
import kireiko.dev.anticheat.managers.DatasetManager;
import kireiko.dev.anticheat.services.DatasetRecorder;
import kireiko.dev.millennium.ml.FactoryML;
import kireiko.dev.millennium.ml.data.ObjectML;
import kireiko.dev.millennium.ml.logic.Millennium;
import kireiko.dev.millennium.vectors.Pair;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public final class TrainCommand extends MXSubCommand {

    public TrainCommand() {
        super("train");
    }

    @Override
    public String getDescription() {
        return "Train ML model from saved dataset";
    }

    @Override
    public String getUsage() {
        return "/" + MX.command + " train <modelIndex> <epochs>";
    }

    @Override
    public int getMinArgs() {
        return 2;
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
        int index;
        int epochs;
        try {
            index = Integer.parseInt(args[0]);
            epochs = Integer.parseInt(args[1]);
        } catch (Exception e) {
            sender.sendMessage("§cInvalid numbers.");
            return true;
        }

        Millennium model = FactoryML.getModel(index);
        if (model == null) {
            sender.sendMessage("§cModel not found.");
            return true;
        }

        int totalFiles = DatasetManager.getCount() + DatasetRecorder.getFileCount();
        sender.sendMessage("§eDataset has " + totalFiles + " total files.");
        if (totalFiles == 0) {
            sender.sendMessage("§cNo dataset files found. Use §f/mx dataset legit <player> §cor §f/mx dataset cheat <player> §cfirst.");
            return true;
        }

        String modelPath = "plugins/MX/models/m1-rnn.dat";
        sender.sendMessage("§eStarting training on model #" + index + " for " + epochs + " epochs...");
        sender.sendMessage("§7This runs async — you'll be notified when complete.");

        final CommandSender fSender = sender;
        AsyncScheduler.run(() -> {
            try {
                List<Pair<List<ObjectML>, Boolean>> dataset = DatasetManager.loadDataset();
                if (dataset.isEmpty()) {
                    fSender.sendMessage("§cRotation dataset is empty. Only .dat files are used for training.");
                    return;
                }

                int cheats = 0;
                for (Pair<List<ObjectML>, Boolean> p : dataset) if (p.getY()) cheats++;

                fSender.sendMessage("§aLoaded " + dataset.size() + " rotation samples ("
                    + "§c" + cheats + " cheats§a, §a" + (dataset.size() - cheats) + " legit§a).");

                long startTime = System.currentTimeMillis();
                for (int e = 1; e <= epochs; e++) {
                    model.trainEpochs(dataset, 1);
                    if (epochs >= 5 && (e % Math.max(1, epochs / 5) == 0 || e == epochs)) {
                        double pct = (e * 100.0) / epochs;
                        long elapsed = System.currentTimeMillis() - startTime;
                        double etaSec = (elapsed / 1000.0 / e) * (epochs - e);
                        fSender.sendMessage("§e  Epoch " + e + "/" + epochs + " §7("
                            + String.format("%.0f", pct) + "%) §7— ~" + String.format("%.0f", etaSec) + "s remaining");
                    }
                }

                File modelDir = new File("plugins/MX/models");
                if (!modelDir.exists()) modelDir.mkdirs();
                model.saveToFile(modelPath);

                long totalMs = System.currentTimeMillis() - startTime;
                fSender.sendMessage("§a§lTraining complete! §a"
                    + epochs + " epochs in " + String.format("%.1f", totalMs / 1000.0) + "s.");
                fSender.sendMessage("§aModel saved: §f" + new File(modelPath).getAbsolutePath());
                fSender.sendMessage("§7Next: reload the plugin or restart to use the new model.");
            } catch (Exception e) {
                fSender.sendMessage("§cError during training: " + e.getMessage());
                e.printStackTrace();
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}