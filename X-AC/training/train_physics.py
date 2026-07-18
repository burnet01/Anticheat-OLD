"""
train_physics.py
Reads session_*.json.gz files from ../plugins/MX/dataset/
Segments into [20, 10] windows:
  [drift, hspeed, vspeed, emaxh, air, accel, speed_ratio, ground, sprint, speed_var]
Trains a 1D-CNN + BiLSTM classifier, exports to physics_{label}.onnx

Usage:
  python train_physics.py                     # train on ALL cheat labels vs legit
  python train_physics.py --label fly         # train only on "fly" vs legit
  python train_physics.py --label speed       # train only on "speed" vs legit
"""

import argparse, os, sys, json, gzip, glob, math
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import precision_score, recall_score, f1_score, confusion_matrix

# ---------- config ----------
WINDOW = 20
FEATURES = 10
BATCH = 32
EPOCHS = 80
LR = 0.001
MIN_SAMPLES = 50
PATIENCE = 15
VAL_SPLIT = 0.2
SEED = 42
STRIDE = WINDOW // 4        # smaller stride = more windows from same data
AUGMENT_MULTIPLIER = 5      # generate 5x synthetic training samples
NOISE_FRACTION = 0.05       # gaussian noise as fraction of feature std
SPEED_SCALE_RANGE = (0.90, 1.10)  # simulate different server TPS / ping
DRIFT_BIAS_RANGE = (-0.02, 0.02)  # simulate physics sim calibration drift

DATASET_DIR = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "plugins", "MX", "dataset"))
OUTPUT_DIR  = os.path.normpath(os.path.join(os.path.dirname(__file__)))

# ---------- model ----------
class PhysicsModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv1d(FEATURES, 32, kernel_size=3, padding=1),
            nn.BatchNorm1d(32),
            nn.ReLU(),
            nn.Conv1d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm1d(64),
            nn.ReLU(),
        )
        self.lstm = nn.LSTM(64, 64, num_layers=2, bidirectional=True, batch_first=True)
        self.head = nn.Sequential(
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(64, 1),
        )

    def forward(self, x):
        x = x.permute(0, 2, 1)
        x = self.conv(x)
        x = x.permute(0, 2, 1)
        x, _ = self.lstm(x)
        x = x[:, -1, :]
        return self.head(x)


class ModelWithSigmoid(nn.Module):
    def __init__(self, base):
        super().__init__()
        self.base = base

    def forward(self, x):
        logits = self.base(x)
        return torch.sigmoid(logits)


class ModelWithNormSigmoid(nn.Module):
    """Wraps model with built-in normalization + sigmoid.
    The Java code sends raw unnormalized values, so the scaler
    must be baked into the ONNX model."""
    def __init__(self, base, mean, std):
        super().__init__()
        self.base = base
        self.register_buffer("mean", torch.tensor(mean, dtype=torch.float32).view(1, 1, FEATURES))
        self.register_buffer("std", torch.tensor(std, dtype=torch.float32).view(1, 1, FEATURES))

    def forward(self, x):
        x = (x - self.mean) / self.std
        logits = self.base(x)
        return torch.sigmoid(logits)


