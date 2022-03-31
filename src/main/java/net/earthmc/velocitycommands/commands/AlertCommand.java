package net.earthmc.velocitycommands.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

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

        if (invocation.arguments().length == 0) {
            invocation.source().sendMessage(Component.text("Not enough arguments! Usage: /alert [message]", NamedTextColor.RED));
            return;
        }

        Component message = MiniMessage.miniMessage().deserialize(String.join(" ", invocation.arguments()));
        proxy.sendMessage(message);

        if (invocation.source() instanceof ConsoleCommandSource)
            invocation.source().sendMessage(message);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitycommands.alert");
    }
}
