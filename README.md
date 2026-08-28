# MX / X-AC Anti-Cheat

This repository is an old, experimental anti-cheat codebase built around a mix of server-side checks, packet analysis, and machine-learning-style detection. It is not a polished production anticheat and it is not recommended for real-world deployment without heavy tuning, testing, and likely rewriting.

> Important: This project is very old and it is very rough to use. It is known to have false positives, especially in live PvP or noisy server environments. It should be treated as a base or reference project, not as a drop-in anti-cheat solution.

This project was created as a learning / experimentation project around packet-based cheat detection, movement simulation, and ML-style classification. It contains multiple modules, including a Bukkit implementation, a Minestom port, a core math/physics library, a training pipeline, and a cloud inference service.

---

## Project status

This repo is best understood as:

- a historical prototype
- an ML-assisted anticheat experiment
- a base for learning architecture and detection ideas
- a starting point for custom anti-cheat development

It is not a production-ready anti-cheat and it should not be expected to behave cleanly in a real server without major adjustments.

If you are looking for a plugin to install on a public server and trust in normal gameplay, this project is not the right choice out of the box. It is more useful as a codebase to study, adapt, or rebuild.

---

## What is in this repo?

The project is split into a few major parts:

### 1) X-AC
The main anti-cheat implementation, with the Bukkit plugin and related services.

Key pieces:
- Bukkit plugin entrypoint in [X-AC/bukkit/src/main/java/win/ac/x/X.java](X-AC/bukkit/src/main/java/win/ac/x/X.java)
- Packet listener-based detection pipeline
- Automated punishment logic
- ML/cloud integration
- dataset recording and training support

### 2) millennium
A math / physics / ML-related library used by the project.

This module contains shared logic for:
- math helpers
- vector and shape utilities
- physics simulation
- model-related components

### 3) minestom
A separate implementation for Minestom-based servers.

This is a second platform target, showing the project was intended to be portable beyond Bukkit.

### 4) X-AC-Cloud
A cloud inference service built with gRPC and ONNX runtime.

This module provides remote ML inference support for the anti-cheat. It is designed to run model evaluation outside the plugin process and send verdicts back to the server side.

### 5) training
Python scripts for ML training using recorded gameplay/session data.

The training code reads dataset files and produces model artifacts such as ONNX files for movement and baseline detection.

---

## High-level architecture

The project combines several ideas:

- packet event collection using PacketEvents
- player state tracking
- physics simulation and movement analysis
- combat/aim heuristics and feature extraction
- dataset recording for ML training
- remote model inference via cloud service
- verdict dispatch and punish logic

In other words, it is trying to act like a hybrid anti-cheat: some checks are based on raw packet/position analysis, and some are fed into ML-style models.

---

## Main features and intent

This project includes:

- movement analysis and simulation-based checks
- combat and aim-based detect logic
- dataset caching/recording for ML training
- model export via ONNX-compatible training scripts
- multi-server target support (Bukkit / Minestom)
- cloud-based inference service
- command framework for dataset recording and management

The code also includes a dataset recording guide in [X-AC/RECORDING_GUIDE.md](X-AC/RECORDING_GUIDE.md), which explains how the project expects legitimate and cheat recordings to be captured for model training.

---

## Warning: false positives and production use

This project is not a clean, mature anti-cheat package.

It is intentionally honest to say:

- false positives are possible and likely
- it is not tuned for a general-purpose public server
- it was built as a research / experimentation project
- it should be used as a base, not as a plug-and-play product

If your goal is to run a production anti-cheat on a real server with low false positives, this repo is best used as inspiration or a foundation, not as the final system.

---

## Repository layout

```text
Anticheat-OLD/
├── README.md
├── X-AC/
│   ├── pom.xml
│   ├── LICENSE
│   ├── RECORDING_GUIDE.md
│   ├── bukkit/
│   │   ├── src/main/java/...      # Bukkit plugin implementation
│   │   └── src/main/resources/    # config.yml, plugin.yml, ML resources
│   ├── millennium/
│   │   └── src/main/java/...      # math/physics/core logic
│   ├── minestom/
│   │   └── src/main/java/...      # Minestom port
│   ├── src/
│   │   └── main/java/...          # shared project sources
│   └── training/
│       ├── requirements.txt
│       ├── train_baseline.py
│       └── train_physics.py
├── X-AC-Cloud/
│   ├── pom.xml
│   ├── src/main/java/...          # gRPC + ONNX inference cloud service
│   └── src/main/proto/...        # protobuf definitions
└── .gitignore
```

---

## Build and run

This repo is Maven-based.

To build the Java modules:

```bash
cd /workspaces/Anticheat-OLD/X-AC
mvn clean package
```

Or from the repository root if you want to operate across the multi-module setup:

```bash
cd /workspaces/Anticheat-OLD
mvn clean package
```

The cloud service is a separate Maven project and may be built separately:

```bash
cd /workspaces/Anticheat-OLD/X-AC-Cloud
mvn clean package
```

The training scripts are Python-based and use the libraries listed in the Python requirements file.

---

## Running the plugin

The Bukkit plugin is the most relevant entrypoint for normal server use.

Main plugin details:
- plugin name: X-AC / MX
- Bukkit API target: 1.21
- dependency: PacketEvents
- commands: MX command system
- configuration: config.yml and plugin.yml under the Bukkit resource folder

The plugin boots up, initializes listeners, loads config, connects to the cloud inference service, and starts its detection pipeline.

---

## Training / model notes

The project contains a training workflow for recorded movement and combat data.

The Python files include model logic for:
- physics-based movement classification
- baseline aim/combat detection
- feature windows from recorded sessions
- export to ONNX-style model artifacts

That means the project is trying to move beyond pure heuristics and into ML-based detection, but it is still a very experimental setup.

---

## Recommended use

This repo is best used for:

- reference and learning
- studying packet-based detection patterns
- understanding ML-assisted anti-cheat design
- building a custom anti-cheat from a rough base

This repo is not recommended for:

- production public servers without heavy modification
- trusting it as-is in a modern PvP environment
- expecting low false positive rates with no tuning

---

## Bottom line

This project is old, rough, and intentionally not polished. It is best described as a legacy anti-cheat prototype that can serve as a foundation or a base for future work.

If you want to use it, do so with the expectation that it is experimental, noisy, and likely to require substantial changes and tuning before it becomes even moderately usable.

---

## License

This project includes a permissive project license in the repository root. You are free to use, modify, and distribute this project without requiring attribution or credit to the author of this repository.

The project is provided AS IS, without warranty of any kind, and the authors and copyright holders are not liable for damages or claims arising from its use.

This repository also contains a large amount of code and design lineage derived from the original MX project by kireikosasha:
https://github.com/kireikosasha/MX-Project/

Please review the root license file for the full legal text before using or redistributing the project.

---

## Credits

This project is a fork / derivative work based on the MX / X-AC code lineage and includes substantial historical influence from the original MX project. The project is presented as a legacy codebase and reference project, not as a guaranteed ready-to-run anticheat solution.

It is intentionally open for reuse and modification, without requiring credit or attribution to the author of this repository.