# ---------- data loading ----------
def load_sessions_by_file(directory, target_label=None, target_letter=None):
    """Load sessions grouped by file so we can split at session level (no data leakage).

    Folder structure:
      dataset/legit/*.json.gz              — legit, no letter
      dataset/legit/A/*.json.gz            — legit with letter A
      dataset/fly/A/*.json.gz              — fly cheat, letter A

    If target_letter is set:
      cheat: loads dataset/{label}/{letter}/*.json.gz
      legit: loads dataset/legit/{letter}/*.json.gz  +  dataset/legit/*.json.gz (root only)
    If target_letter is None:
      cheat: loads dataset/{label}/*.json.gz (root only, no subfolders)
      legit: loads dataset/legit/*.json.gz (root only, no subfolders)
    """
    sessions = []

    # Build cheat file list
    cheat_files = []
    if target_label:
        if target_letter:
            cheat_dir = os.path.join(directory, target_label, target_letter)
            cheat_files = glob.glob(os.path.join(cheat_dir, "*.json.gz"))
        else:
            cheat_dir = os.path.join(directory, target_label)
            cheat_files = glob.glob(os.path.join(cheat_dir, "*.json.gz"))

    # Build legit file list
    legit_files = []
    if target_letter:
        legit_letter_dir = os.path.join(directory, "legit", target_letter)
        legit_files = glob.glob(os.path.join(legit_letter_dir, "*.json.gz"))
        legit_root_dir = os.path.join(directory, "legit")
        legit_files += glob.glob(os.path.join(legit_root_dir, "*.json.gz"))
    else:
        legit_dir = os.path.join(directory, "legit")
        legit_files = glob.glob(os.path.join(legit_dir, "*.json.gz"))

    all_files = [(fp, 1.0) for fp in cheat_files] + [(fp, 0.0) for fp in legit_files]

    skipped = 0
    for fp, is_cheat in all_files:
        with gzip.open(fp, "rt", encoding="utf-8") as f:
            obj = json.load(f)

        hspeeds = obj.get("hspeeds", [])
        vspeeds = obj.get("vspeeds", [])
        drifts   = obj.get("drifts", [])
        sprints  = obj.get("sprints", [])
        emaxhs_rec = obj.get("emaxhs", [])

        if len(hspeeds) < WINDOW:
            continue

        min_len = min(len(hspeeds), len(vspeeds), len(drifts))
        if min_len < WINDOW:
            continue

        hspeeds = hspeeds[:min_len]
        vspeeds = vspeeds[:min_len]
        drifts  = drifts[:min_len]
        if len(sprints) < min_len:
            sprints = sprints + [0.0] * (min_len - len(sprints))
        else:
            sprints = sprints[:min_len]

        # Use recorded emaxh from Java if available, otherwise fall back to vspeed heuristic
        if len(emaxhs_rec) >= min_len:
            emaxh = emaxhs_rec[:min_len]
            # Derive air ticks from vspeed pattern (still needed for air feature)
            air = []
            air_ticks = 0
            for i in range(min_len):
                v = vspeeds[i]
                if v > 0.1 or air_ticks > 0:
                    air_ticks += 1
                    if v < 0.01 and v > -0.01:
                        air_ticks = 0
                else:
                    air_ticks = 0
                air.append(air_ticks)
        else:
            # Legacy data without recorded emaxh — compute from vspeed heuristic
            emaxh = []
            air = []
            air_ticks = 0
            for i in range(min_len):
                v = vspeeds[i]
                if v > 0.1 or air_ticks > 0:
                    air_ticks += 1
                    if v < 0.01 and v > -0.01:
                        air_ticks = 0
                else:
                    air_ticks = 0

                if air_ticks > 0:
                    emaxh.append(0.29 * 0.91 * 0.91)
                else:
                    emaxh.append(0.29)
                air.append(air_ticks)

        accel = [0.0]
        for i in range(1, min_len):
            accel.append(hspeeds[i] - hspeeds[i-1])

        # New derived features
        ground_state = [1.0 if air[i] == 0 else 0.0 for i in range(min_len)]
        speed_ratio = [hspeeds[i] / max(emaxh[i], 0.001) for i in range(min_len)]

        # Rolling speed variance (std dev over last 10 ticks)
        speed_var = [0.0] * min_len
        for i in range(min_len):
            start = max(0, i - 9)
            window = hspeeds[start:i+1]
            if len(window) >= 2:
                mean = sum(window) / len(window)
                var = sum((x - mean) ** 2 for x in window) / len(window)
                speed_var[i] = var ** 0.5

        windows = []
        for start in range(0, min_len - WINDOW + 1, STRIDE):
            window = []
            for t in range(WINDOW):
                idx = start + t
                window.append([
                    drifts[idx],
                    hspeeds[idx],
                    vspeeds[idx],
                    emaxh[idx],
                    air[idx],
                    accel[idx],
                    speed_ratio[idx],
                    ground_state[idx],
                    sprints[idx],
                    speed_var[idx],
                ])
            windows.append(window)

        if windows:
            sessions.append({
                "X": np.array(windows, dtype=np.float32),
                "y": is_cheat,
                "file": os.path.basename(fp),
                "label": "cheat" if is_cheat == 1.0 else "legit",
            })

    return sessions, len(all_files), skipped


