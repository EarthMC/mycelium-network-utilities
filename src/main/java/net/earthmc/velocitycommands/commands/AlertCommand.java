package net.earthmc.velocitycommands.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class AlertCommand extends BaseCommand implements SimpleCommand {
    private final ProxyServer proxy;

    public AlertCommand(ProxyServer proxy) {
        this.proxy = proxy;
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

        Audience recipients;
        if ("all".equalsIgnoreCase(invocation.arguments()[0]))
            recipients = proxy;
        else {
            Set<RegisteredServer> servers = new HashSet<>();
            Set<String> invalidServers = new HashSet<>();

            for (String server : invocation.arguments()[0].split(",")) {
                Optional<RegisteredServer> optServer = proxy.getServer(server);

                if (optServer.isEmpty())
                    invalidServers.add(server);
                else
                    servers.add(optServer.get());
            }

            if (!invalidServers.isEmpty())
                invocation.source().sendMessage(Component.text("The following servers are invalid: " + String.join(", ", invalidServers) + ".", NamedTextColor.GOLD));

            if (!servers.isEmpty())
                invocation.source().sendMessage(Component.text("Broadcasting message to " + servers.size() + " server" + (servers.size() == 1 ? "" : "s") + ".", NamedTextColor.GOLD));
            else
                return;

            recipients = Audience.audience(servers);
        }

        String[] messageArgs = Arrays.copyOfRange(invocation.arguments(), 1, invocation.arguments().length);
        recipients.sendMessage(MiniMessage.miniMessage().deserialize(String.join(" ", messageArgs)));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.alert");
    }
}
