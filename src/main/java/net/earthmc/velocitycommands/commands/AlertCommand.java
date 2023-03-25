package net.earthmc.velocitycommands.commands;

import com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI;
import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.velocitycommands.VelocityCommands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AlertCommand extends BaseCommand implements SimpleCommand {
    public static final String REDIS_CHANNEL = "vcommands-alert";
    private final ProxyServer proxy;
    private final VelocityCommands plugin;

    public AlertCommand(VelocityCommands plugin) {
        this.plugin = plugin;
        this.proxy = plugin.proxy();
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("velocitycommands.alert")) {
            invocation.source().sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return;
        }

        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Component.text("Not enough arguments! Usage: /alert [server(s)|all] [message]", NamedTextColor.RED));
            return;
        }

        final Set<UUID> recipients = new HashSet<>();
        if ("all".equalsIgnoreCase(invocation.arguments()[0]))
            recipients.addAll(plugin.getAllPlayers());
        else {
            Set<RegisteredServer> servers = new HashSet<>();
            Set<String> invalidServers = new HashSet<>();

            for (String server : invocation.arguments()[0].split(",")) {
                Optional<RegisteredServer> optServer = proxy.getServer(server);

                if (optServer.isEmpty()) {
                    invalidServers.add(server);
                } else {
                    servers.add(optServer.get());
                    recipients.addAll(plugin.getPlayersOnServer(optServer.get()));
                }
            }

            if (!invalidServers.isEmpty())
                invocation.source().sendMessage(Component.text("The following servers are invalid: " + String.join(", ", invalidServers) + ".", NamedTextColor.GOLD));

            if (!servers.isEmpty())
                invocation.source().sendMessage(Component.text("Broadcasting message to " + servers.size() + " server" + (servers.size() == 1 ? "" : "s") + ".", NamedTextColor.GOLD));
            else
                return;
        }

        final String message = String.join(", ", Arrays.copyOfRange(invocation.arguments(), 1, invocation.arguments().length));
        final Component component = MiniMessage.miniMessage().deserialize(message);

        proxy.getConsoleCommandSource().sendMessage(component);

        for (UUID uuid : recipients) {
            proxy.getPlayer(uuid).ifPresentOrElse(player -> player.sendMessage(component), () -> {
                if (plugin.usingRedisBungee())
                    RedisBungeeAPI.getRedisBungeeApi().sendChannelMessage(REDIS_CHANNEL, String.join(",", uuid.toString(), message));
            });
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.alert");
    }

    @Subscribe
    public void onPubSub(PubSubMessageEvent event) {
        if (!REDIS_CHANNEL.equals(event.getChannel()))
            return;

        final String[] data = event.getMessage().split(",", 2);
        proxy.getPlayer(UUID.fromString(data[0])).ifPresent(player -> player.sendMessage(MiniMessage.miniMessage().deserialize(data[1])));
    }
}
