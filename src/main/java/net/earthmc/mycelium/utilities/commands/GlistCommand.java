package net.earthmc.mycelium.utilities.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ProxyServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.network.Player;
import net.earthmc.mycelium.api.network.Server;
import net.earthmc.mycelium.utilities.NetworkUtilities;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class GlistCommand {
    private static final String SERVER_ARG = "server";

    private final ProxyServer server;
    private final NetworkUtilities plugin;

    public GlistCommand(ProxyServer server, NetworkUtilities plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    /**
     * Registers this command.
     */
    public void register() {
        final LiteralArgumentBuilder<CommandSource> rootNode = BrigadierCommand
                .literalArgumentBuilder("glist")
                .requires(source ->
                        source.getPermissionValue("velocity.command.glist") == Tristate.TRUE)
                .executes(this::totalCount);
        final ArgumentCommandNode<CommandSource, String> serverNode = BrigadierCommand
                .requiredArgumentBuilder(SERVER_ARG, StringArgumentType.string())
                .suggests((context, builder) -> {
                    final String argument = context.getArguments().containsKey(SERVER_ARG)
                            ? context.getArgument(SERVER_ARG, String.class)
                            : "";
                    for (Server server : Mycelium.get().network().servers()) {
                        final String serverName = server.name();
                        if (serverName.regionMatches(true, 0, argument, 0, argument.length())) {
                            builder.suggest(serverName);
                        }
                    }
                    if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
                        builder.suggest("all");
                    }
                    return builder.buildFuture();
                })
                .executes(this::serverCount)
                .build();
        rootNode.then(serverNode);
        final BrigadierCommand command = new BrigadierCommand(rootNode);
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder(command)
                        .plugin(plugin)
                        .build(),
                command
        );
    }

    private int totalCount(final CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        sendTotalProxyCount(source);
        source.sendMessage(
                Component.translatable("velocity.command.glist-view-all", NamedTextColor.YELLOW));
        return 1;
    }

    private int serverCount(final CommandContext<CommandSource> context) {
        final CommandSource source = context.getSource();
        final String serverName = getString(context, SERVER_ARG);
        if (serverName.equalsIgnoreCase("all")) {
            for (final Server server : Mycelium.get().network().servers()) {
                sendServerPlayers(source, server, true);
            }
            sendTotalProxyCount(source);
        } else {
            final Server registeredServer = Mycelium.get().network().getServerById(serverName);
            if (registeredServer == null) {
                source.sendMessage(Component.translatable("velocity.command.server-does-not-exist", NamedTextColor.RED).arguments(Component.text(serverName)));
                return -1;
            }
            sendServerPlayers(source, registeredServer, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void sendTotalProxyCount(CommandSource target) {
        final int online = Mycelium.get().network().playerCount();
        final TranslatableComponent.Builder msg = Component.translatable()
                .key(online == 1
                        ? "velocity.command.glist-player-singular"
                        : "velocity.command.glist-player-plural"
                ).color(NamedTextColor.YELLOW)
                .arguments(Component.text(Integer.toString(online), NamedTextColor.GREEN));
        target.sendMessage(msg.build());
    }

    private void sendServerPlayers(final CommandSource target,
                                   final Server server, final boolean fromAll) {
        final List<Player> onServer = ImmutableList.copyOf(server.players());
        if (onServer.isEmpty() && fromAll) {
            return;
        }

        final TextComponent.Builder builder = Component.text()
                .append(Component.text("[" + server.name() + "] ",
                        NamedTextColor.DARK_AQUA))
                .append(Component.text("(" + onServer.size() + ")", NamedTextColor.GRAY))
                .append(Component.text(": "))
                .resetStyle();

        for (int i = 0; i < onServer.size(); i++) {
            final Player player = onServer.get(i);
            builder.append(Component.text(player.username()));

            if (i + 1 < onServer.size()) {
                builder.append(Component.text(", "));
            }
        }

        target.sendMessage(builder.build());
    }
}