def find_best_threshold(y_true, y_probs):
    best_f1 = 0.0
    best_th = 0.5
    for th in np.arange(0.30, 0.95, 0.01):
        preds = (y_probs >= th).astype(int)
        f1 = f1_score(y_true, preds, zero_division=0)
        if f1 > best_f1:
            best_f1 = f1
            best_th = th
    return best_th, best_f1


def augment_window(window, label, rng):
    """Generate one augmented copy of a [WINDOW, FEATURES] sample."""
    w = np.array(window, dtype=np.float32).copy()

    # 1. Gaussian noise on continuous features only (skip binary features 7,8)
    for f in range(FEATURES):
        if f in (7, 8):  # ground_state, sprint_state — binary, don't noise
            continue
        col = w[:, f]
        std = max(np.std(col), 1e-6)
        col += rng.normal(0, NOISE_FRACTION * std, size=col.shape).astype(np.float32)

    # 2. Speed scaling — simulates server TPS variation / player ping differences
    scale = rng.uniform(*SPEED_SCALE_RANGE)
    w[:, 1] *= scale   # hspeed
    w[:, 2] *= scale   # vspeed
    w[:, 3] *= scale   # emaxh
    w[:, 5] *= scale   # acceleration
    w[:, 6] *= scale   # speed_ratio (derived from hspeed/emaxh)
    w[:, 9] *= scale   # speed_variance

    # 3. Drift bias — simulates physics-sim calibration differences
    drift_bias = rng.uniform(*DRIFT_BIAS_RANGE)
    w[:, 0] += drift_bias

    return w, label


def generate_augmented(X_orig, y_orig, multiplier, rng):
    """Generate augmented copies of the training set."""
    X_aug = [X_orig]
    y_aug = [y_orig]
    n = len(X_orig)
    for m in range(multiplier):
        for i in range(n):
            w, l = augment_window(X_orig[i], y_orig[i], rng)
            X_aug.append(w[np.newaxis])
            y_aug.append([l])
    X_out = np.vstack(X_aug).astype(np.float32)
    y_out = np.concatenate(y_aug).astype(np.float32)
    return X_out, y_out


def evaluate(y_true, y_probs, threshold):
    preds = (y_probs >= threshold).astype(int)
    prec = precision_score(y_true, preds, zero_division=0)
    rec = recall_score(y_true, preds, zero_division=0)
    f1 = f1_score(y_true, preds, zero_division=0)
    cm = confusion_matrix(y_true, preds, labels=[0, 1])
    return prec, rec, f1, cm


