package revxrsal.commands.minestom.sender;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.list.AnnotationList;
import revxrsal.commands.minestom.actor.MinestomCommandActor;
import revxrsal.commands.minestom.annotation.CommandPermission;
import net.minestom.server.entity.Player;
import twizzy.tech.player.Profile;

public enum MinestomPermissionFactory implements revxrsal.commands.command.CommandPermission.Factory<MinestomCommandActor> {
    INSTANCE;

    @Override
    public @Nullable revxrsal.commands.command.CommandPermission<MinestomCommandActor> create(@NotNull AnnotationList annotations, @NotNull Lamp<MinestomCommandActor> lamp) {
        CommandPermission permissionAnn = annotations.get(CommandPermission.class);
        if (permissionAnn == null)
            return null;

        String requiredPermission = permissionAnn.value();


        return actor -> {
            Object sender = actor.sender();

            // If not a player (console, etc.), grant all permissions
            if (!(sender instanceof Player)) {
                return true;
            }

            // Get the player's UUID and profile
            Player player = (Player) sender;
            java.util.UUID uuid = player.getUuid();
            Profile profile = Profile.Companion.getProfile(uuid);

            // If profile doesn't exist, deny permission
            if (profile == null) {
                return false;
            }

            // Check if the player has the required permission using our Profile system
            return profile.hasEffectivePermission(requiredPermission);
        };
    }
}
