package win.ac.x.api.events;

import lombok.Getter;
import lombok.Setter;
import net.minestom.server.entity.Player;

@Getter
public final class MXFlagEvent {

    private final Player player;
    private final String check;
    private final String component;
    private final String info;
    private final float vl;
    private final double vlLimit;
    @Setter
    private boolean cancelled;

    public MXFlagEvent(Player player, String check, String component, String info, float vl, double vlLimit) {
        this.player = player;
        this.check = check;
        this.component = component;
        this.info = info;
        this.vl = vl;
        this.vlLimit = vlLimit;
    }
}