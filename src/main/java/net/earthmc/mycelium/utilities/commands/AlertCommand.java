package net.earthmc.mycelium.utilities.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.network.Player;
import net.earthmc.mycelium.api.network.Server;
import net.earthmc.mycelium.utilities.NetworkUtilities;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AlertCommand extends BaseCommand implements SimpleCommand {
    public static final String REDIS_CHANNEL = "vcommands-alert";
    private final ProxyServer proxy;
    private final NetworkUtilities plugin;

    public AlertCommand(NetworkUtilities plugin) {
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

        final Set<Player> recipients = new HashSet<>();
        if ("all".equalsIgnoreCase(invocation.arguments()[0]))
            recipients.addAll(Mycelium.get().network().players());
        else {
            Set<Server> servers = new HashSet<>();
            Set<String> invalidServers = new HashSet<>();

            for (String serverName : invocation.arguments()[0].split(",")) {
                final Server server = Mycelium.get().network().getServerById(serverName);

                if (server == null) {
                    invalidServers.add(serverName);
                } else {
                    servers.add(server);
                    recipients.addAll(server.players());
                }
            }

            if (!invalidServers.isEmpty())
                invocation.source().sendMessage(Component.text("Could not find server(s): " + String.join(", ", invalidServers) + ".", NamedTextColor.GOLD));

            if (!servers.isEmpty())
                invocation.source().sendMessage(Component.text("Broadcasting message to " + servers.size() + " server" + (servers.size() == 1 ? "" : "s") + ".", NamedTextColor.GOLD));
            else
                return;
        }

        final String message = String.join(" ", Arrays.copyOfRange(invocation.arguments(), 1, invocation.arguments().length));
        final Component component = MiniMessage.miniMessage().deserialize(message);

        proxy.getConsoleCommandSource().sendMessage(component);

        for (Player player : recipients) {
            player.sendRichMessage(message);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.alert");
    }
}
