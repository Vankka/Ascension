package com.discordsrv.common.command.combined.abstraction;

import com.discordsrv.common.config.messages.MessagesConfig;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

public interface CommandExecution {

    Locale locale();
    MessagesConfig messages();

    void setEphemeral(boolean ephemeral);

    String getArgument(String label);

    default void send(Text... texts) {
        send(Arrays.asList(texts));
    }

    default void send(Collection<Text> texts) {
        send(texts, Collections.emptyList());
    }

    void send(Collection<Text> texts, Collection<Text> extra);

    void send(Component minecraft, String discord);

    void runAsync(Runnable runnable);
}
