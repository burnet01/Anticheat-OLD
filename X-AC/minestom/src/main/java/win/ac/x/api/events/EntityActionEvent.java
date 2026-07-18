package win.ac.x.api.events;

import win.ac.x.listeners.EntityActionListener;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class EntityActionEvent {
    private EntityActionListener.AbilitiesEnum abilitiesEnum;
}