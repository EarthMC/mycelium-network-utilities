package net.earthmc.velocitycommands;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.earthmc.velocitycommands.commands.AlertCommand;
import net.earthmc.velocitycommands.commands.SendCommand;

@Plugin(id = "velocitycommands", name = "VelocityCommands", version = "0.0.1", authors = {"Warriorrr"})
public class VelocityCommands {

    private static VelocityCommands instance;
    private static ProxyServer proxy;

    @Inject
    public VelocityCommands(ProxyServer proxy) {
        VelocityCommands.instance = this;
        VelocityCommands.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getCommandManager().register("send", new SendCommand(proxy));
        proxy.getCommandManager().register("alert", new AlertCommand(proxy));
    }
}
