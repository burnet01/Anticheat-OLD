package win.ac.x.api;

import win.ac.x.api.data.ConfigLabel;

import java.util.Map;

public interface PacketCheckHandler extends Cloneable {
    void event(Object event);
    void applyConfig(Map<String, Object> params);
    Map<String, Object> getConfig();
    ConfigLabel config();
}
