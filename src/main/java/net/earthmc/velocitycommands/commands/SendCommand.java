package net.earthmc.velocitycommands.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SendCommand extends BaseCommand implements SimpleCommand {

    private final List<String> tabCompletes = new ArrayList<>();
    private final List<String> servers = new ArrayList<>();
    private final ProxyServer proxy;

    public SendCommand(ProxyServer proxy) {
        this.proxy = proxy;

        tabCompletes.addAll(Arrays.asList("all", "current"));
        for (RegisteredServer server : proxy.getAllServers()) {
            servers.add(server.getServerInfo().getName().toLowerCase());
            tabCompletes.add(server.getServerInfo().getName().toLowerCase());
        }
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("velocitycommands.send")) {
            source.sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return;
        }

        if (invocation.arguments().length < 2) {
            source.sendMessage(Component.text("Not enough arguments! Usage: /send [all/current/player/server] [server(s)].", NamedTextColor.RED));
            return;
        }

        if (source instanceof ConsoleCommandSource && invocation.arguments()[0].equalsIgnoreCase("current")) {
            source.sendMessage(Component.text("The 'current' argument cannot be used as console.", NamedTextColor.RED));
            return;
        }

        List<RegisteredServer> targets = new ArrayList<>();

        for (String target : invocation.arguments()[1].split(",")) {
            Optional<RegisteredServer> optTarget = proxy.getServer(target);

            if (optTarget.isEmpty() || !hasPermissionForServer(invocation.source(), target)) {
                source.sendMessage(Component.text(target + " is not a valid target server.", NamedTextColor.RED));
                return;
            }

            targets.add(optTarget.get());
        }

        Collection<Player> toSend = switch (invocation.arguments()[0].toLowerCase()) {
            case "current" -> {
                Player player = (Player) source;
                yield player.getCurrentServer().get().getServer().getPlayersConnected();
            }
            case "all" -> proxy.getAllPlayers();
            default -> {
                Optional<RegisteredServer> from = proxy.getServer(invocation.arguments()[0]);

                if (from.isEmpty()) {
                    Optional<Player> player = proxy.getPlayer(invocation.arguments()[0]);
                    if (player.isEmpty()) {
                        source.sendMessage(Component.text("Invalid argument! Usage: /send [all/current/player/server] [server(s)].", NamedTextColor.RED));
                        yield Collections.emptyList();
                    }

                    Optional<RegisteredServer> playerServer = player.get().getCurrentServer().map(ServerConnection::getServer);

                    if (playerServer.isEmpty()) {
                        source.sendMessage(Component.text(player.get().getUsername() + " is not connected to any servers.", NamedTextColor.RED));
                        yield Collections.emptyList();
                    }

                    if (targets.contains(playerServer.get())) {
                        source.sendMessage(Component.text(player.get().getUsername() + " is already connected to " + format(playerServer.get()) + ".", NamedTextColor.RED));
                        yield Collections.emptyList();
                    } else
                        yield Collections.singleton(player.get());
                }

                if (from.get().getPlayersConnected().isEmpty())
                    source.sendMessage(Component.text("No players are currently online on '" + format(from.get()) + "'.", NamedTextColor.RED));

                yield from.get().getPlayersConnected();
            }
        };

        if (toSend.isEmpty()) {
            source.sendMessage(Component.text("No players have been sent.", NamedTextColor.RED));
            return;
        }

        String formattedTargets = targets.stream().map(this::format).collect(Collectors.joining(", "));
        source.sendMessage(Component.text("Sending " + toSend.size() + " player" + (toSend.size() == 1 ? "" : "s") + " to " + formattedTargets + "...", NamedTextColor.GREEN));

        int index = 0;

        for (Player player : toSend) {
            Optional<RegisteredServer> server = player.getCurrentServer().map(ServerConnection::getServer);
            if (server.isPresent() && targets.get(index).equals(server.get()))
                continue;

            RegisteredServer target = targets.get(index);

            index++;
            if (index >= targets.size())
                index = 0;

            player.sendMessage(Component.text("Summoned to " + format(target) + " by " + format(source), NamedTextColor.GOLD));
            player.createConnectionRequest(target).fireAndForget();
        }
    }

    private String format(RegisteredServer server) {
        String name = server.getServerInfo().getName();

        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String format(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : "Console";
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return switch (invocation.arguments().length) {
            case 0 -> tabCompletes;
            case 1 -> {
                String arg = invocation.arguments()[0].toLowerCase();
                List<String> filtered = filterByStart(tabCompletes, arg);

                if (filtered.isEmpty())
                    yield proxy.getAllPlayers().stream().map(Player::getUsername).filter(name -> name.toLowerCase().startsWith(arg)).collect(Collectors.toList());
                else
                    yield filtered;
            }
            case 2 -> filterByPermission(invocation.source(), servers, "velocity.command.server.", invocation.arguments()[1]);
            default -> Collections.emptyList();
        };
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.send");
    }
}
