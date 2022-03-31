package net.earthmc.velocitycommands;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.earthmc.velocitycommands.commands.AlertCommand;
import net.earthmc.velocitycommands.commands.SendCommand;
import net.earthmc.velocitycommands.commands.ServerCommand;
import net.earthmc.velocitycommands.commands.VSudoCommand;

@Plugin(id = "velocitycommands", name = "VelocityCommands", version = "0.0.1", authors = {"Warriorrr"})
public class VelocityCommands {
    private static ProxyServer proxy;

    @Inject
    public VelocityCommands(ProxyServer proxy) {
        VelocityCommands.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getCommandManager().register("send", new SendCommand(proxy));
        proxy.getCommandManager().register("alert", new AlertCommand(proxy));
        proxy.getCommandManager().register("vsudo", new VSudoCommand(proxy));

        // Remove the original server command and replace it with ours
        proxy.getCommandManager().unregister("server");
        proxy.getCommandManager().register("server", new ServerCommand(proxy));
    }
}
