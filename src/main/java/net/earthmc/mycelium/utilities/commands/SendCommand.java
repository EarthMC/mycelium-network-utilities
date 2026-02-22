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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SendCommand extends BaseCommand implements SimpleCommand {
    private final Set<String> sources = new LinkedHashSet<>();
    private final Set<String> targets = new LinkedHashSet<>();
    private final ProxyServer proxy;

    public SendCommand(NetworkUtilities plugin) {
        this.proxy = plugin.proxy();

        sources.addAll(Arrays.asList("all", "current"));
        for (RegisteredServer server : proxy.getAllServers()) {
            targets.add(server.getServerInfo().getName().toLowerCase());
            sources.add(server.getServerInfo().getName().toLowerCase());
        }

        for (final Server server : Mycelium.api().network().servers()) {
            targets.add(server.name());
            sources.add(server.name());
        }
    }

    @Override
    public void execute(Invocation invocation) {
        final CommandSource source = invocation.source();

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

        final Mycelium mycelium = Mycelium.api();

        List<String> targets = new ArrayList<>();

        for (String target : invocation.arguments()[1].split(",")) {
            final Server server = mycelium.network().getServerById(target);
            final RegisteredServer registeredServer = proxy.getServer(target).orElse(null);

            if ((server == null && registeredServer == null) || !hasPermissionForServer(invocation.source(), target)) {
                source.sendMessage(Component.text(target + " is not a valid target server.", NamedTextColor.RED));
                return;
            }

            targets.add(target);
        }

        Collection<UUID> toSend = switch (invocation.arguments()[0].toLowerCase()) {
            case "current" -> {
                final com.velocitypowered.api.proxy.Player player = (com.velocitypowered.api.proxy.Player) source;
                final RegisteredServer current = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);
                if (current == null) {
                    source.sendMessage(Component.text("You are currently not connected to a server!", NamedTextColor.RED));
                    yield Collections.emptyList();
                }

                final Server myceliumServer = mycelium.network().getServerById(current.getServerInfo().getName());
                if (myceliumServer == null) {
                    yield current.getPlayersConnected().stream().map(com.velocitypowered.api.proxy.Player::getUniqueId).toList();
                } else {
                    yield myceliumServer.players().stream().map(Player::uuid).toList();
                }
            }
            case "all" -> {
                final Set<UUID> allPlayers = new HashSet<>();

                mycelium.network().players().forEach(player -> allPlayers.add(player.uuid()));
                proxy.getAllPlayers().forEach(player -> allPlayers.add(player.getUniqueId()));

                yield allPlayers;
            }
            default -> {
                Server from = mycelium.network().getServerById(invocation.arguments()[0]);
                RegisteredServer fromVelocity = proxy.getServer(invocation.arguments()[0]).orElse(null);

                if (from == null && fromVelocity == null) {
                    // Send a specific player to a server
                    Player player = mycelium.network().getPlayerByName(invocation.arguments()[0]);
                    @Nullable String playerServerName = null;
                    UUID playerUUID;

                    if (player == null) {
                        final com.velocitypowered.api.proxy.Player velocityPlayer = proxy.getPlayer(invocation.arguments()[0]).orElse(null);
                        if (velocityPlayer == null) {
                            source.sendMessage(Component.text("Invalid argument! Usage: /send [all/current/player/server] [server(s)].", NamedTextColor.RED));
                            yield Collections.emptyList();
                        }

                        playerUUID = velocityPlayer.getUniqueId();
                        playerServerName = velocityPlayer.getCurrentServer().map(server -> server.getServerInfo().getName()).orElse(null);
                    } else {
                        playerUUID = player.uuid();
                        final Server playerServer = player.server();

                        if (playerServer != null) {
                            playerServerName = playerServer.name();
                        }
                    }


                    if (playerServerName == null) {
                        source.sendMessage(Component.text(invocation.arguments()[0] + " is not connected to any server.", NamedTextColor.RED));
                        yield Collections.emptyList();
                    }

                    if (targets.contains(playerServerName)) {
                        source.sendMessage(Component.text(invocation.arguments()[0] + " is already connected to " + format(playerServerName) + ".", NamedTextColor.RED));
                        yield Collections.emptyList();
                    } else {
                        yield Collections.singleton(playerUUID);
                    }
                }

                // Send all players on this server to a server
                final Set<UUID> connected = new HashSet<>();
                final String fromName;

                if (from == null) {
                    fromVelocity.getPlayersConnected().forEach(player -> connected.add(player.getUniqueId()));
                    fromName = fromVelocity.getServerInfo().getName();
                } else {
                    from.players().forEach(player -> connected.add(player.uuid()));
                    fromName = from.name();
                }

                if (connected.isEmpty()) {
                    source.sendMessage(Component.text("No players are currently online on '" + format(fromName) + "'.", NamedTextColor.RED));
                }

                yield connected;
            }
        };

        if (toSend.isEmpty()) {
            source.sendMessage(Component.text("No players have been sent.", NamedTextColor.RED));
            return;
        }

        String formattedTargets = targets.stream().map(this::format).collect(Collectors.joining(", "));
        source.sendMessage(Component.text("Sending " + toSend.size() + " player" + (toSend.size() == 1 ? "" : "s") + " to " + formattedTargets + "...", NamedTextColor.GREEN));

        int sentPlayers = 0;

        for (UUID playerUUID : toSend) {
            final String targetServerName = targets.get(sentPlayers % targets.size());

            final Player player = mycelium.network().getPlayerByUUID(playerUUID);
            if (player == null) {
                final com.velocitypowered.api.proxy.Player velocityPlayer = proxy.getPlayer(playerUUID).orElse(null);
                if (velocityPlayer == null) {
                    continue;
                }

                final ServerConnection playerServer = velocityPlayer.getCurrentServer().orElse(null);
                if (playerServer != null && playerServer.getServerInfo().getName().equals(targetServerName)) {
                    continue;
                }

                final RegisteredServer target = proxy.getServer(targetServerName).orElse(null);
                if (target == null) {
                    continue;
                }

                velocityPlayer.createConnectionRequest(target).fireAndForget();

                velocityPlayer.sendRichMessage("<gold>Summoned to " + format(targetServerName) + " by " + format(source));
            } else {
                final Server playerServer = player.server();

                if (playerServer != null && targetServerName.equals(playerServer.name())) {
                    continue;
                }

                final Server target = mycelium.network().getServerById(targetServerName);
                if (target == null) {
                    continue;
                }

                sentPlayers++;

                player.sendRichMessage("<gold>Summoned to " + format(targetServerName) + " by " + format(source));
                player.transferToServer(target);
            }

            sentPlayers++;
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
            case 0 -> List.copyOf(sources);
            case 1 -> {
                String arg = invocation.arguments()[0].toLowerCase();
                List<String> filtered = filterByStart(sources, arg);

                if (filtered.isEmpty()) {
                    yield Mycelium.api().network().players().stream().map(Player::username).filter(name -> name.toLowerCase().startsWith(arg)).toList();
                } else {
                    yield filtered;
                }
            }
            case 2 -> filterByPermission(invocation.source(), targets, "velocity.command.server.", invocation.arguments()[1]);
            default -> Collections.emptyList();
        };
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.send");
    }
}
