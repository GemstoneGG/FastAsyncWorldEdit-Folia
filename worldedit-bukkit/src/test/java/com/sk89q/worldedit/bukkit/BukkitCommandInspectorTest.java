/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit;

import com.sk89q.bukkit.util.DynamicPluginCommand;
import com.sk89q.bukkit.util.DynamicPluginCommandHelpTopic;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.NoCapablePlatformException;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.CommandManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BukkitCommandInspectorTest {

    @Test
    void helpTopicCanBeCreatedWithoutACapablePlatform() {
        WorldEdit worldEdit = mock(WorldEdit.class);
        when(worldEdit.getConfiguration()).thenThrow(new NoCapablePlatformException());
        try (MockedStatic<WorldEdit> singleton = mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(worldEdit);
            DynamicPluginCommand command = command();
            DynamicPluginCommandHelpTopic topic = new DynamicPluginCommandHelpTopic(command);
            assertEquals("Stored description", topic.getShortText());
            assertEquals("/test <value>", topic.getFullText(null));
        }
    }

    @Test
    void helpUsesConfiguredLocaleWhenPlatformIsAvailable() {
        WorldEdit worldEdit = mock(WorldEdit.class);
        LocalConfiguration configuration = mock(LocalConfiguration.class);
        configuration.defaultLocale = Locale.GERMAN;
        when(worldEdit.getConfiguration()).thenReturn(configuration);
        try (MockedStatic<WorldEdit> singleton = mockStatic(WorldEdit.class);
                MockedStatic<WorldEditText> text = mockStatic(WorldEditText.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(worldEdit);
            text.when(() -> WorldEditText.reduceToText(TextComponent.of("description"), Locale.GERMAN))
                    .thenReturn("Translated description");
            text.when(() -> WorldEditText.reduceToText(TextComponent.of("full help"), Locale.GERMAN))
                    .thenReturn("Translated full help");
            DynamicPluginCommandHelpTopic topic = new DynamicPluginCommandHelpTopic(command());
            assertEquals("Translated description", topic.getShortText());
            assertEquals("Translated full help", topic.getFullText(null));
        }
    }

    @Test
    void helpAlsoFallsBackWhenTranslationPlatformIsUnavailable() {
        WorldEdit worldEdit = mock(WorldEdit.class);
        LocalConfiguration configuration = mock(LocalConfiguration.class);
        configuration.defaultLocale = Locale.ENGLISH;
        when(worldEdit.getConfiguration()).thenReturn(configuration);
        try (MockedStatic<WorldEdit> singleton = mockStatic(WorldEdit.class);
                MockedStatic<WorldEditText> text = mockStatic(WorldEditText.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(worldEdit);
            text.when(() -> WorldEditText.reduceToText(TextComponent.of("description"), Locale.ENGLISH))
                    .thenThrow(new NoCapablePlatformException());
            text.when(() -> WorldEditText.reduceToText(TextComponent.of("full help"), Locale.ENGLISH))
                    .thenThrow(new NoCapablePlatformException());
            DynamicPluginCommandHelpTopic topic = new DynamicPluginCommandHelpTopic(command());
            assertEquals("Stored description", topic.getShortText());
            assertEquals("/test <value>", topic.getFullText(null));
        }
    }

    @Test
    void unrelatedFailuresAreNotSuppressed() {
        WorldEdit worldEdit = mock(WorldEdit.class);
        when(worldEdit.getConfiguration()).thenThrow(new IllegalStateException("unrelated"));
        try (MockedStatic<WorldEdit> singleton = mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(worldEdit);
            assertThrows(IllegalStateException.class, () -> new DynamicPluginCommandHelpTopic(command()));
        }
    }

    private DynamicPluginCommand command() {
        WorldEditPlugin plugin = mock(WorldEditPlugin.class);
        CommandManager dispatcher = mock(CommandManager.class);
        org.enginehub.piston.Command mapping = mock(org.enginehub.piston.Command.class);
        when(mapping.getDescription()).thenReturn(TextComponent.of("description"));
        when(mapping.getFullHelp()).thenReturn(TextComponent.of("full help"));
        when(dispatcher.getCommand("test")).thenReturn(Optional.of(mapping));
        BukkitCommandInspector inspector = new BukkitCommandInspector(plugin, dispatcher);
        return new DynamicPluginCommand(
                new String[]{"test"}, "Stored description", "/test <value>", plugin, inspector, plugin
        );
    }
}
