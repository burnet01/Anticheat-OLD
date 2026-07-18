package win.ac.x.checks.aim.heuristic;

import win.ac.x.api.data.ConfigLabel;
import win.ac.x.api.events.RotationEvent;

import java.util.Map;

public interface HeuristicComponent {
    void process(final RotationEvent event);
    ConfigLabel config();
    void applyConfig(Map<String, Object> fileSection);
}
