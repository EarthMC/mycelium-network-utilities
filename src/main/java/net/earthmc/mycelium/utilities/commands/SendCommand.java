package net.earthmc.mycelium.utilities.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.network.Player;
import net.earthmc.mycelium.api.network.Server;
import net.earthmc.mycelium.utilities.NetworkUtilities;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SendCommand extends BaseCommand implements SimpleCommand {
    private final List<String> tabCompletes = new ArrayList<>();
    private final List<String> servers = new ArrayList<>();
    private final ProxyServer proxy;
    private final NetworkUtilities plugin;

    public SendCommand(NetworkUtilities plugin) {
        this.plugin = plugin;
        this.proxy = plugin.proxy();

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

        List<Server> targets = new ArrayList<>();

        for (String target : invocation.arguments()[1].split(",")) {
            final Server server = Mycelium.get().network().getServerById(target);

            if (server == null || !hasPermissionForServer(invocation.source(), target)) {
                source.sendMessage(Component.text(target + " is not a valid target server.", NamedTextColor.RED));
                return;
            }

            targets.add(server);
        }

        Collection<Player> toSend = switch (invocation.arguments()[0].toLowerCase()) {
            case "current" -> {
                final com.velocitypowered.api.proxy.Player player = (com.velocitypowered.api.proxy.Player) source;
                final RegisteredServer current = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);
                if (current == null) {
                    source.sendMessage(Component.text("You are currently not connected to a server!", NamedTextColor.RED));
                    yield Collections.emptyList();
                }

                yield Mycelium.get().network().getServerById(current.getServerInfo().getName()).players();
            }
            case "all" -> Mycelium.get().network().players();
            default -> {
                Server from = Mycelium.get().network().getServerById(invocation.arguments()[0]);

                if (from == null) {
                    // Send a specific player to a server
                    Player player = Mycelium.get().network().getPlayerByName(invocation.arguments()[0]);
                    if (player == null) {
                        source.sendMessage(Component.text("Invalid argument! Usage: /send [all/current/player/server] [server(s)].", NamedTextColor.RED));
                        yield Collections.emptyList();
                    }

                    final Server playerServer = player.server();

                    if (playerServer == null) {
                        source.sendMessage(Component.text(invocation.arguments()[0] + " is not connected to any servers.", NamedTextColor.RED));
                        yield Collections.emptyList();
                    }

                    if (targets.contains(playerServer)) {
                        source.sendMessage(Component.text(invocation.arguments()[0] + " is already connected to " + format(playerServer.name()) + ".", NamedTextColor.RED));
                        yield Collections.emptyList();
                    } else
                        yield Collections.singleton(player);
                }

                // Send all players on this server to a server
                Collection<Player> connected = from.players();
                if (connected.isEmpty())
                    source.sendMessage(Component.text("No players are currently online on '" + format(from.name()) + "'.", NamedTextColor.RED));

                yield connected;
            }
        };

        if (toSend.isEmpty()) {
            source.sendMessage(Component.text("No players have been sent.", NamedTextColor.RED));
            return;
        }

        String formattedTargets = targets.stream().map(Server::name).map(this::format).collect(Collectors.joining(", "));
        source.sendMessage(Component.text("Sending " + toSend.size() + " player" + (toSend.size() == 1 ? "" : "s") + " to " + formattedTargets + "...", NamedTextColor.GREEN));

        int index = 0;

        for (Player player : toSend) {
            final Server playerServer = player.server();
            if (playerServer == null || targets.get(index).equals(playerServer))
                continue;

            Server target = targets.get(index);

            index++;
            if (index >= targets.size())
                index = 0;

            player.sendRichMessage("<gold>Summoned to " + format(target.name()) + " by " + format(source));
            player.transferToServer(target);
        }
    }

    private String format(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String format(CommandSource source) {
        return source instanceof com.velocitypowered.api.proxy.Player player ? player.getUsername() : "Console";
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return switch (invocation.arguments().length) {
            case 0 -> tabCompletes;
            case 1 -> {
                String arg = invocation.arguments()[0].toLowerCase();
                List<String> filtered = filterByStart(tabCompletes, arg);

                if (filtered.isEmpty())
                    yield Mycelium.get().network().players().stream().map(Player::username).filter(name -> name.toLowerCase().startsWith(arg)).toList();
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
