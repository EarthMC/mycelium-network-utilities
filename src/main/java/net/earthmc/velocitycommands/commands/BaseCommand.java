package net.earthmc.velocitycommands.commands;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class BaseCommand {
    public List<String> filterByStart(Collection<String> strings, String startingWith) {
        return strings.stream().filter(s -> s.regionMatches(true, 0, startingWith, 0, startingWith.length())).toList();
    }

    public List<String> filterByPermission(@NotNull PermissionSubject subject, Collection<String> collection, String permPrefix, @Nullable String startingWith) {
        List<String> strings = new ArrayList<>(collection);
        strings.removeIf(string -> !hasPrefixedPermission(subject, permPrefix, string));
        return startingWith != null ? filterByStart(strings, startingWith) : strings;
    }

    public boolean hasPrefixedPermission(@NotNull PermissionSubject subject, @NotNull String permPrefix, @Nullable String arg) {
        if (arg != null && subject.getPermissionValue(permPrefix + arg) == Tristate.FALSE)
            return false;

        return subject.hasPermission(permPrefix + "*") || (arg != null && subject.hasPermission(permPrefix + arg.toLowerCase(Locale.ROOT)));
    }

    public boolean hasPermissionForServer(@NotNull PermissionSubject subject, @Nullable String serverName) {
        return hasPrefixedPermission(subject, "velocity.command.server.", serverName);
    }
}
