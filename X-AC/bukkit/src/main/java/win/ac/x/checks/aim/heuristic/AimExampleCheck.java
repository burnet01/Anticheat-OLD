package win.ac.x.checks.aim.heuristic;

import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.RotationEvent;
import win.ac.x.api.player.PlayerProfile;
import win.ac.x.checks.aim.AimHeuristicCheck;
import win.ac.x.vectors.Vec2f;

import java.util.Map;
import java.util.TreeMap;

public final class AimExampleCheck implements HeuristicComponent {
    private final AimHeuristicCheck check;
    private Vec2f oldDelta = new Vec2f(0, 0), oldAbsDelta = new Vec2f(0, 0);
    private int buffer = 0;
    private Map<String, Object> localCfg = new TreeMap<>();
    public AimExampleCheck(final AimHeuristicCheck check) {
        this.check = check;
    }

    @Override
    public ConfigLabel config() {
        localCfg.put("vl", 5);
        return new ConfigLabel("example_check", localCfg);
    }
    @Override
    public void applyConfig(Map<String, Object> params) {
        localCfg = params;
    }

    @Override
    public void process(final RotationEvent event) {
        //if (check.getProfile().ignoreCinematic()) return;
        final PlayerProfile profile = check.getProfile();
        final Vec2f delta = event.getDelta(), absDelta = event.getAbsDelta();
        { // check logic

        }
        oldDelta = delta;
        oldAbsDelta = absDelta;
    }

    private float getNumCfg(String key) {
        return ((Number) localCfg.get(key)).floatValue();
    }
}