package net.earthmc.velocitycommands.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
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
            source.sendMessage(Component.text("Not enough arguments! Usage: /send [all/current/player/server] [server].", NamedTextColor.RED));
            return;
        }

        if (source instanceof ConsoleCommandSource && invocation.arguments()[0].equalsIgnoreCase("current")) {
            source.sendMessage(Component.text("The 'current' argument cannot be used as console.", NamedTextColor.RED));
            return;
        }

        Optional<RegisteredServer> optTarget = proxy.getServer(invocation.arguments()[1]);
        if (optTarget.isEmpty() || !hasPermissionForServer(invocation.source(), invocation.arguments()[1])) {
            source.sendMessage(Component.text(invocation.arguments()[1] + " is not a valid target server.", NamedTextColor.RED));
            return;
        }

        RegisteredServer target = optTarget.get();

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
                        source.sendMessage(Component.text("Invalid argument! Usage: /send [all/current/player/server] [target].", NamedTextColor.RED));
                        yield Collections.emptyList();
                    } else if (target.equals(player.get().getCurrentServer().get().getServer())) {
                        source.sendMessage(Component.text(player.get().getUsername() + " is already connected to " + target.getServerInfo().getName() + ".", NamedTextColor.RED));
                        yield Collections.emptyList();
                    }
                    yield Collections.singleton(player.get());
                }
                if (from.get().getPlayersConnected().isEmpty())
                    source.sendMessage(Component.text("No players are currently online on '" + format(from.get()) + "'.", NamedTextColor.RED));

                yield from.get().getPlayersConnected();
            }
        };

        if (!toSend.isEmpty())
            toSend = toSend.stream().filter(player -> !player.getCurrentServer().get().getServer().equals(target)).collect(Collectors.toSet());

        if (toSend.isEmpty()) {
            source.sendMessage(Component.text("No players have been sent.", NamedTextColor.RED));
            return;
        }

        source.sendMessage(Component.text("Sending " + toSend.size() + " player" + (toSend.size() == 1 ? "" : "s") + " to " + format(target) + "...", NamedTextColor.GREEN));

        for (Player player : toSend) {
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
            case 2 -> filterByPermission(servers, invocation.arguments()[1], invocation.source(), "velocity.command.server.");
            default -> Collections.emptyList();
        };
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.send");
    }
}
