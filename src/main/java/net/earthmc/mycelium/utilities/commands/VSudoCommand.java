package net.earthmc.mycelium.utilities.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class VSudoCommand extends BaseCommand implements SimpleCommand {
    private final ProxyServer server;

    public VSudoCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("velocitycommands.vsudo")) {
            invocation.source().sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return;
        }

        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Component.text("Not enough arguments! Usage: /vsudo [player] [command]", NamedTextColor.RED));
            return;
        }

        Optional<Player> optPlayer = server.getPlayer(invocation.arguments()[0]);
        if (optPlayer.isEmpty()) {
            invocation.source().sendMessage(Component.text("Could not find a player named " + invocation.arguments()[0] + " connected to the proxy.", NamedTextColor.RED));
            return;
        }

        String command = String.join(" ", Arrays.copyOfRange(invocation.arguments(), 1, invocation.arguments().length));
        if (command.startsWith("/"))
            command = command.substring(1);

        if (command.isEmpty()) {
            invocation.source().sendMessage(Component.text("Command cannot be empty.", NamedTextColor.RED));
            return;
        }

        server.getCommandManager().executeAsync(optPlayer.get(), command);
        invocation.source().sendMessage(Component.text("Forcing " + optPlayer.get().getUsername() + " to run /" + command + ".", NamedTextColor.GOLD));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return switch (invocation.arguments().length) {
            case 0 -> server.getAllPlayers().stream().map(Player::getUsername).toList();
            case 1 -> filterByStart(server.getAllPlayers().stream().map(Player::getUsername).toList(), invocation.arguments()[0]);
            default -> Collections.emptyList();
        };
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.vsudo");
    }
}