# ---------- main ----------
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", type=str, default=None, help="Cheat label to train on (e.g. fly, speed)")
    parser.add_argument("--letter", type=str, default=None, help="Letter subfolder (e.g. A, B, C)")
    parser.add_argument("--device", type=str, default=None, help="Force device: cpu or cuda")
    args = parser.parse_args()

    label_name = args.label if args.label else "all"
    letter_name = args.letter.upper() if args.letter else None
    model_suffix = f"_{letter_name}" if letter_name else ""
    display_name = f"{label_name}" + (f" [{letter_name}]" if letter_name else "")

    if args.device:
        device = torch.device(args.device)
    else:
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    print(f"{'='*60}")
    print(f"  Physics Model Training")
    print(f"  Label: {display_name}")
    print(f"{'='*60}")

    print("Loading dataset...")
    sessions, n_files, n_skipped = load_sessions_by_file(DATASET_DIR, args.label, letter_name)
    print(f"  Session files: {n_files}")

    if not sessions:
        print(f"\n  ERROR: No valid sessions found.")
        sys.exit(1)

    cheat_sessions = [s for s in sessions if s["y"] == 1.0]
    legit_sessions = [s for s in sessions if s["y"] == 0.0]

    print(f"  Sessions: {len(sessions)} total ({len(cheat_sessions)} cheat, {len(legit_sessions)} legit)")
    print(f"  Windows:  {sum(len(s['X']) for s in sessions)} total "
          f"({sum(len(s['X']) for s in cheat_sessions)} cheat, "
          f"{sum(len(s['X']) for s in legit_sessions)} legit)")

    if len(cheat_sessions) < 2 or len(legit_sessions) < 2:
        print(f"\n  ERROR: Need at least 2 sessions of each class for session-level split.")
        print(f"  Have {len(cheat_sessions)} cheat, {len(legit_sessions)} legit sessions.")
        print(f"  Record more with: /mx dataset cheat <player> [{args.label or 'label'}]")
        sys.exit(1)

    # Session-level split: validation sessions are NEVER seen during training
    rng = np.random.RandomState(SEED)
    rng.shuffle(cheat_sessions)
    rng.shuffle(legit_sessions)

    n_cheat_val = max(1, int(len(cheat_sessions) * VAL_SPLIT))
    n_legit_val = max(1, int(len(legit_sessions) * VAL_SPLIT))

    val_sessions = cheat_sessions[:n_cheat_val] + legit_sessions[:n_legit_val]
    train_sessions = cheat_sessions[n_cheat_val:] + legit_sessions[n_legit_val:]

    X_train = np.vstack([s["X"] for s in train_sessions])
    y_train = np.array([s["y"] for s in train_sessions for _ in range(len(s["X"]))], dtype=np.float32)
    X_val = np.vstack([s["X"] for s in val_sessions])
    y_val = np.array([s["y"] for s in val_sessions for _ in range(len(s["X"]))], dtype=np.float32)

    print(f"\n  Session-level split (NO data leakage):")
    print(f"    Train sessions: {len(train_sessions)} -> {len(X_train)} windows")
    print(f"    Val sessions:   {len(val_sessions)} -> {len(X_val)} windows")
    print(f"    Val session files: {[s['file'] for s in val_sessions]}")

    # Augment training data only (never validation)
    aug_rng = np.random.RandomState(SEED + 1)
    X_train_aug, y_train_aug = generate_augmented(X_train, y_train, AUGMENT_MULTIPLIER, aug_rng)

    # Fit scaler on real training data only, apply to both
    orig_shape_train = X_train_aug.shape
    orig_shape_val = X_val.shape
    scaler = StandardScaler()
    X_flat_train = scaler.fit_transform(X_train_aug.reshape(-1, FEATURES))
    X_flat_val = scaler.transform(X_val.reshape(-1, FEATURES))
    X_train_final = X_flat_train.reshape(orig_shape_train).astype(np.float32)
    X_val_final = X_flat_val.reshape(orig_shape_val).astype(np.float32)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"\n  Device: {device}")
    print(f"  Train: {len(X_train_final)} (augmented from {len(X_train)}) | Val: {len(X_val_final)} (real, untouched)")

    model = PhysicsModel().to(device)
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=LR, weight_decay=1e-3)

    X_train_t = torch.tensor(X_train_final).to(device)
    y_train_t = torch.tensor(y_train_aug).unsqueeze(1).to(device)
    X_val_t   = torch.tensor(X_val_final).to(device)
    y_val_np  = y_val

    best_val_f1 = 0.0
    best_state = None
    no_improve = 0

    print(f"\n  Epoch |    Loss | Val Acc | Val F1  | Val Prec | Val Rec  | Best F1")
    print(f"  -------|---------|---------|---------|----------|----------|--------")

    for epoch in range(1, EPOCHS + 1):
        model.train()
        perm = torch.randperm(len(X_train_t))
        total_loss = 0.0
        for i in range(0, len(X_train_t), BATCH):
            idx = perm[i:i+BATCH]
            xb = X_train_t[idx]
            yb = y_train_t[idx]
            optimizer.zero_grad()
            out = model(xb)
            loss = criterion(out, yb)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=5.0)
            optimizer.step()
            total_loss += loss.item()

        model.eval()
        with torch.no_grad():
            val_logits = model(X_val_t).squeeze().cpu().numpy()
            val_probs = 1.0 / (1.0 + np.exp(-val_logits))

            # Evaluate at 0.5 threshold for standard metrics
            val_pred = (val_probs >= 0.5).astype(float)
            val_acc = (val_pred == y_val_np).mean()
            val_prec = precision_score(y_val_np, val_pred, zero_division=0)
            val_rec = recall_score(y_val_np, val_pred, zero_division=0)
            val_f1 = f1_score(y_val_np, val_pred, zero_division=0)

        if val_f1 > best_val_f1:
            best_val_f1 = val_f1
            best_state = {k: v.clone() for k, v in model.state_dict().items()}
            no_improve = 0
        else:
            no_improve += 1

        if epoch % 5 == 0 or epoch == 1 or no_improve == 0:
            print(f"  {epoch:6d} | {total_loss:7.4f} | {val_acc:7.4f} | {val_f1:7.4f} | {val_prec:8.4f} | {val_rec:8.4f} | {best_val_f1:7.4f}")

        if no_improve >= PATIENCE:
            print(f"\n  Early stopping at epoch {epoch} (no improvement for {PATIENCE} epochs)")
            break

    if best_state is not None:
        model.load_state_dict(best_state)
        print(f"  Restored best model (F1={best_val_f1:.4f})")

    model.eval()
    with torch.no_grad():
        val_logits = model(X_val_t).squeeze().cpu().numpy()
        val_probs = 1.0 / (1.0 + np.exp(-val_logits))

    best_th, best_th_f1 = find_best_threshold(y_val_np, val_probs)
    prec, rec, f1, cm = evaluate(y_val_np, val_probs, best_th)

    print(f"\n{'='*60}")
    print(f"  FINAL METRICS (on validation set)")
    print(f"{'='*60}")
    print(f"  Accuracy:          {val_acc:.4f}")
    print(f"  Precision:         {prec:.4f}  (of all flagged, {prec*100:.1f}% were real cheats)")
    print(f"  Recall:            {rec:.4f}  (of all real cheats, {rec*100:.1f}% were caught)")
    print(f"  F1 Score:          {f1:.4f}")
    print(f"  Optimal Threshold: {best_th:.2f}")
    print(f"  Confusion Matrix:")
    print(f"    True Negatives:   {cm[0][0]:4d}  (legit correctly not flagged)")
    print(f"    False Positives:  {cm[0][1]:4d}  (legit WRONGLY flagged)")
    print(f"    False Negatives:  {cm[1][0]:4d}  (cheat WRONGLY missed)")
    print(f"    True Positives:   {cm[1][1]:4d}  (cheat correctly flagged)")

    fp_rate = cm[0][1] / max(1, cm[0].sum())
    fn_rate = cm[1][0] / max(1, cm[1].sum())
    print(f"  False Positive Rate: {fp_rate:.4f} ({fp_rate*100:.1f}%)")
    print(f"  False Negative Rate: {fn_rate:.4f} ({fn_rate*100:.1f}%)")

    if fp_rate > 0.05:
        print(f"\n  WARNING: False positive rate is {fp_rate*100:.1f}% (>5%)")
        print(f"  Consider recording more legit data or raising threshold.")
    if best_th < 0.5:
        print(f"\n  WARNING: Optimal threshold is very low ({best_th:.2f})")
        print(f"  Model may be undertrained. Record more diverse data.")
    print(f"{'='*60}")

    # Bake the scaler into the model so Java sends raw values, model normalizes internally
    scaler_mean = scaler.mean_.astype(np.float32)
    scaler_std = scaler.scale_.astype(np.float32)
    export_device = torch.device("cpu")
    export_model = model.to(export_device)
    deploy_model = ModelWithNormSigmoid(export_model, scaler_mean, scaler_std)
    deploy_model.eval()
    deploy_model.to(export_device)

    output_file = os.path.join(OUTPUT_DIR, f"physics_{label_name}{model_suffix}.onnx")
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    dummy_input = torch.randn(1, WINDOW, FEATURES).to(export_device)
    try:
        torch.onnx.export(
            deploy_model, dummy_input, output_file,
            input_names=["input"], output_names=["output"],
            opset_version=18,
            dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
            dynamo=False,
        )
    except TypeError:
        torch.onnx.export(
            deploy_model, dummy_input, output_file,
            input_names=["input"], output_names=["output"],
            opset_version=18,
            dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
        )

    print(f"\n  Model exported: {os.path.abspath(output_file)}")
    print(f"  Best val F1: {best_val_f1:.4f}")
    print(f"  Exported WITH sigmoid + built-in normalization")
    print(f"  (Java code sends RAW values — model normalizes internally)")
    print(f"  Recommended deploy threshold: {best_th:.2f}")
    print(f"  Deploy: place at plugins/MX/models/physics_{label_name}{model_suffix}.onnx")
    print(f"  Then restart the server.")
    print(f"{'='*60}")

if __name__ == "__main__":
    main()
