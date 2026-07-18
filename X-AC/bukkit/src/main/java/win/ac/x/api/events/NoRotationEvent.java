package win.ac.x.api.events;

import win.ac.x.api.player.PlayerProfile;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class NoRotationEvent {
    private PlayerProfile profile;
}
