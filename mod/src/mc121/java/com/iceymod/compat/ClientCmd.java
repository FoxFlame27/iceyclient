package com.iceymod.compat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ClientCmd {
    private ClientCmd() {}
    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) { return ClientCommandManager.literal(name); }
    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) { return ClientCommandManager.argument(name, type); }
    public static void onRegister(Consumer<CommandDispatcher<FabricClientCommandSource>> c) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> c.accept(dispatcher));
    }
}
